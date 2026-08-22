"""LangGraph 状态流转测试：路由 / 检索 / 反思循环 / 回答"""
from agent.graph import build_graph
from tests.conftest import FakeLLM, FakeRetriever


def _state(llm, retriever=None, query="数据结构是什么", knowledge_id="kb1", history=None):
    return {
        "query": query,
        "session_id": "test-session",
        "knowledge_id": knowledge_id,
        "history": history or [],
        "llm": llm,
        "retriever": retriever or FakeRetriever(),
        "retry_count": 0,
        "max_retries": 2,
        "sources": [],
    }


def test_graph_kb_route_answers_with_sources():
    """知识库问答：router→retrieve→reflect(够)→answer，回答带引用来源"""
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="kb", reflect="yes", answer="链表、栈、队列。")))

    assert result["route"] == "kb"
    assert result["sources"], "应召回知识库片段"
    assert result["answer"] == "链表、栈、队列。"
    assert result["retry_count"] == 1, "证据足够不应二次检索"


def test_graph_chitchat_skips_retrieval():
    """闲聊：router 直接到 answer，不检索"""
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="chitchat", answer="你好！我是学智汇助手。"),
                                 retriever=FakeRetriever(), knowledge_id=None))

    assert result["route"] == "chitchat"
    assert result["sources"] == []
    assert "你好" in result["answer"]


def test_graph_insufficient_evidence_triggers_retry_loop():
    """反思判定证据不足 → 触发二次检索（循环重试），最终仍拒绝编造"""
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="kb", reflect="no", answer="知识库中暂无相关信息。")))

    assert result["route"] == "kb"
    assert result["retry_count"] >= 2, "证据不足应触发二次检索"
    assert result["answer"], "最终仍输出回答（拒绝编造）"


def test_graph_no_knowledge_id_skips_retrieval():
    """未指定知识库 → 跳过检索，直接回答（走 chitchat 兜底）"""
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="kb"), knowledge_id=None))

    assert result["route"] == "kb"
    assert result["sources"] == []
    assert result["answer"]
