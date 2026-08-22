"""
LangGraph 状态图构建：
router → (business) tool → answer                    （工具回调 Java 业务数据）
      → (kb) retrieve → reflect →(不足且未达上限) retrieve / (足够) answer
      → (闲聊/其他) answer
支持证据不足时的循环重试（最多 max_retries 次检索）。
"""
from langgraph.graph import END, START, StateGraph

from agent.nodes.answer_agent import answer_agent
from agent.nodes.reflect_agent import reflect_agent
from agent.nodes.retrieve_agent import retrieve_agent
from agent.nodes.router_agent import router_agent
from agent.nodes.tool_agent import tool_agent
from agent.state import AgentState

MAX_RETRIES = 2


def build_graph():
    graph = StateGraph(AgentState)
    graph.add_node("router", router_agent)
    graph.add_node("tool", tool_agent)
    graph.add_node("retrieve", retrieve_agent)
    graph.add_node("reflect", reflect_agent)
    graph.add_node("answer", answer_agent)

    graph.add_edge(START, "router")
    # 路由：业务数据 → 工具回调；知识库问答 → 检索；闲聊/其他 → 直接回答
    graph.add_conditional_edges(
        "router",
        _route_next,
        {"tool": "tool", "retrieve": "retrieve", "answer": "answer"},
    )
    graph.add_edge("tool", "answer")
    graph.add_edge("retrieve", "reflect")
    # 反思：证据不足且未达重试上限 → 二次检索（形成闭环）
    graph.add_conditional_edges(
        "reflect",
        _should_retry,
        {"retrieve": "retrieve", "answer": "answer"},
    )
    graph.add_edge("answer", END)
    return graph.compile()


# 单例：图结构不变，避免每个请求重建
_graph = None


def get_graph():
    global _graph
    if _graph is None:
        _graph = build_graph()
    return _graph


def _route_next(state):
    route = state.get("route")
    if route == "business":
        return "tool"
    # 知识库问答且指定了知识库 → 检索；否则（闲聊/其他/无知识库）直接回答，避免无效循环
    if route == "kb" and state.get("knowledge_id"):
        return "retrieve"
    return "answer"


def _should_retry(state):
    sufficient = state.get("sufficient", False)
    retry_count = state.get("retry_count", 0)
    max_retries = state.get("max_retries", MAX_RETRIES)
    if not sufficient and retry_count < max_retries:
        return "retrieve"
    return "answer"
