"""chat_api 测试：_save_session 保留 Redis 上下文并回调 Java 落库"""
from api import chat_api


class FakeStore:
    """记录 append 调用，不连 Redis"""

    def __init__(self):
        self.calls = []

    def append_message(self, session_id, role, content):
        self.calls.append((role, content))


def test_save_session_appends_context_and_persists(monkeypatch):
    store = FakeStore()
    persisted = []

    monkeypatch.setattr(chat_api.runtime, "redis_store", store)
    monkeypatch.setattr(chat_api, "persist_chat",
                        lambda session_id, user_id, query, answer, *a, **k: persisted.append((session_id, user_id, query, answer)))

    chat_api._save_session("s1", "你好", "你好呀", "1001", "kb", "kb1",
                           ["判断问题类型：知识库问答"], [{"text": "片段A"}])

    assert ("user", "你好") in store.calls
    assert ("assistant", "你好呀") in store.calls
    assert persisted == [("s1", "1001", "你好", "你好呀")]


def test_save_session_persist_failure_does_not_block(monkeypatch):
    """落库失败（persist_chat 返回 False）不影响 Redis 上下文"""
    store = FakeStore()
    monkeypatch.setattr(chat_api.runtime, "redis_store", store)
    monkeypatch.setattr(chat_api, "persist_chat", lambda *a, **k: False)

    chat_api._save_session("s1", "hi", "hi", "1001")  # 不应抛异常
    assert len(store.calls) == 2


# ---------- 按角色选 LLM ----------

class _FakeLLM:
    def __init__(self, name):
        self.name = name


def test_pick_llm_admin_uses_deepseek(monkeypatch):
    deepseek = _FakeLLM("deepseek")
    monkeypatch.setattr(chat_api.runtime, "llm", deepseek)
    monkeypatch.setattr(chat_api.runtime, "user_llm", _FakeLLM("user"))
    assert chat_api._pick_llm("admin").name == "deepseek"


def test_pick_llm_user_uses_user_llm(monkeypatch):
    user_llm = _FakeLLM("user")
    monkeypatch.setattr(chat_api.runtime, "llm", _FakeLLM("deepseek"))
    monkeypatch.setattr(chat_api.runtime, "user_llm", user_llm)
    assert chat_api._pick_llm("user").name == "user"
    # 缺省角色（Java 未传）也按普通用户处理
    assert chat_api._pick_llm(None).name == "user"
    assert chat_api._pick_llm("").name == "user"


def test_pick_llm_fallback_when_no_user_llm(monkeypatch):
    """未配置 USER_API_KEY 时普通用户回退 DeepSeek"""
    deepseek = _FakeLLM("deepseek")
    monkeypatch.setattr(chat_api.runtime, "llm", deepseek)
    monkeypatch.setattr(chat_api.runtime, "user_llm", None)
    assert chat_api._pick_llm("user").name == "deepseek"
