"""
重排器：交叉编码器（Cross-Encoder）二次打分，过滤初筛噪声。
默认降级为透传（不加载大模型），配置 RERANKER_MODEL 后启用增强。

直接用 transformers 底层 API 而非 sentence_transformers.CrossEncoder：
ST 5.x 的 predict 与 transformers 5.x 不兼容（tokenizer 输出错位报 AttributeError），
bge-reranker 是标准序列分类模型，sigmoid(logits) 即相关性分数，无需 ST 封装。
"""
import os
from typing import List, Optional

from utils.logger_util import get_logger, agent_event

logger = get_logger("reranker")

# bge-reranker-base 上下文上限 512 token，超长截断
MAX_LENGTH = 512


class Reranker:
    def __init__(self, model_name: str = None):
        self._model = None
        self._tokenizer = None
        model_name = model_name or os.getenv("RERANKER_MODEL", "").strip()
        if model_name:
            try:
                import torch
                from transformers import AutoModelForSequenceClassification, AutoTokenizer
                self._torch = torch
                self._tokenizer = AutoTokenizer.from_pretrained(model_name)
                self._model = AutoModelForSequenceClassification.from_pretrained(model_name)
                self._model.to("cpu").eval()
                # 可选 int8 动态量化：交叉编码器 FP32 权重约 1.1GB，量化后约 300MB，
                # 在 2C4G 小内存服务器上避免 OOM；精度损失 <1%，召回排序影响可忽略。
                if os.getenv("RERANKER_INT8", "false").lower() == "true":
                    self._quantize()
                logger.info("加载重排模型: %s", model_name)
            except Exception as e:
                logger.warning("重排模型加载失败，降级为融合排序: %s", e)
                self._model = None
                self._tokenizer = None

    def _quantize(self):
        """对模型全部 Linear 层做 int8 动态量化，显著降低内存占用"""
        try:
            from torch.quantization import quantize_dynamic
            self._model = quantize_dynamic(
                self._model, {self._torch.nn.Linear}, dtype=self._torch.qint8,
            )
            logger.info("重排模型已 int8 动态量化")
        except Exception as e:
            logger.warning("int8 量化失败，保持 FP32: %s", e)

    def rerank(self, query: str, docs: List[dict], top_k: Optional[int] = None) -> List[dict]:
        """对检索结果重排。未配置模型时直接返回（保持融合排序结果）。"""
        if not docs:
            return []
        if self._model is None:
            return docs[:top_k] if top_k else docs
        pairs = [(query, d["text"]) for d in docs]
        with self._torch.no_grad():
            feats = self._tokenizer(
                pairs, padding=True, truncation=True, max_length=MAX_LENGTH, return_tensors="pt",
            )
            logits = self._model(**feats).logits.squeeze(-1)
        scores = self._torch.sigmoid(logits).tolist()
        ranked = sorted(zip(docs, scores), key=lambda x: -x[1])
        # 保留 doc_id：来源文档需透传到前端展示，缺失会丢引用
        result = [{"text": d["text"], "score": float(s), "doc_id": d.get("doc_id")} for d, s in ranked]
        agent_event(logger, "rerank_done", hits=len(result))
        return result[:top_k] if top_k else result
