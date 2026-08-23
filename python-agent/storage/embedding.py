"""
Embedding 模型封装：sentence-transformers 本地加载中文 bge 模型。
"""
import os

from utils.logger_util import get_logger, agent_event

logger = get_logger("embedding")

# 中文检索建议的查询指令前缀（BGE 官方推荐）
BGE_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："

DEFAULT_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")


def _model_cached(model_name: str) -> bool:
    """判断模型是否已在本地 HF 缓存（命中则只读本地，不联网不重复下载）"""
    try:
        from huggingface_hub import try_to_load_from_cache
        # 权重文件能取到本地路径即已缓存
        return try_to_load_from_cache(model_name, "model.safetensors") is not None
    except Exception:
        return False


class EmbeddingModel:
    """本地 embedding：首次运行自动下载模型（约 130MB），之后走本地缓存不再联网"""

    def __init__(self, model_name: str = DEFAULT_MODEL):
        self.model_name = model_name
        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as e:
            raise RuntimeError("缺少 sentence-transformers，请 pip install sentence-transformers") from e
        # 隐藏 torch 加载权重的进度条（从本地读取，非下载）
        try:
            from transformers import logging as tf_logging
            tf_logging.disable_progress_bar()
        except Exception:
            pass
        if _model_cached(model_name):
            # 本地已缓存：只读本地，不连 HF Hub（避免每次启动做版本检查/误以为在下载）
            logger.info("加载 embedding 模型: %s（本地缓存，无需下载）", model_name)
            self.model = SentenceTransformer(model_name, local_files_only=True)
        else:
            logger.info("加载 embedding 模型: %s（首次运行，需下载约 130MB）", model_name)
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
