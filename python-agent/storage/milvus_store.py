"""
Milvus 向量库实现（生产环境使用）。
"""
from typing import List, Optional

from config import settings
from storage.vector_store import VectorStore
from utils.logger_util import get_logger, agent_event

logger = get_logger("milvus")


class MilvusStore(VectorStore):
    """基于 pymilvus MilvusClient 的向量库实现"""

    def __init__(self, embedding, uri: str = None, collection: str = None):
        self.embedding = embedding
        self.uri = uri or settings.milvus_uri
        self.collection = collection or settings.milvus_collection
        from pymilvus import MilvusClient
        self.client = MilvusClient(uri=self.uri)
        self._ensure_collection()

    def _ensure_collection(self):
        from pymilvus import DataType
        if self.client.has_collection(self.collection):
            # 确保已加载到内存（含索引），否则 search 会失败
            self.client.load_collection(self.collection)
            return
        schema = self.client.create_schema(auto_id=True, enable_dynamic_field=True)
        schema.add_field("id", DataType.INT64, is_primary=True)
        schema.add_field("knowledge_id", DataType.VARCHAR, max_length=64)
        schema.add_field("doc_id", DataType.VARCHAR, max_length=64)
        schema.add_field("text", DataType.VARCHAR, max_length=2048)
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=self.embedding.dim())
        index_params = self.client.prepare_index_params()
        index_params.add_index(field_name="vector", index_type="HNSW", metric_type="COSINE",
                               params={"M": 16, "efConstruction": 200})
        self.client.create_collection(self.collection, schema=schema, index_params=index_params)
        self.client.load_collection(self.collection)
        agent_event(logger, "milvus_collection_created", collection=self.collection)

    def add_document(self, knowledge_id, doc_id, text, chunks):
        if not chunks:
            return 0
        # 幂等：先删旧向量
        self.client.delete(self.collection, filter=f'doc_id == "{doc_id}"')
        vecs = self.embedding.embed_documents(chunks)
        rows = [
            {"knowledge_id": str(knowledge_id), "doc_id": str(doc_id), "text": c, "vector": v}
            for c, v in zip(chunks, vecs)
        ]
        self.client.insert(self.collection, data=rows)
        # 数据落盘，确保立即可检索
        self.client.flush(self.collection)
        agent_event(logger, "milvus_added", knowledge_id=knowledge_id, doc_id=doc_id, chunks=len(rows))
        return len(rows)

    def delete_document(self, knowledge_id, doc_id):
        self.client.delete(self.collection, filter=f'doc_id == "{doc_id}"')
        self.client.flush(self.collection)

    def delete_knowledge(self, knowledge_id):
        self.client.delete(self.collection, filter=f'knowledge_id == "{knowledge_id}"')
        self.client.flush(self.collection)

    def search(self, query, knowledge_id=None, top_k=5):
        q_vec = self.embedding.embed_query(query)
        expr = f'knowledge_id == "{knowledge_id}"' if knowledge_id is not None else None
        results = self.client.search(
            self.collection, data=[q_vec], limit=top_k, output_fields=["text"],
            filter=expr, metric_type="COSINE",
        )
        hits = results[0] if results else []
        return [{"text": h["entity"]["text"], "score": float(h["distance"])} for h in hits]
