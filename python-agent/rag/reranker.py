"""
重排器接口：交叉编码器（Cross-Encoder）二次打分，过滤初筛噪声。
默认降级为透传（不加载大模型），配置 RERANKER_MODEL 后启用增强。
"""
import os
from typing import List, Optional

from utils.logger_util import get_logger, agent_event

logger = get_logger("reranker")


class Reranker:
    def __init__(self):
        self._model = None
        model_name = os.getenv("RERANKER_MODEL", "").strip()
        if model_name:
            try:
                from sentence_transformers import CrossEncoder
                self._model = CrossEncoder(model_name)
                logger.info("加载重排模型: %s", model_name)
            except Exception as e:
                logger.warning("重排模型加载失败，降级为融合排序: %s", e)

    def rerank(self, query: str, docs: List[dict], top_k: Optional[int] = None) -> List[dict]:
        """对检索结果重排。未配置模型时直接返回（保持融合排序结果）"""
        if not docs:
            return []
        if self._model is None:
            return docs[:top_k] if top_k else docs
        pairs = [(query, d["text"]) for d in docs]
        scores = self._model.predict(pairs)
        ranked = sorted(zip(docs, scores), key=lambda x: -x[1])
        result = [{"text": d["text"], "score": float(s)} for d, s in ranked]
        agent_event(logger, "rerank_done", hits=len(result))
        return result[:top_k] if top_k else result
