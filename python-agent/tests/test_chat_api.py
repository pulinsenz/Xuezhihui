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


# ---------- SSE 流式接口（POST 契约）----------

class FakeStoreWithHistory(FakeStore):
    def get_history(self, session_id):
        return []


def _fake_graph():
    """可控的假 LangGraph：路由 → token → 最终回答，三步流式输出"""
    class _Msg:
        content = "答案"

    class FakeGraph:
        async def astream(self, state, stream_mode=None):
            yield ("updates", {"router": {"route": "kb"}})
            yield ("messages", (_Msg(), {"langgraph_node": "answer"}))
            yield ("updates", {"answer": {"answer": "答案", "sources": []}})

    return FakeGraph()


def _stream_client(monkeypatch, token="t"):
    from fastapi.testclient import TestClient
    import main as app_main

    monkeypatch.setattr(chat_api.runtime, "redis_store", FakeStoreWithHistory())
    monkeypatch.setattr(chat_api.runtime, "llm", _FakeLLM("deepseek"))
    monkeypatch.setattr(chat_api, "persist_chat", lambda *a, **k: None)
    monkeypatch.setattr(chat_api, "get_graph", lambda: _fake_graph())
    monkeypatch.setattr(app_main.settings, "agent_token", token)
    return TestClient(app_main.app)


def test_stream_post_returns_sse_done(monkeypatch):
    """POST body 契约：合法请求返回 SSE，末尾含 done 事件且落一次历史"""
    client = _stream_client(monkeypatch)
    resp = client.post("/api/agent/stream",
                       json={"session_id": "s1", "query": "你好"},
                       headers={"X-Agent-Token": "t"})
    assert resp.status_code == 200
    assert '"type":"done"' in resp.text or '"type": "done"' in resp.text
    # finally 兜底：历史落库（旧实现仅正常路径保存）
    assert ("assistant", "答案") in chat_api.runtime.redis_store.calls


def test_stream_rejects_without_token(monkeypatch):
    """缺 X-Agent-Token 一律 401（内部鉴权 fail-closed）"""
    client = _stream_client(monkeypatch, token="t")
    resp = client.post("/api/agent/stream", json={"session_id": "s1", "query": "你好"})
    assert resp.status_code == 401
