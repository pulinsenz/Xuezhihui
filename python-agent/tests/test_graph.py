"""LangGraph 状态流转测试：路由 / 检索 / 反思循环 / 回答"""
from agent.graph import build_graph
from tests.conftest import FakeLLM, FakeRetriever


def _state(llm, retriever=None, query="数据结构是什么", knowledge_id="kb1", history=None, user_id=None):
    return {
        "query": query,
        "session_id": "test-session",
        "knowledge_id": knowledge_id,
        "user_id": user_id,
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


def test_graph_other_route_with_knowledge_id_still_retrieves():
    """路由误判为 other，但用户显式选了知识库 → 仍走检索，基于资料回答（回归：问自己上传的记录）"""
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="other", answer="15号你学了 hello_agent 文档。"),
                                 knowledge_id="kb1"))

    assert result["route"] == "other"
    assert result["sources"], "选了知识库即使路由判 other 也应检索"
    assert result["answer"]


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


def test_graph_business_calls_tool_and_answers(monkeypatch):
    """业务数据问题：router→tool(回调 Java)→answer，回答基于真实统计数据"""
    stats = {"knowledge_count": "2", "doc_count": "5", "vector_success": "4",
             "vector_pending": "1", "vector_failed": "0", "last_upload_time": "2026-08-23T10:00:00"}
    monkeypatch.setattr("agent.nodes.tool_agent.fetch_user_stats", lambda user_id: stats)
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="business", answer="你有 2 个知识库、5 篇文档。"),
                                 knowledge_id=None, user_id="1001"))

    assert result["route"] == "business"
    assert result["tool_called"] is True, "应回调 Java 拿到业务数据"
    assert "知识库数量：2" in result["tool_context"]
    assert "上传文档总数：5" in result["tool_context"]
    assert result["answer"] == "你有 2 个知识库、5 篇文档。"


def test_graph_business_callback_failure_degrades(monkeypatch):
    """回调 Java 失败（无 token/网络异常）→ 降级：不阻断对话，走普通回答"""
    monkeypatch.setattr("agent.nodes.tool_agent.fetch_user_stats", lambda user_id: None)
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="business", answer="暂时无法获取业务数据，请稍后再试。"),
                                 knowledge_id=None, user_id="1001"))

    assert result["route"] == "business"
    assert result["tool_called"] is False
    assert result["tool_context"] == ""
    assert result["answer"]


def test_graph_general_question_prefers_wiki():
    """泛化/总结类问题：检索走 Wiki 专用路径（retrieve_wiki）而非全库检索"""
    class RecordingRetriever(FakeRetriever):
        def __init__(self):
            super().__init__()
            self.calls = []

        def retrieve(self, query, knowledge_id, top_k=5):
            self.calls.append("retrieve")
            return self.docs

        def retrieve_wiki(self, query, knowledge_id, top_k=10):
            self.calls.append("retrieve_wiki")
            return self.docs

    retriever = RecordingRetriever()
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="kb", reflect="yes", answer="LangGraph 教程。"),
                                 retriever=retriever, query="当前知识库的内容是什么"))

    assert result["sources"], "应召回片段"
    assert "retrieve_wiki" in retriever.calls, "泛化问题应优先走 Wiki 检索路径"
    assert result["answer"] == "LangGraph 教程。"


def test_graph_general_question_falls_back_without_wiki():
    """泛化问题但知识库无 Wiki：回退全库混合检索"""
    class EmptyWikiRetriever(FakeRetriever):
        def __init__(self):
            super().__init__()
            self.calls = []

        def retrieve(self, query, knowledge_id, top_k=5):
            self.calls.append("retrieve")
            return self.docs

        def retrieve_wiki(self, query, knowledge_id, top_k=10):
            self.calls.append("retrieve_wiki")
            return []

    retriever = EmptyWikiRetriever()
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="kb", reflect="yes", answer="基于资料回答。"),
                                 retriever=retriever, query="总结一下这个知识库"))

    assert "retrieve_wiki" in retriever.calls and "retrieve" in retriever.calls, "无 Wiki 应回退全库检索"
    assert result["sources"], "回退后应召回片段"
    assert result["answer"] == "基于资料回答。"


def test_graph_general_retry_falls_back_to_full_search():
    """泛化问题首次 Wiki 检索证据不足 → 二次检索回退全库（避免具体问题被锁死在 Wiki 集合）"""
    class RetryRetriever(FakeRetriever):
        def __init__(self):
            super().__init__()
            self.calls = []

        def retrieve(self, query, knowledge_id, top_k=5):
            self.calls.append("retrieve")
            return self.docs

        def retrieve_wiki(self, query, knowledge_id, top_k=10):
            self.calls.append("retrieve_wiki")
            return self.docs

    retriever = RetryRetriever()
    graph = build_graph()
    result = graph.invoke(_state(FakeLLM(route="kb", reflect="no", answer="知识库中暂无相关信息。"),
                                 retriever=retriever, query="总结一下这个知识库"))

    assert result["retry_count"] >= 2, "证据不足应触发二次检索"
    assert "retrieve_wiki" in retriever.calls, "首次应走 Wiki 检索"
    assert "retrieve" in retriever.calls, "二次检索应回退全库检索"
