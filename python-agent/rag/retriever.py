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

# 融合权重：向量语义为主、BM25 关键词为辅
VECTOR_WEIGHT = 0.6
BM25_WEIGHT = 0.4


def _tokenize(text: str) -> List[str]:
    """中文按单字、英文按词切分（不依赖 jieba，轻量可用）"""
    return re.findall(r"[一-鿿]|[a-zA-Z0-9]+", text.lower())


class HybridRetriever:
    """BM25 + 向量混合检索器"""

    def __init__(self, vector_store: VectorStore, chunker: Optional[Chunker] = None):
        self.vector_store = vector_store
        self.chunker = chunker or Chunker()
        self._bm25: dict[str, BM25Okapi] = {}
        self._bm25_docs: dict[str, List[str]] = {}
        # knowledge_id -> {doc_id -> [chunks]}：按文档记录分块，删除/重入库时精确重建
        self._chunks: dict[str, dict[str, List[str]]] = {}

    def add_document(self, knowledge_id, doc_id, text: str):
        """分块 + 向量入库 + 重建该知识库 BM25 索引（幂等：同 doc_id 向量会被覆盖）"""
        chunks = self.chunker.split(text)
        self.vector_store.add_document(knowledge_id, doc_id, text, chunks)
        kid = str(knowledge_id)
        self._chunks.setdefault(kid, {})[str(doc_id)] = chunks
        self._rebuild_bm25(kid)
        return len(chunks)

    def delete_document(self, knowledge_id, doc_id):
        """删除单个文档：删向量 + 从 BM25 索引剔除该文档分块"""
        self.vector_store.delete_document(knowledge_id, doc_id)
        kid = str(knowledge_id)
        self._chunks.get(kid, {}).pop(str(doc_id), None)
        self._rebuild_bm25(kid)

    def delete_knowledge(self, knowledge_id):
        """删除整个知识库：删向量 + 清空该知识库 BM25 索引"""
        self.vector_store.delete_knowledge(knowledge_id)
        kid = str(knowledge_id)
        self._chunks.pop(kid, None)
        self._rebuild_bm25(kid)

    def get_chunks(self, knowledge_id, doc_id):
        """查询文档切片文本列表（「向量详情」）"""
        return self.vector_store.get_chunks(knowledge_id, doc_id)

    def _rebuild_bm25(self, knowledge_id: str):
        """按知识库内全部文档的分块重建 BM25（含多文档，删除/重入库后保持一致）"""
        all_chunks = [c for doc_chunks in self._chunks.get(knowledge_id, {}).values() for c in doc_chunks]
        if all_chunks:
            self._bm25[knowledge_id] = BM25Okapi([_tokenize(c) for c in all_chunks])
            self._bm25_docs[knowledge_id] = all_chunks
        else:
            self._bm25.pop(knowledge_id, None)
            self._bm25_docs.pop(knowledge_id, None)

    def retrieve(self, query: str, knowledge_id: str, top_k: int = 5) -> List[dict]:
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
        """按 text 去重，各自归一化后加权融合（保留 doc_id 供前端展示来源文档）"""
        items: dict[str, dict] = {}
        for r in vector_results:
            item = items.setdefault(r["text"], {"text": r["text"], "v": r["score"], "b": 0.0, "doc_id": r.get("doc_id")})
            item["v"] = r["score"]
            item["doc_id"] = r.get("doc_id") or item["doc_id"]
        for r in bm25_results:
            item = items.setdefault(r["text"], {"text": r["text"], "v": 0.0, "b": r["score"], "doc_id": None})
            item["b"] = r["score"]

        v_scores = [i["v"] for i in items.values()]
        b_scores = [i["b"] for i in items.values()]
        v_max, v_min = (max(v_scores), min(v_scores)) if v_scores else (1.0, 0.0)
        b_max, b_min = (max(b_scores), min(b_scores)) if b_scores else (1.0, 0.0)

        for item in items.values():
            v_norm = (item["v"] - v_min) / (v_max - v_min + 1e-9)
            b_norm = (item["b"] - b_min) / (b_max - b_min + 1e-9)
            item["score"] = VECTOR_WEIGHT * v_norm + BM25_WEIGHT * b_norm

        ranked = sorted(items.values(), key=lambda x: -x["score"])[:top_k]
        return [{"text": r["text"], "score": round(r["score"], 4), "doc_id": r.get("doc_id")} for r in ranked]
