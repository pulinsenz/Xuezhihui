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
    user_role: Optional[str] = None  # 受信角色：user/admin，Java 从 JWT 解析后注入，决定用哪个 LLM


class ChatResponse(BaseModel):
    answer: str
    sources: list = []
    route: str = ""
    session_id: str


def _pick_llm(user_role: str = None):
    """按角色选 LLM：管理员走 DeepSeek，普通用户走 USER_API_KEY 的独立 LLM。
    user_llm 未配置（无 USER_API_KEY）时回退 DeepSeek。"""
    if user_role == "admin":
        return runtime.llm
    if runtime.user_llm is not None:
        return runtime.user_llm
    if runtime.llm is not None:
        logger.warning("USER_API_KEY 未配置，普通用户 LLM 回退为 DeepSeek")
        return runtime.llm
    raise RuntimeError("LLM 未初始化")


def _build_state(query: str, session_id: str, knowledge_id: str, history: list,
                 user_id: str = None, user_role: str = None) -> dict:
    return {
        "query": query,
        "session_id": session_id,
        "knowledge_id": knowledge_id,
        "user_id": user_id,
        "history": history,
        "llm": _pick_llm(user_role),
        "retriever": runtime.retriever,
        "reranker": runtime.reranker,
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


def _cache_lookup(knowledge_id, query):
    """答案缓存查询（business/历史依赖的回答不会入库，见 answer_cache）。
    缓存层内部全程降级容错；未启用/未初始化返回 None。调用方保证只在会话首问时调用。"""
    cache = runtime.answer_cache
    if cache is None:
        return None
    return cache.lookup(knowledge_id, query)


def _cache_store(knowledge_id, query, answer, sources, route, intent):
    """图跑完后写入缓存（可缓存性在 AnswerCache.store 内部硬过滤）。"""
    cache = runtime.answer_cache
    if cache is None:
        return
    cache.store(knowledge_id, query, answer, sources, route, intent)


@router.post("/chat")
def chat(req: ChatRequest):
    """普通对话（非流式），返回完整回答"""
    if not req.session_id or not req.query.strip():
        raise HTTPException(status_code=400, detail="session_id 和 query 不能为空")
    history = runtime.redis_store.get_history(req.session_id)
    # 新会话首问先查答案缓存（重复问题省 token 与延迟）
    cache_hit = _cache_lookup(req.knowledge_id, req.query) if not history else None
    if cache_hit:
        answer = cache_hit["answer"]
        route = cache_hit.get("route", "")
        sources = cache_hit.get("sources", [])
        match_label = "精确匹配" if cache_hit.get("match") == "exact" else "语义匹配"
        thinking = [f"命中答案缓存（{match_label}），直接返回历史回答"]
        _save_session(req.session_id, req.query, answer, req.user_id, route,
                      req.knowledge_id, thinking, sources)
        return {
            "code": 0, "message": "ok",
            "data": ChatResponse(answer=answer, sources=sources, route=route,
                                 session_id=req.session_id).model_dump(),
        }
    graph = get_graph()
    result = graph.invoke(_build_state(req.query, req.session_id, req.knowledge_id, history,
                                       req.user_id, req.user_role))
    answer = result.get("answer", "")
    route = result.get("route", "")
    sources = result.get("sources", [])
    # 非流式没有节点轨迹，构造最简思考过程
    thinking = [f"判断问题类型：{_route_label(route)}", "正在生成回答…"]
    _save_session(req.session_id, req.query, answer, req.user_id, route, req.knowledge_id, thinking, sources)
    # 首问结果写入缓存（business/历史依赖的回答在缓存层内部被过滤）
    if not history:
        intent = (result.get("intent_plan") or {}).get("intent", "")
        _cache_store(req.knowledge_id, req.query, answer, sources, route, intent)
    return {
        "code": 0, "message": "ok",
        "data": ChatResponse(
            answer=answer,
            sources=result.get("sources", []),
            route=result.get("route", ""),
            session_id=req.session_id,
        ).model_dump(),
    }


class StreamRequest(BaseModel):
    """SSE 流式对话请求（POST body；改自 GET query，问句全文不再落访问日志）"""
    session_id: str
    query: str
    knowledge_id: Optional[str] = None
    user_id: Optional[str] = None  # 受信身份：Java 从 JWT 解析后注入
    user_role: Optional[str] = None  # 受信角色：user/admin，Java 从 JWT 解析后注入，决定用哪个 LLM


@router.post("/stream")
def stream(req: StreamRequest):
    """SSE 流式对话：逐 token 输出 + thinking 思考过程事件，末尾带 done 事件（含回答/来源/路由）"""
    session_id = req.session_id
    query = req.query
    knowledge_id = req.knowledge_id
    user_id = req.user_id
    user_role = req.user_role
    if not session_id or not query.strip():
        raise HTTPException(status_code=400, detail="session_id 和 query 不能为空")
    history = runtime.redis_store.get_history(session_id)
    # 新会话首问先查答案缓存（重复问题省 token 与延迟）
    cache_hit = _cache_lookup(knowledge_id, query) if not history else None
    state = _build_state(query, session_id, knowledge_id, history, user_id, user_role)
    graph = get_graph()

    def sse_event(payload: dict) -> str:
        return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"

    async def generate():
        tokens = []
        final_answer = ""
        sources = []
        route = ""
        intent = ""
        thinking_steps = []
        saved = False

        def save_once():
            """所有出口（正常/断连/异常）只落一次历史：客户端断连抛 GeneratorExit
            （继承 BaseException，except Exception 接不住），旧实现会整体跳过保存，
            导致用户问过的问题既不进 Redis 会话也不落 MySQL。无任何回答时不落库。"""
            nonlocal saved
            if saved:
                return
            saved = True
            answer = final_answer or "".join(tokens)
            if answer:
                _save_session(session_id, query, answer, user_id, route, knowledge_id, thinking_steps, sources)

        def emit_thinking(content: str):
            thinking_steps.append(content)
            return sse_event({"type": "thinking", "content": content})

        if cache_hit:
            # 缓存命中回放：thinking + 分块 token（保持前端打字机效果）+ done（cached 扩展字段，
            # 旧前端不识别会忽略，行为不变）
            match_label = "精确匹配" if cache_hit.get("match") == "exact" else "语义匹配"
            final_answer = cache_hit["answer"]
            route = cache_hit.get("route", "")
            sources = cache_hit.get("sources", [])
            try:
                yield emit_thinking(f"命中答案缓存（{match_label}），直接返回历史回答")
                for i in range(0, len(final_answer), 16):
                    chunk = final_answer[i:i + 16]
                    tokens.append(chunk)
                    yield sse_event({"type": "token", "content": chunk})
                yield sse_event({"type": "done", "answer": final_answer, "sources": sources,
                                 "route": route, "knowledge_id": knowledge_id, "cached": True})
            finally:
                save_once()
            return

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
                            intent = (update.get("intent_plan") or {}).get("intent", "")
                            yield emit_thinking(f"判断问题类型：{_route_label(route)}")
                        elif node == "tool":
                            if update.get("tool_called"):
                                yield emit_thinking(f"调用工具：查询业务数据 → {update.get('tool_context', '')}")
                            else:
                                yield emit_thinking("调用业务数据工具：无数据或已降级为普通回答")
                        elif node == "retrieve":
                            sources = update.get("sources", [])
                            retry = update.get("retry_count", 1)
                            kind = update.get("evidence_kind")
                            if update.get("evidence_error"):
                                yield emit_thinking("资料读取受限，回答将说明限制")
                            elif kind == "outline":
                                yield emit_thinking(f"读取知识目录，共 {len(sources)} 个条目")
                            elif kind in ("provenance", "transform"):
                                yield emit_thinking("读取对应历史回答及其实际引用")
                            else:
                                yield emit_thinking(f"检索知识库，命中 {len(sources)} 条资料（第 {retry} 次检索）")
                        elif node == "reflect":
                            sufficient = update.get("sufficient", False)
                            yield emit_thinking("证据校验：" + ("证据充分，开始回答" if sufficient else "证据不足，继续检索"))
                        elif node == "answer":
                            yield emit_thinking("正在生成回答…")
                            if update.get("answer"):
                                final_answer = update["answer"]
                            # answer 节点已按正文引用过滤 sources（未被引用的分片不展示）
                            if update.get("sources"):
                                sources = update["sources"]
            # 会话记忆 + 历史持久化（含回答属性/思考过程/参考文献）
            if not final_answer:
                final_answer = "".join(tokens)
            save_once()
            # 首问结果写入缓存（business/历史依赖的回答在缓存层内部被过滤）
            if not history and final_answer:
                _cache_store(knowledge_id, query, final_answer, sources, route, intent)
            yield sse_event({"type": "done", "answer": final_answer, "sources": sources,
                             "route": route, "knowledge_id": knowledge_id})
        except Exception as e:
            logger.error("流式对话异常: %s", e)
            yield sse_event({"type": "error", "message": str(e)})
        finally:
            # 兜底所有出口：正常结束、客户端断连（GeneratorExit 继承 BaseException，
            # 旧实现因 except Exception 接不住而整体跳过保存——用户问过的问题
            # 既不进 Redis 会话也不落 MySQL）、以及中途异常
            save_once()

    return StreamingResponse(generate(), media_type="text/event-stream")
