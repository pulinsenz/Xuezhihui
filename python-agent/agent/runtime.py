"""
共享运行时组件（单例）：LLM、向量存储、检索器、会话存储。
main.py 启动时 init_runtime()，各 api 模块从 runtime 读取。
"""
from config import DEEPSEEK_BASE_URL, settings
from llm.llm_client import FallbackLLM, LLMClient
from rag.chunker import Chunker
from rag.reranker import Reranker
from rag.retriever import HybridRetriever
from storage.answer_cache import AnswerCache
from storage.embedding import EmbeddingModel
from storage.redis_store import RedisStore
from storage.vector_store import LocalVectorStore

llm = None
user_llm = None
embedding = None
vector_store = None
retriever = None
redis_store = None
reranker = None
answer_cache = None


def create_vector_store(embedding_model):
    """按配置创建向量库：生产默认 Milvus（持久化），local 仅开发显式开启"""
    if settings.vector_store == "milvus":
        from storage.milvus_store import MilvusStore
        return MilvusStore(embedding_model, uri=settings.milvus_uri,
                           collection=settings.milvus_collection)
    return LocalVectorStore(embedding_model)


def _build_user_llm(default_llm):
    """构造普通用户 LLM 实例：网关与官方通道不同源时包故障转移（主失败 → 官方兜底）。
    同源（.env 已把 USER_LLM_BASE_URL 指向官方）时不包——回退到同一 key 只是重复计费。"""
    user = LLMClient(api_key=settings.user_api_key, model=settings.user_llm_model,
                     base_url=settings.user_llm_base_url, label="普通用户")
    if (settings.user_llm_base_url or "").rstrip("/") != DEEPSEEK_BASE_URL:
        return FallbackLLM(primary=user, fallback=default_llm, label="网关→官方")
    return user


def init_runtime():
    global llm, user_llm, embedding, vector_store, retriever, redis_store, reranker, answer_cache
    llm = LLMClient()  # 管理员/默认：DEEPSEEK_API_KEY
    # 普通用户 LLM：仅在有 USER_API_KEY 时创建；无 key 时 _pick_llm 回退到 llm
    user_llm = None
    if settings.user_api_key:
        user_llm = _build_user_llm(llm)
    embedding = EmbeddingModel(settings.embedding_model)
    vector_store = create_vector_store(embedding)
    retriever = HybridRetriever(vector_store, chunker=Chunker())
    redis_store = RedisStore(host=settings.redis_host, port=settings.redis_port,
                             password=settings.redis_password or None)
    reranker = Reranker(model_name=settings.reranker_model)
    # 答案缓存：复用会话 Redis 与检索 embedding；查询/写入全程降级容错
    answer_cache = AnswerCache(redis_store.client,
                               enabled=settings.answer_cache_enabled,
                               sem_threshold=settings.answer_cache_sem_threshold,
                               ttl_hours=settings.answer_cache_ttl_hours,
                               max_entries=settings.answer_cache_max_entries,
                               embed_fn=embedding.embed_query)
