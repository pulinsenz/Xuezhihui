"""
内部调用鉴权：Java 调用 Python Agent 时带 X-Agent-Token。
防止 8000 端口被未授权直接访问。
"""
import hmac

from fastapi import Header, HTTPException

from config import settings


def verify_agent_token(x_agent_token: str = Header(default="")) -> None:
    """FastAPI 依赖：校验内部 token（常数时间比较防时序攻击）"""
    if not settings.agent_token:
        raise HTTPException(status_code=401, detail="Agent 服务未配置内部 token")
    if not _constant_time_equals(x_agent_token, settings.agent_token):
        raise HTTPException(status_code=401, detail="未授权的 Agent 调用")


def _constant_time_equals(a: str, b: str) -> bool:
    return hmac.compare_digest(a.encode(), b.encode())
