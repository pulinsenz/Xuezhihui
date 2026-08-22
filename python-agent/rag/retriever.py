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

    def add_document(self, knowledge_id, doc_id, text: str):
        """分块 + 向量入库 + 建立 BM25 索引"""
        chunks = self.chunker.split(text)
        self.vector_store.add_document(knowledge_id, doc_id, text, chunks)
        self._index_bm25(str(knowledge_id), chunks)
        return len(chunks)

    def _index_bm25(self, knowledge_id: str, chunks: List[str]):
        corpus = [_tokenize(c) for c in chunks]
        # 幂等：同知识库重建索引
        self._bm25[knowledge_id] = BM25Okapi(corpus)
        self._bm25_docs[knowledge_id] = chunks

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
        """按 text 去重，各自归一化后加权融合"""
        items: dict[str, dict] = {}
        for r in vector_results:
            item = items.setdefault(r["text"], {"text": r["text"], "v": r["score"], "b": 0.0})
            item["v"] = r["score"]
        for r in bm25_results:
            item = items.setdefault(r["text"], {"text": r["text"], "v": 0.0, "b": r["score"]})
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
        return [{"text": r["text"], "score": round(r["score"], 4)} for r in ranked]
