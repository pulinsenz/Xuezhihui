"""
集中配置：环境变量 → Settings 快照。
自动加载 .env（环境变量优先，不覆盖已设置的）：
  1) python-agent/.env —— 本地默认（AGENT_TOKEN、ALLOW_MOCK_LLM、VECTOR_STORE 等）
  2) 项目根 .env —— 部署密钥（DEEPSEEK_API_KEY、AGENT_TOKEN、JWT_SECRET）
生产启动校验在 main.py 的 validate_settings 执行。
"""
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

_BASE = Path(__file__).resolve().parent
# 先本地默认，后项目根密钥；override=False 保证环境变量/docker 注入不被覆盖
load_dotenv(_BASE / ".env", override=False)
load_dotenv(_BASE.parent / ".env", override=False)


@dataclass
class Settings:
    # ---- LLM ----
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    llm_model: str = os.getenv("LLM_MODEL", "deepseek-chat")
    # 仅开发用：设 ALLOW_MOCK_LLM=true 才允许无 key 时用 MockLLM，生产禁止
    allow_mock_llm: bool = os.getenv("ALLOW_MOCK_LLM", "false").lower() == "true"

    # ---- 向量存储（生产默认 Milvus 持久化，local 仅开发）----
    vector_store: str = os.getenv("VECTOR_STORE", "milvus")
    milvus_uri: str = os.getenv("MILVUS_URI", "http://localhost:19530")
    milvus_collection: str = os.getenv("MILVUS_COLLECTION", "xuezhihui_docs")

    # ---- Embedding / Rerank ----
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
    reranker_model: str = os.getenv("RERANKER_MODEL", "")

    # ---- Redis 会话记忆 ----
    redis_host: str = os.getenv("REDIS_HOST", "localhost")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))

    # ---- 长任务消息队列（Redis List：Java 提交 / 本服务消费）----
    task_queue_vectorize: str = os.getenv("TASK_QUEUE_VECTORIZE", "xzh:task:vectorize")

    # ---- 内部鉴权（Java 调用时带 X-Agent-Token）----
    agent_token: str = os.getenv("AGENT_TOKEN", "")

    # ---- Java 业务服务回调（工具 Agent 获取业务数据；Python 不直接访问 MySQL）----
    java_base_url: str = os.getenv("JAVA_BASE_URL", "http://localhost:8123/api")
    # 出站内部鉴权：显式 JAVA_TOKEN 优先，缺省复用 AGENT_TOKEN（同一信任域双向一把钥匙）
    java_token: str = os.getenv("JAVA_TOKEN", "") or os.getenv("AGENT_TOKEN", "")

    # ---- 日志 ----
    log_level: str = os.getenv("LOG_LEVEL", "INFO")


settings = Settings()


def validate_settings() -> None:
    """生产启动校验：关键配置缺失直接抛错，杜绝静默降级上线"""
    errors = []
    if not settings.deepseek_api_key and not settings.allow_mock_llm:
        errors.append("DEEPSEEK_API_KEY 未配置（本地调试可设 ALLOW_MOCK_LLM=true，生产禁止）")
    if not settings.agent_token:
        errors.append("AGENT_TOKEN 未配置（Java 调用鉴权必需）")
    if errors:
        raise RuntimeError("配置缺失，启动失败: " + "; ".join(errors))
