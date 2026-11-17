"""
混合检索：BM25 关键词 + 向量语义，分数归一化融合。
弥补纯向量检索丢失关键词精确匹配的问题。
"""
import re
from typing import List, Optional

from rank_bm25 import BM25Okapi

from rag.chunker import Chunker
from storage.vector_store import VectorStore
from utils.logger_util import get_logger, agent_event

logger = get_logger("retriever")

# RRF 融合常数：排名倒数融合的分母平滑项（业界常用 60）
RRF_K = 60


def _tokenize(text: str) -> List[str]:
    """中文 jieba 分词、英文按词切分（统一小写）；jieba 缺失时降级为单字切分。

    单字切分会把「数据结构」「知识库」等中文词组拆散，BM25 只能靠单字 IDF 匹配，
    词组精确命中能力弱；jieba 按词切分后 BM25 的词项与文档词组对齐，召回更准。
    """
    try:
        import jieba
    except ImportError:
        return re.findall(r"[\u4e00-\u9fff]|[a-zA-Z0-9]+", text.lower())
    tokens: List[str] = []
    for seg in jieba.lcut(text.lower()):
        seg = seg.strip()
        # 丢弃纯标点/空白，保留含中英文数字的片段（jieba 对英文单词整体保留）
        if re.search(r"[\u4e00-\u9fff]|[a-zA-Z0-9]", seg):
            tokens.append(seg)
    return tokens


class HybridRetriever:
    """BM25 + 向量混合检索器

    BM25 索引以向量库（持久层）为唯一数据源，进程内存只缓存重建产物：
    - 重启后无需手工重建，首次检索自动从向量库回灌（多副本部署同理）
    - 入库/删除只标记 dirty，下次检索时重建一次；连续导入 N 个文档
      从「每次入库全量重建」的 O(N×全库) 降为 N 次标记 + 1 次重建
    """

    def __init__(self, vector_store: VectorStore, chunker: Optional[Chunker] = None):
        self.vector_store = vector_store
        self.chunker = chunker or Chunker()
        self._bm25: dict[str, BM25Okapi] = {}
        self._bm25_docs: dict[str, List[str]] = {}
        # 有写入待重建的知识库集合
        self._dirty: set[str] = set()

    def add_document(self, knowledge_id, doc_id, text: str):
        """分块 + 向量入库（幂等：同 doc_id 向量被覆盖）；BM25 延迟到下次检索重建"""
        chunks = self.chunker.split(text)
        self.vector_store.add_document(knowledge_id, doc_id, text, chunks)
        self._dirty.add(str(knowledge_id))
        return len(chunks)

    def delete_document(self, knowledge_id, doc_id):
        """删除单个文档：删向量 + 标记 BM25 待重建"""
        self.vector_store.delete_document(knowledge_id, doc_id)
        self._dirty.add(str(knowledge_id))

    def delete_knowledge(self, knowledge_id):
        """删除整个知识库：删向量 + 标记 BM25 待重建"""
        self.vector_store.delete_knowledge(knowledge_id)
        self._dirty.add(str(knowledge_id))

    def get_chunks(self, knowledge_id, doc_id):
        """查询文档切片文本列表（「向量详情」）"""
        return self.vector_store.get_chunks(knowledge_id, doc_id)

    def retrieve_wiki(self, query: str, knowledge_id: str, top_k: int = 10) -> List[dict]:
        """泛化/总结类问答专用：仅在 Wiki 页面集合内做向量检索（doc_id 前缀 wiki-）。
        避开原始文档的代码/日志噪声碎片；无 Wiki 时返回空列表，调用方回退全库检索。"""
        return self.vector_store.search(query, knowledge_id, top_k=top_k, doc_id_prefix="wiki-")

    def _ensure_bm25(self, knowledge_id: str):
        """索引缺失或已 dirty 时，从向量库回灌该知识库全部分块重建"""
        if knowledge_id not in self._dirty and knowledge_id in self._bm25:
            return
        all_chunks = self.vector_store.get_all_chunks(knowledge_id)
        if all_chunks:
            self._bm25[knowledge_id] = BM25Okapi([_tokenize(c) for c in all_chunks])
            self._bm25_docs[knowledge_id] = all_chunks
        else:
            self._bm25.pop(knowledge_id, None)
            self._bm25_docs.pop(knowledge_id, None)
        self._dirty.discard(knowledge_id)

    def retrieve(self, query: str, knowledge_id: str, top_k: int = 5) -> List[dict]:
        self._ensure_bm25(str(knowledge_id))
        vector_results = self.vector_store.search(query, knowledge_id, top_k=top_k * 2)
        bm25_results = self._bm25_retrieve(query, knowledge_id, top_k=top_k * 2)
        fused = self._fuse(vector_results, bm25_results, top_k)
        agent_event(
            logger, "retrieve_done",
            query=query[:30], knowledge_id=knowledge_id,
            vector_hits=len(vector_results), bm25_hits=len(bm25_results), final=len(fused),
        )
        return fused

    def _bm25_retrieve(self, query: str, knowledge_id: str, top_k: int) -> List[dict]:
        bm25 = self._bm25.get(str(knowledge_id))
        if bm25 is None or not self._bm25_docs.get(str(knowledge_id)):
            return []
        scores = bm25.get_scores(_tokenize(query))
        docs = self._bm25_docs[str(knowledge_id)]
        ranked = sorted(range(len(scores)), key=lambda i: -scores[i])[:top_k]
        return [{"text": docs[i], "score": float(scores[i])} for i in ranked if scores[i] > 0]

    def _fuse(self, vector_results, bm25_results, top_k: int) -> List[dict]:
        """RRF（Reciprocal Rank Fusion）按名次融合 BM25 与向量结果，按 text 去重。

        原实现对两类异构分数做 min-max 归一化后线性加权：BM25 分数无上界、向量余弦
        [-1,1]，极值会拉偏归一化，且权重需手工调。RRF 只用各列表内的名次贡献分值，
        对分数分布不敏感、无需调参，融合更鲁棒。
        """
        items: dict[str, dict] = {}
        for rank, r in enumerate(vector_results):
            item = items.setdefault(r["text"], {"text": r["text"], "rrf": 0.0, "doc_id": r.get("doc_id")})
            item["rrf"] += 1.0 / (RRF_K + rank + 1)
            if r.get("doc_id"):
                item["doc_id"] = r.get("doc_id")
        for rank, r in enumerate(bm25_results):
            item = items.setdefault(r["text"], {"text": r["text"], "rrf": 0.0, "doc_id": None})
            item["rrf"] += 1.0 / (RRF_K + rank + 1)
            if r.get("doc_id"):
                item["doc_id"] = r.get("doc_id")
        ranked = sorted(items.values(), key=lambda x: -x["rrf"])[:top_k]
        return [{"text": r["text"], "score": round(r["rrf"], 4), "doc_id": r.get("doc_id")} for r in ranked]
