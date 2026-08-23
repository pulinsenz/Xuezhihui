"""
向量存储抽象 + 本地内存实现（开发/演示用）。
生产环境切换 storage/milvus_store.py 的 MilvusStore，业务代码零改动。
"""
import threading
from abc import ABC, abstractmethod
from typing import List, Optional

from utils.logger_util import get_logger, agent_event

logger = get_logger("vector")


class VectorStore(ABC):
    """向量存储抽象接口"""

    @abstractmethod
    def add_document(self, knowledge_id: str, doc_id: str, text: str, chunks: List[str]) -> int:
        """入库：按 chunks 生成向量"""

    @abstractmethod
    def delete_document(self, knowledge_id: str, doc_id: str) -> None:
        """删除单个文档的向量"""

    @abstractmethod
    def delete_knowledge(self, knowledge_id: str) -> None:
        """删除整个知识库的向量"""

    @abstractmethod
    def search(self, query: str, knowledge_id: Optional[str] = None, top_k: int = 5) -> List[dict]:
        """向量检索，返回 [{"text", "score"}]"""


class LocalVectorStore(VectorStore):
    """内存向量库：embedding + 余弦相似度暴力检索，适合开发/小规模演示"""

    def __init__(self, embedding):
        self.embedding = embedding
        self._vectors: List[dict] = []  # [{knowledge_id, doc_id, text, vec}]
        self._lock = threading.Lock()

    def add_document(self, knowledge_id, doc_id, text, chunks):
        if not chunks:
            return 0
        with self._lock:
            # 全量替换同 doc 的旧向量（幂等，重新入库）
            self._vectors = [v for v in self._vectors if v["doc_id"] != doc_id]
        vecs = self.embedding.embed_documents(chunks)
        with self._lock:
            for chunk, vec in zip(chunks, vecs):
                self._vectors.append({
                    "knowledge_id": str(knowledge_id),
                    "doc_id": str(doc_id),
                    "text": chunk,
                    "vec": vec,
                })
        agent_event(logger, "vector_added", knowledge_id=knowledge_id, doc_id=doc_id, chunks=len(chunks))
        return len(chunks)

    def delete_document(self, knowledge_id, doc_id):
        with self._lock:
            before = len(self._vectors)
            self._vectors = [v for v in self._vectors if v["doc_id"] != str(doc_id)]
            logger.info("删除文档向量: doc_id=%s removed=%d", doc_id, before - len(self._vectors))

    def delete_knowledge(self, knowledge_id):
        with self._lock:
            before = len(self._vectors)
            self._vectors = [v for v in self._vectors if v["knowledge_id"] != str(knowledge_id)]
            logger.info("删除知识库向量: knowledge_id=%s removed=%d", knowledge_id, before - len(self._vectors))

    def search(self, query, knowledge_id=None, top_k=5):
        q_vec = self.embedding.embed_query(query)
        with self._lock:
            candidates = self._vectors
            if knowledge_id is not None:
                candidates = [v for v in candidates if v["knowledge_id"] == str(knowledge_id)]
        if not candidates:
            return []
        scored = []
        for v in candidates:
            score = _cosine(q_vec, v["vec"])
            scored.append({"text": v["text"], "score": score, "doc_id": v["doc_id"]})
        scored.sort(key=lambda x: -x["score"])
        return scored[:top_k]


def _cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = sum(x * x for x in a) ** 0.5
    nb = sum(x * x for x in b) ** 0.5
    return dot / (na * nb + 1e-9)
