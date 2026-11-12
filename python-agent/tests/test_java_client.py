"""Java 回调客户端测试：对话落库（persist_chat）

生产代码经模块级共享 httpx.Client（连接池复用）发请求，
因此 mock 的是 java_client._client（MockTransport），而非 httpx.post。
"""
import httpx

from utils import java_client


def _set_token(monkeypatch, token="test-token"):
    monkeypatch.setattr(java_client.settings, "java_token", token)


def _use_transport(monkeypatch, handler):
    """把共享客户端替换为 MockTransport 实现（测完自动还原）"""
    fake = httpx.Client(transport=httpx.MockTransport(handler), trust_env=False)
    monkeypatch.setattr(java_client, "_client", fake)
    return fake


def test_shared_client_is_direct():
    """真实共享客户端必须 trust_env=False：内部回调直连，不走系统代理
    （踩坑：Windows 注册表代理会把 localhost 出站发给代理 → 502）"""
    assert java_client._client.trust_env is False


def test_persist_chat_success(monkeypatch):
    """成功落库：URL/body/header 正确，返回 True"""
    _set_token(monkeypatch)
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = dict(request.headers)
        captured["json"] = __import__("json").loads(request.content)
        return httpx.Response(200, json={"code": 0, "message": "ok"})

    _use_transport(monkeypatch, handler)

    ok = java_client.persist_chat("s1", "1001", "你好", "你好呀")

    assert ok is True
    assert captured["url"].endswith("/internal/agent/chat-save")
    assert captured["json"] == {
        "session_id": "s1", "user_id": "1001", "query": "你好", "answer": "你好呀",
    }
    assert captured["headers"]["x-agent-token"] == "test-token"


def test_persist_chat_missing_user_id_returns_false(monkeypatch):
    """缺少 user_id 直接跳过，不发请求"""
    _set_token(monkeypatch)
    _use_transport(monkeypatch, lambda request: (_ for _ in ()).throw(AssertionError("不应发请求")))
    assert java_client.persist_chat("s1", None, "hi", "hi") is False


def test_persist_chat_no_token_returns_false(monkeypatch):
    """未配置 JAVA_TOKEN：降级不落库"""
    _set_token(monkeypatch, token="")
    assert java_client.persist_chat("s1", "1001", "hi", "hi") is False


def test_persist_chat_business_fail_degrades(monkeypatch):
    """Java 返回业务错误：降级 False，不抛异常"""
    _set_token(monkeypatch)
    _use_transport(monkeypatch, lambda request: httpx.Response(200, json={"code": 500, "message": "err"}))
    assert java_client.persist_chat("s1", "1001", "hi", "hi") is False


def test_persist_chat_network_error_degrades(monkeypatch):
    """网络异常：降级 False，不阻断对话"""
    _set_token(monkeypatch)

    def handler(request):
        raise ConnectionError("Java 未启动")

    _use_transport(monkeypatch, handler)
    assert java_client.persist_chat("s1", "1001", "hi", "hi") is False
