"""
Embedding 模型封装：sentence-transformers 本地加载中文 bge 模型。
"""
import os

from utils.logger_util import get_logger, agent_event

logger = get_logger("embedding")

# 中文检索建议的查询指令前缀（BGE 官方推荐）
BGE_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："

DEFAULT_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")


class EmbeddingModel:
    """本地 embedding：首次运行自动下载模型（约 130MB）"""

    def __init__(self, model_name: str = DEFAULT_MODEL):
        self.model_name = model_name
        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as e:
            raise RuntimeError("缺少 sentence-transformers，请 pip install sentence-transformers") from e
        logger.info("加载 embedding 模型: %s（首次运行需下载）", model_name)
        self.model = SentenceTransformer(model_name)

    def dim(self) -> int:
        """向量维度（Milvus 建集合需要）"""
        try:
            return self.model.get_embedding_dimension()
        except AttributeError:
            # 兼容旧版本 API
            return self.model.get_sentence_embedding_dimension()

    def embed_documents(self, texts):
        return self.model.encode(texts, normalize_embeddings=True).tolist()

    def embed_query(self, text):
        # bge 中文模型：查询加指令前缀提升检索效果
        prefixed = BGE_QUERY_PREFIX + text
        return self.model.encode([prefixed], normalize_embeddings=True)[0].tolist()
