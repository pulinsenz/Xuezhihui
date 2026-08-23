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
                        lambda session_id, user_id, query, answer: persisted.append((session_id, user_id, query, answer)))

    chat_api._save_session("s1", "你好", "你好呀", "1001")

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
