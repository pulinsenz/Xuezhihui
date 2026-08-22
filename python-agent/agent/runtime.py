"""
共享运行时组件（单例）：LLM、向量存储、检索器、会话存储。
main.py 启动时 init_runtime()，各 api 模块从 runtime 读取。
"""
import os

from llm.llm_client import LLMClient
from rag.chunker import Chunker
from rag.reranker import Reranker
from rag.retriever import HybridRetriever
from storage.embedding import EmbeddingModel
from storage.redis_store import RedisStore
from storage.vector_store import LocalVectorStore

llm = None
embedding = None
vector_store = None
retriever = None
redis_store = None
reranker = None


def create_vector_store(embedding_model):
    """按配置创建向量库：VECTOR_STORE=milvus 时切 Milvus（生产），默认本地（开发）"""
    if os.getenv("VECTOR_STORE", "local") == "milvus":
        from storage.milvus_store import MilvusStore
        return MilvusStore(embedding_model)
    return LocalVectorStore(embedding_model)


def init_runtime():
    global llm, embedding, vector_store, retriever, redis_store, reranker
    llm = LLMClient()
    embedding = EmbeddingModel()
    vector_store = create_vector_store(embedding)
    retriever = HybridRetriever(vector_store, chunker=Chunker())
    redis_store = RedisStore()
    reranker = Reranker()
