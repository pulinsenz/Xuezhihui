"""用户可见的范围覆盖、来源对应和访问边界回归。"""
import pytest
from agent.nodes.evidence import historical_evidence, outline_evidence
from agent.nodes.retrieve_agent import retrieve_agent
from tests.conftest import FakeLLM


def test_outline_covers_all_six_topics_without_similarity_search(monkeypatch):
    topics = ["环境配置", "LangGraph总览", "控制流", "持久化", "中断", "流式输出"]
    pages = [{"title": t, "summary": t + "的说明", "doc_id": "wiki-" + str(i)} for i, t in enumerate(topics)]
    monkeypatch.setattr("agent.nodes.evidence.fetch_knowledge_outline", lambda *a: {"wiki_pages": pages, "total": 6})
    result = retrieve_agent({"query": "这个库讲什么", "knowledge_id": "kb", "user_id": "u",
                             "intent_plan": {"intent": "knowledge", "scope": "knowledge_base", "operation": "overview"}})
    assert all(t in result["retrieval_context"] for t in topics)
    assert {s["doc_id"] for s in result["sources"]} == {p["doc_id"] for p in pages}
    assert result["evidence_complete"]


def test_outline_without_wiki_does_not_invent_document_content(monkeypatch):
    monkeypatch.setattr("agent.nodes.evidence.fetch_knowledge_outline", lambda *a: {
        "wiki_pages": [], "total": 0, "documents": [{"title": "教程.md", "doc_id": "doc1"}]})
    result = outline_evidence({"knowledge_id": "kb"})
    assert "未读取正文" in result["retrieval_context"]
    assert "不能据此推断正文" in result["sources"][0]["text"]


def test_outline_keeps_documents_not_covered_by_existing_wiki(monkeypatch):
    monkeypatch.setattr("agent.nodes.evidence.fetch_knowledge_outline", lambda *a: {
        "wiki_pages": [{"title": "持久化", "summary": "保存检查点", "doc_id": "wiki-1"}],
        "total": 1, "uncovered_document_count": 1,
        "documents": [{"title": "流式输出.md", "doc_id": "doc2"}]})
    result = outline_evidence({"knowledge_id": "kb"})
    assert "持久化" in result["retrieval_context"]
    assert "流式输出.md" in result["retrieval_context"]
    document = next(source for source in result["sources"] if source["doc_id"] == "doc2")
    assert "不能据此推断正文" in document["text"]


def test_incomplete_outline_is_not_reported_as_complete(monkeypatch):
    monkeypatch.setattr("agent.nodes.evidence.fetch_knowledge_outline", lambda *a: {"wiki_pages": [], "total": 6})
    with pytest.raises(ValueError):
        outline_evidence({})


def test_provenance_uses_requested_historical_sources(monkeypatch):
    messages = [{"id": "1", "role": "assistant", "content": "旧回答", "sources": [{"text": "旧原文", "doc_id": "old"}]},
                {"id": "2", "role": "assistant", "content": "新回答", "sources": [{"text": "新原文", "doc_id": "new"}]}]
    monkeypatch.setattr("agent.nodes.evidence.fetch_conversation_evidence", lambda *a: {"messages": messages})
    result = historical_evidence({"intent_plan": {"intent": "provenance", "target_message_id": "1"}})
    assert result["sources"] == messages[0]["sources"]
    assert "旧回答" in result["retrieval_context"] and "新原文" not in result["retrieval_context"]


def test_revoked_historical_evidence_is_not_exposed(monkeypatch):
    monkeypatch.setattr("agent.nodes.evidence.fetch_conversation_evidence", lambda *a: {"messages": [
        {"role": "assistant", "content": "", "access_revoked": True, "sources": []}]})
    result = historical_evidence({"intent_plan": {"intent": "transform"}})
    assert not result["sources"] and not result["evidence_complete"]
    assert "无权访问" in result["evidence_error"]


def test_large_outline_includes_every_entry_in_grouped_input(monkeypatch):
    pages = [{"title": f"主题{i}", "summary": "资料" * 1000, "doc_id": str(i)} for i in range(20)]
    monkeypatch.setattr("agent.nodes.evidence.fetch_knowledge_outline", lambda *a: {"wiki_pages": pages, "total": 20})
    class RecordingLLM(FakeLLM):
        def __init__(self): self.inputs = []
        def chat(self, messages):
            self.inputs.append(messages[-1]["content"])
            return "分组摘要"
    llm = RecordingLLM()
    result = outline_evidence({"llm": llm})
    sent = "\n".join(llm.inputs)
    assert all(f"主题{i}：" in sent for i in range(20))
    assert {s["doc_id"] for s in result["sources"]} == {str(i) for i in range(20)}
