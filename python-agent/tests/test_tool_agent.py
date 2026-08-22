"""工具 Agent 回调 Java 测试：数据格式化 / 节点行为 / Java 客户端降级"""
from config import settings

from agent.nodes.tool_agent import _format_stats, tool_agent


def test_format_stats_full():
    text = _format_stats({"knowledge_count": "2", "doc_count": "5", "vector_success": "3",
                          "vector_pending": "1", "vector_failed": "1",
                          "last_upload_time": "2026-08-23T10:00:00"})
    assert "知识库数量：2 个" in text
    assert "上传文档总数：5 篇" in text
    assert "已向量化：3 篇" in text
    assert "处理中：1 篇" in text
    assert "失败：1 篇" in text
    assert "最近上传时间：2026-08-23T10:00:00" in text


def test_format_stats_missing_fields_default_zero():
    """Java 返回 null/缺字段 → 补 0，避免 'None 个' 拼进回答"""
    text = _format_stats({"knowledge_count": None, "doc_count": "", "vector_failed": 0})
    assert "知识库数量：0 个" in text
    assert "上传文档总数：0 篇" in text
    assert "失败：0 篇" in text


def test_tool_agent_without_user_id():
    """无 user_id（未登录透传）→ 不回调，降级为空上下文"""
    result = tool_agent({"user_id": None})
    assert result["tool_called"] is False
    assert result["tool_context"] == ""


def test_tool_agent_calls_java_with_user_id(monkeypatch):
    captured = {}

    def fake_fetch(user_id):
        captured["user_id"] = user_id
        return {"knowledge_count": "2", "doc_count": "4", "vector_success": "4",
                "vector_pending": "0", "vector_failed": "0"}

    monkeypatch.setattr("agent.nodes.tool_agent.fetch_user_stats", fake_fetch)
    result = tool_agent({"user_id": "1001"})
    assert captured["user_id"] == "1001"
    assert result["tool_called"] is True
    assert "知识库数量：2" in result["tool_context"]


def test_tool_agent_callback_failure_degrades(monkeypatch):
    monkeypatch.setattr("agent.nodes.tool_agent.fetch_user_stats", lambda user_id: None)
    result = tool_agent({"user_id": "1001"})
    assert result["tool_called"] is False
    assert result["tool_context"] == ""


# ---- Java 回调客户端（不发起真实网络请求） ----

def test_fetch_no_token_returns_none(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "")
    from utils.java_client import fetch_user_stats
    assert fetch_user_stats("1") is None


def test_fetch_connection_error_degrades(monkeypatch):
    """Java 未启动/连接拒绝 → 异常被捕获，降级 None 不抛"""
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://127.0.0.1:1")
    from utils.java_client import fetch_user_stats
    assert fetch_user_stats("1") is None


def test_fetch_success_parses_data(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://java:8123/api")

    class FakeResp:
        status_code = 200

        def json(self):
            return {"code": 0, "message": "ok", "data": {"knowledge_count": "2", "doc_count": "5"}}

    import utils.java_client as jc
    monkeypatch.setattr(jc.httpx, "get", lambda *a, **k: FakeResp())
    data = jc.fetch_user_stats("1")
    assert data == {"knowledge_count": "2", "doc_count": "5"}


def test_fetch_business_error_returns_none(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://java:8123/api")

    class FakeResp:
        status_code = 200

        def json(self):
            return {"code": 40101, "message": "内部接口未授权", "data": None}

    import utils.java_client as jc
    monkeypatch.setattr(jc.httpx, "get", lambda *a, **k: FakeResp())
    assert jc.fetch_user_stats("1") is None
