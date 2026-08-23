"""
Java 业务服务回调客户端：工具 Agent 获取业务数据的唯一通道。
信任边界：Python Agent 不直接访问 MySQL，业务数据一律回调 Java internal 接口获取。
双向内部鉴权：Java 调 Python 带 X-Agent-Token，Python 回调 Java 同样携带（同一把钥匙）。
失败一律降级为 None（不阻断对话），由 answer 节点兜底为普通回答。
"""
import httpx

from typing import Optional

from config import settings
from utils.logger_util import get_logger, agent_event

logger = get_logger("java_client")

_TIMEOUT = httpx.Timeout(5.0)


def _headers() -> dict:
    return {"X-Agent-Token": settings.java_token, "Accept": "application/json"}


def fetch_user_stats(user_id) -> Optional[dict]:
    """回调 Java：查询用户业务数据统计（知识库/文档/向量化状态）。失败降级 None。"""
    if not user_id:
        return None
    if not settings.java_token:
        logger.warning("JAVA_TOKEN 未配置，工具回调不可用（对话降级为普通回答）")
        return None
    url = f"{settings.java_base_url.rstrip('/')}/internal/agent/user-stats"
    try:
        # trust_env=False：内部服务回调必须直连，不走系统代理。
        # 踩坑：Windows 注册表代理（如 Docker Desktop 设的 127.0.0.1:7993）会被 httpx 自动读取，
        # 把 localhost 出站也发给代理 → 502；curl 只读环境变量不读注册表所以正常。
        resp = httpx.get(url, params={"user_id": str(user_id)}, headers=_headers(),
                         timeout=_TIMEOUT, trust_env=False)
        if resp.status_code != 200:
            logger.warning("工具回调失败: status=%s", resp.status_code)
            return None
        body = resp.json()
        if body.get("code") != 0:
            logger.warning("工具回调业务失败: %s", body.get("message"))
            return None
        agent_event(logger, "java_callback_ok", user_id=str(user_id))
        return body.get("data")
    except Exception as e:
        # 网络异常 / Java 未启动：不阻断对话，降级为无业务数据
        logger.warning("工具回调异常（降级为无数据）: %s", e)
        return None


def persist_chat(session_id, user_id, query, answer, route=None, knowledge_id=None,
                 thinking=None, sources=None) -> bool:
    """回调 Java 持久化一轮对话（会话 + 消息，MySQL）。失败降级 False，不阻断对话。"""
    if not session_id or not user_id:
        return False
    if not settings.java_token:
        logger.warning("JAVA_TOKEN 未配置，对话历史不落库")
        return False
    payload = {
        "session_id": str(session_id),
        "user_id": str(user_id),
        "query": query,
        "answer": answer,
    }
    if route:
        payload["route"] = route
    if knowledge_id:
        payload["knowledge_id"] = str(knowledge_id)
    if thinking:
        payload["thinking"] = list(thinking)
    if sources:
        payload["sources"] = list(sources)
    url = f"{settings.java_base_url.rstrip('/')}/internal/agent/chat-save"
    try:
        resp = httpx.post(url, json=payload,
                          headers=_headers(), timeout=_TIMEOUT, trust_env=False)
        if resp.status_code != 200:
            logger.warning("对话落库失败: status=%s", resp.status_code)
            return False
        body = resp.json()
        if body.get("code") != 0:
            logger.warning("对话落库业务失败: %s", body.get("message"))
            return False
        agent_event(logger, "chat_persisted", session_id=str(session_id))
        return True
    except Exception as e:
        # 网络异常 / Java 未启动：不阻断对话，历史缺该轮（可接受）
        logger.warning("对话落库异常（降级，不阻断）: %s", e)
        return False
