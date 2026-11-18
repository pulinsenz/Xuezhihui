"""
Java 业务服务回调客户端：工具 Agent 获取业务数据的唯一通道。
信任边界：Python Agent 不直接访问 MySQL，业务数据一律回调 Java internal 接口获取。
双向内部鉴权：Java 调 Python 带 X-Agent-Token，Python 回调 Java 同样携带（同一把钥匙）。
旧统计/写回接口保留降级契约；问答取证接口失败明确抛出 JavaEvidenceError。
"""
import httpx

from typing import Optional

from config import settings
from utils.logger_util import get_logger, agent_event

logger = get_logger("java_client")

_TIMEOUT = httpx.Timeout(5.0)

# 模块级共享连接池：每次 httpx.post 新建 TCP+TLS 握手的开销在回调高频场景不可忽视
# （httpx.Client 线程安全，可被 FastAPI 线程池并发复用）
_client = httpx.Client(timeout=_TIMEOUT, trust_env=False)


def get_shared_client() -> httpx.Client:
    """供 worker 等其他模块复用同一连接池（同样直连不走系统代理）"""
    return _client


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
        resp = _client.get(url, params={"user_id": str(user_id)}, headers=_headers())
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
        resp = _client.post(url, json=payload, headers=_headers())
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


class JavaEvidenceError(RuntimeError):
    """Java 问答取证失败；不得将服务不可用或无权访问当成空知识库。"""


def _fetch_evidence(endpoint: str, params: dict) -> dict:
    if any(value is None or not str(value).strip() for value in params.values()):
        raise JavaEvidenceError(f"{endpoint}: 缺少取证参数")
    if not settings.java_token:
        raise JavaEvidenceError(f"{endpoint}: JAVA_TOKEN 未配置")
    url = f"{settings.java_base_url.rstrip('/')}/internal/agent/{endpoint}"
    try:
        response = _client.get(url, params={key: str(value) for key, value in params.items()},
                               headers=_headers())
        response.raise_for_status()
        body = response.json()
    except (httpx.HTTPError, ValueError) as exc:
        raise JavaEvidenceError(f"{endpoint}: Java 取证请求失败 ({exc})") from exc
    if not isinstance(body, dict) or body.get("code") != 0:
        code = body.get("code") if isinstance(body, dict) else None
        message = body.get("message") if isinstance(body, dict) else "响应格式错误"
        raise JavaEvidenceError(f"{endpoint}: Java 取证失败 code={code}, {message}")
    data = body.get("data")
    if not isinstance(data, dict):
        raise JavaEvidenceError(f"{endpoint}: Java 取证响应缺少有效 data")
    return data


def fetch_knowledge_outline(user_id, knowledge_id) -> dict:
    """获取经当前读取权限校验的完整 Wiki/文档目录；失败抛异常，合法空库返回空数组。"""
    data = _fetch_evidence("knowledge-outline", {"user_id": user_id, "knowledge_id": knowledge_id})
    if (not isinstance(data.get("wiki_pages"), list)
            or not isinstance(data.get("documents"), list)
            or not isinstance(data.get("total"), int)
            or not isinstance(data.get("version"), str)
            or not isinstance(data.get("uncovered_document_count"), int)):
        raise JavaEvidenceError("knowledge-outline: Java 目录响应格式错误")
    return data


def fetch_conversation_evidence(user_id, session_id) -> dict:
    """按所有权读取带真实消息 id 的历史；sources 已解析并按当前权限过滤。"""
    data = _fetch_evidence("conversation-evidence", {"user_id": user_id, "session_id": session_id})
    messages = data.get("messages")
    if not isinstance(messages, list) or any(
            not isinstance(message, dict) or not isinstance(message.get("sources"), list)
            for message in messages):
        raise JavaEvidenceError("conversation-evidence: Java 历史响应格式错误")
    return data
