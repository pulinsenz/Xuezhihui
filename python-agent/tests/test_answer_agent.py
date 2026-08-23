"""回答节点提示词选择测试：业务数据 > 检索证据 > 未选知识库引导 > 闲聊"""
from agent.nodes.answer_agent import (
    ANSWER_SYSTEM_TEMPLATE,
    CHITCHAT_SYSTEM,
    NO_KB_SYSTEM,
    TOOL_SYSTEM_TEMPLATE,
    answer_agent,
)


class RecordingLLM:
    """记录系统提示词的假 LLM"""

    def __init__(self, answer="test answer"):
        self.answer = answer
        self.last_system = None

    def stream(self, messages):
        self.last_system = messages[0]["content"]
        for token in self.answer.split(" "):
            yield token + " "


def _run(route, context="", tool_context=""):
    llm = RecordingLLM()
    state = {
        "query": "数据结构是什么",
        "route": route,
        "history": [],
        "retrieval_context": context,
        "tool_context": tool_context,
        "sources": [],
        "llm": llm,
    }
    result = answer_agent(state)
    return llm.last_system, result["answer"]


def test_answer_uses_tool_context_first():
    system, _ = _run(route="business", tool_context="知识库数量：2")
    assert system == TOOL_SYSTEM_TEMPLATE.format(context="知识库数量：2")


def test_answer_uses_retrieval_context():
    system, _ = _run(route="kb", context="链表、栈、队列。")
    assert system == ANSWER_SYSTEM_TEMPLATE.format(context="链表、栈、队列。")


def test_answer_kb_without_kb_guides_select():
    """知识问题但未选知识库 → 用引导提示词而非空泛闲聊"""
    system, answer = _run(route="kb")
    assert system == NO_KB_SYSTEM
    assert "选择知识库" in system


def test_answer_chitchat_uses_chitchat_prompt():
    system, _ = _run(route="chitchat")
    assert system == CHITCHAT_SYSTEM
