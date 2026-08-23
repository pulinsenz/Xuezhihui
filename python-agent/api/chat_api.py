"""
对话接口：普通对话 + SSE 流式（被 Java SSE 透传给前端）
"""
import json

from typing import Optional

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from agent import runtime
from agent.graph import get_graph
from utils.java_client import persist_chat
from utils.logger_util import get_logger, agent_event

logger = get_logger("chat_api")

router = APIRouter(prefix="/api/agent", tags=["agent"])

MAX_RETRIES = 2


class ChatRequest(BaseModel):
    session_id: str
    query: str
    knowledge_id: Optional[str] = None
    user_id: Optional[str] = None  # 受信身份：Java 从 JWT 解析后注入，前端/外部不可伪造


class ChatResponse(BaseModel):
    answer: str
    sources: list = []
    route: str = ""
    session_id: str


def _build_state(query: str, session_id: str, knowledge_id: str, history: list, user_id: str = None) -> dict:
    return {
        "query": query,
        "session_id": session_id,
        "knowledge_id": knowledge_id,
        "user_id": user_id,
        "history": history,
        "llm": runtime.llm,
        "retriever": runtime.retriever,
        "retry_count": 0,
        "max_retries": MAX_RETRIES,
        "sources": [],
        "tool_called": False,
        "tool_context": "",
    }


def _save_session(session_id: str, query: str, answer: str, user_id: str = None):
    # Redis 会话记忆（LLM 上下文，窗口裁剪）
    runtime.redis_store.append_message(session_id, "user", query)
    runtime.redis_store.append_message(session_id, "assistant", answer)
    # MySQL 持久化历史（回调 Java 落库），失败降级不阻断对话
    persist_chat(session_id, user_id, query, answer)


@router.post("/chat")
def chat(req: ChatRequest):
    """普通对话（非流式），返回完整回答"""
    if not req.session_id or not req.query.strip():
        raise HTTPException(status_code=400, detail="session_id 和 query 不能为空")
    history = runtime.redis_store.get_history(req.session_id)
    graph = get_graph()
    result = graph.invoke(_build_state(req.query, req.session_id, req.knowledge_id, history, req.user_id))
    answer = result.get("answer", "")
    _save_session(req.session_id, req.query, answer, req.user_id)
    return {
        "code": 0, "message": "ok",
        "data": ChatResponse(
            answer=answer,
            sources=result.get("sources", []),
            route=result.get("route", ""),
            session_id=req.session_id,
        ).model_dump(),
    }


@router.get("/stream")
def stream(session_id: str, query: str, knowledge_id: str = None, user_id: str = None):
    """SSE 流式对话：逐 token 输出，末尾带 done 事件（含完整回答与来源）"""
    if not session_id or not query.strip():
        raise HTTPException(status_code=400, detail="session_id 和 query 不能为空")
    history = runtime.redis_store.get_history(session_id)
    state = _build_state(query, session_id, knowledge_id, history, user_id)
    graph = get_graph()

    def sse_event(payload: dict) -> str:
        return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"

    async def generate():
        tokens = []
        final_answer = ""
        sources = []
        try:
            # stream_mode=["messages","updates"]：messages 输出 LLM token，updates 输出各节点结果
            async for mode, payload in graph.astream(state, stream_mode=["messages", "updates"]):
                if mode == "messages":
                    msg, _meta = payload
                    token = msg.content if hasattr(msg, "content") else str(msg)
                    if token:
                        tokens.append(token)
                        yield sse_event({"type": "token", "content": token})
                else:
                    for node, update in payload.items():
                        if node == "answer" and update.get("answer"):
                            final_answer = update["answer"]
                        if node == "retrieve":
                            sources = update.get("sources", [])
            # 会话记忆（流式用缓存 token 拼完整回答）
            if not final_answer:
                final_answer = "".join(tokens)
            _save_session(session_id, query, final_answer, user_id)
            yield sse_event({"type": "done", "answer": final_answer, "sources": sources})
        except Exception as e:
            logger.error("流式对话异常: %s", e)
            yield sse_event({"type": "error", "message": str(e)})

    return StreamingResponse(generate(), media_type="text/event-stream")
