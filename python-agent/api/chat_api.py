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


def _save_session(session_id: str, query: str, answer: str, user_id: str = None,
                  route: str = "", knowledge_id: str = None, thinking: list = None, sources: list = None):
    # Redis 会话记忆（LLM 上下文，窗口裁剪）
    runtime.redis_store.append_message(session_id, "user", query)
    runtime.redis_store.append_message(session_id, "assistant", answer)
    # MySQL 持久化历史（回调 Java 落库，含回答属性/思考过程/参考文献），失败降级不阻断对话
    persist_chat(session_id, user_id, query, answer, route, knowledge_id, thinking, sources)


_ROUTE_LABELS = {
    "kb": "知识库问答",
    "business": "业务数据查询",
    "chitchat": "闲聊",
    "other": "通用问答",
}


def _route_label(route: str) -> str:
    """路由类型 → 思考过程里的可读描述"""
    return _ROUTE_LABELS.get(route, route or "未知")


@router.post("/chat")
def chat(req: ChatRequest):
    """普通对话（非流式），返回完整回答"""
    if not req.session_id or not req.query.strip():
        raise HTTPException(status_code=400, detail="session_id 和 query 不能为空")
    history = runtime.redis_store.get_history(req.session_id)
    graph = get_graph()
    result = graph.invoke(_build_state(req.query, req.session_id, req.knowledge_id, history, req.user_id))
    answer = result.get("answer", "")
    route = result.get("route", "")
    sources = result.get("sources", [])
    # 非流式没有节点轨迹，构造最简思考过程
    thinking = [f"判断问题类型：{_route_label(route)}", "正在生成回答…"]
    _save_session(req.session_id, req.query, answer, req.user_id, route, req.knowledge_id, thinking, sources)
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
    """SSE 流式对话：逐 token 输出 + thinking 思考过程事件，末尾带 done 事件（含回答/来源/路由）"""
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
        route = ""
        thinking_steps = []

        def emit_thinking(content: str):
            thinking_steps.append(content)
            return sse_event({"type": "thinking", "content": content})

        try:
            # stream_mode=["messages","updates"]：messages 输出 LLM token，updates 输出各节点结果
            async for mode, payload in graph.astream(state, stream_mode=["messages", "updates"]):
                if mode == "messages":
                    msg, meta = payload
                    # 只收集回答节点的 token：路由/反思/工具等非流式 LLM 输出也会进入 messages 流
                    # （否则 "kb"、"yes" 等会被误拼进回答），按节点名过滤
                    if meta.get("langgraph_node") != "answer":
                        continue
                    token = msg.content if hasattr(msg, "content") else str(msg)
                    if token:
                        tokens.append(token)
                        yield sse_event({"type": "token", "content": token})
                else:
                    for node, update in payload.items():
                        if node == "router":
                            route = update.get("route", "")
                            yield emit_thinking(f"判断问题类型：{_route_label(route)}")
                        elif node == "tool":
                            if update.get("tool_called"):
                                yield emit_thinking(f"调用工具：查询业务数据 → {update.get('tool_context', '')}")
                            else:
                                yield emit_thinking("调用业务数据工具：无数据或已降级为普通回答")
                        elif node == "retrieve":
                            sources = update.get("sources", [])
                            retry = update.get("retry_count", 1)
                            yield emit_thinking(f"检索知识库，命中 {len(sources)} 条资料（第 {retry} 次检索）")
                        elif node == "reflect":
                            sufficient = update.get("sufficient", False)
                            yield emit_thinking("证据校验：" + ("证据充分，开始回答" if sufficient else "证据不足，继续检索"))
                        elif node == "answer":
                            yield emit_thinking("正在生成回答…")
                            if update.get("answer"):
                                final_answer = update["answer"]
            # 会话记忆 + 历史持久化（含回答属性/思考过程/参考文献）
            if not final_answer:
                final_answer = "".join(tokens)
            _save_session(session_id, query, final_answer, user_id, route, knowledge_id, thinking_steps, sources)
            yield sse_event({"type": "done", "answer": final_answer, "sources": sources,
                             "route": route, "knowledge_id": knowledge_id})
        except Exception as e:
            logger.error("流式对话异常: %s", e)
            yield sse_event({"type": "error", "message": str(e)})

    return StreamingResponse(generate(), media_type="text/event-stream")
