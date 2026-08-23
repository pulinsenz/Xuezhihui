"""Java 回调客户端测试：对话落库（persist_chat）"""
import httpx

from utils import java_client


def _set_token(monkeypatch, token="test-token"):
    monkeypatch.setattr(java_client.settings, "java_token", token)


def test_persist_chat_success(monkeypatch):
    """成功落库：URL/body/header 正确，返回 True"""
    _set_token(monkeypatch)
    captured = {}

    def fake_post(url, json=None, headers=None, timeout=None, trust_env=None):
        captured["url"] = url
        captured["json"] = json
        captured["headers"] = headers
        captured["trust_env"] = trust_env
        return httpx.Response(200, json={"code": 0, "message": "ok"})

    monkeypatch.setattr(java_client.httpx, "post", fake_post)

    ok = java_client.persist_chat("s1", "1001", "你好", "你好呀")

    assert ok is True
    assert captured["url"].endswith("/internal/agent/chat-save")
    assert captured["json"] == {
        "session_id": "s1", "user_id": "1001", "query": "你好", "answer": "你好呀",
    }
    assert captured["headers"]["X-Agent-Token"] == "test-token"
    assert captured["trust_env"] is False, "内部回调必须直连，不走系统代理"


def test_persist_chat_missing_user_id_returns_false(monkeypatch):
    """缺少 user_id 直接跳过，不发请求"""
    _set_token(monkeypatch)
    monkeypatch.setattr(java_client.httpx, "post", lambda *a, **k: (_ for _ in ()).throw(AssertionError("不应发请求")))
    assert java_client.persist_chat("s1", None, "hi", "hi") is False


def test_persist_chat_no_token_returns_false(monkeypatch):
    """未配置 JAVA_TOKEN：降级不落库"""
    _set_token(monkeypatch, token="")
    assert java_client.persist_chat("s1", "1001", "hi", "hi") is False


def test_persist_chat_business_fail_degrades(monkeypatch):
    """Java 返回业务错误：降级 False，不抛异常"""
    _set_token(monkeypatch)
    monkeypatch.setattr(java_client.httpx, "post",
                        lambda *a, **k: httpx.Response(200, json={"code": 500, "message": "err"}))
    assert java_client.persist_chat("s1", "1001", "hi", "hi") is False


def test_persist_chat_network_error_degrades(monkeypatch):
    """网络异常：降级 False，不阻断对话"""
    _set_token(monkeypatch)

    def fake_post(*a, **k):
        raise ConnectionError("Java 未启动")

    monkeypatch.setattr(java_client.httpx, "post", fake_post)
    assert java_client.persist_chat("s1", "1001", "hi", "hi") is False
