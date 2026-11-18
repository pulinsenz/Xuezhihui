import json
import pytest
from agent.nodes.router_agent import router_agent
from agent.graph import build_graph
from tests.conftest import FakeLLM


class IntentLLM(FakeLLM):
    def __init__(self, plan):
        super().__init__()
        self.plan = plan
        self.router_input = None
    def chat(self, messages):
        if "路由判断器" in messages[0]["content"]:
            self.router_input = json.loads(messages[-1]["content"])
            return json.dumps(self.plan, ensure_ascii=False)
        return super().chat(messages)


@pytest.mark.parametrize("intent,scope,operation", [
    ("knowledge", "knowledge_base", "overview"), ("knowledge", "topic", "overview"),
    ("provenance", "history", "verify"), ("transform", "history", "rewrite"),
    ("business", "none", "stats"),
])
def test_structured_plan_preserves_scope_and_resolved_reference(intent, scope, operation):
    llm = IntentLLM({"route": "kb", "intent": intent, "scope": scope,
                     "operation": operation, "resolved_query": "解释持久化的第二种模式"})
    history = [{"role": "assistant", "content": "持久化有两种模式"}]
    result = router_agent({"query": "第二种呢", "knowledge_id": "new-kb", "history": history, "llm": llm})
    assert result["intent_plan"]["scope"] == scope
    assert result["intent_plan"]["resolved_query"] == "解释持久化的第二种模式"
    assert llm.router_input["history"] == history
    assert llm.router_input["knowledge_id"] == "new-kb"
    assert result["route"] == ("business" if intent == "business" else "kb")


def test_topic_summary_does_not_read_whole_library(monkeypatch):
    def forbidden(*args): raise AssertionError("限定主题不能获取全库目录")
    monkeypatch.setattr("agent.nodes.evidence.fetch_knowledge_outline", forbidden)
    class Retriever:
        def retrieve(self, query, knowledge_id, top_k):
            assert query == "总结持久化章节"
            return [{"text": "持久化保存检查点", "doc_id": "p"}]
    llm = IntentLLM({"route": "kb", "intent": "knowledge", "scope": "topic", "operation": "overview",
                    "resolved_query": "总结持久化章节"})
    result = build_graph().invoke({"query": "总结这章", "llm": llm, "retriever": Retriever(),
                                   "knowledge_id": "kb", "history": [], "max_retries": 1})
    assert result["sources"][0]["doc_id"] == "p"
    assert result["evidence_kind"] == "retrieval"
