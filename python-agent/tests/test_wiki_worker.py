"""AI Wiki worker 测试：状态流转、回调 Java 请求体、无 token 降级"""
from config import settings

from worker.wiki_worker import WikiWorker


class FakeTaskStore:
    """记录 update_status 调用，不连真实 Redis"""

    def __init__(self):
        self.calls = []

    def update_status(self, task_id, status, message=None, **extra):
        self.calls.append({"task_id": task_id, "status": status, "message": message, "extra": extra})

    def brpop(self, queue, timeout=5):
        return None


def _task(**overrides):
    base = {"task_id": "w1", "knowledge_id": "k1",
            "docs": [{"doc_id": "1", "name": "A.md", "file_url": "/data/files/A.md"}]}
    base.update(overrides)
    return base


_RESULT = {"status": "SUCCESS", "message": None, "pages": [
    {"title": "总览", "summary": "s", "content": "# 总览", "source_doc_ids": '["1"]',
     "vector_doc_id": "wiki-abc", "status": "SUCCESS", "error_msg": None},
]}


def test_handle_success_updatesStatusAndCallbacks(monkeypatch):
    store = FakeTaskStore()
    worker = WikiWorker(store)
    monkeypatch.setattr("worker.wiki_worker.process_wiki_generate", lambda *a, **k: dict(_RESULT))
    callbacks = []
    worker._callback_java = lambda *a, **k: callbacks.append(a)

    worker._handle(_task())

    statuses = [c["status"] for c in store.calls]
    assert statuses == ["PROCESSING", "SUCCESS"], f"状态应 PROCESSING→SUCCESS，实际 {statuses}"
    assert callbacks == [("w1", "k1", _RESULT)]


def test_handle_failed_result_updatesFailed(monkeypatch):
    store = FakeTaskStore()
    worker = WikiWorker(store)
    result = {"status": "FAILED", "message": "所有页面生成失败", "pages": []}
    monkeypatch.setattr("worker.wiki_worker.process_wiki_generate", lambda *a, **k: result)
    callbacks = []
    worker._callback_java = lambda *a, **k: callbacks.append(a)

    worker._handle(_task())

    statuses = [c["status"] for c in store.calls]
    assert statuses == ["PROCESSING", "FAILED"]
    assert "所有页面生成失败" in store.calls[-1]["message"]
    assert callbacks == [("w1", "k1", result)]


def test_handle_exception_updatesFailedAndCallbacks(monkeypatch):
    store = FakeTaskStore()
    worker = WikiWorker(store)

    def boom(*a, **k):
        raise RuntimeError("文档解析失败")

    monkeypatch.setattr("worker.wiki_worker.process_wiki_generate", boom)
    callbacks = []
    worker._callback_java = lambda *a, **k: callbacks.append(a)

    worker._handle(_task())

    statuses = [c["status"] for c in store.calls]
    assert statuses == ["PROCESSING", "FAILED"]
    assert callbacks[-1][2]["status"] == "FAILED"
    assert "文档解析失败" in callbacks[-1][2]["message"]


# ---- 回调 Java（信任边界：Python 不直连 MySQL，回写走 internal 接口） ----

class FakeHttpClient:
    trust_env = False

    def __init__(self, capture):
        self._capture = capture

    def post(self, url, json=None, headers=None, timeout=None):
        self._capture.update({"url": url, "json": json, "headers": headers})
        return FakeResp()


class FakeResp:
    status_code = 200


def test_callback_java_body_shape(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://java:8123/api")
    captured = {}
    monkeypatch.setattr("worker.wiki_worker.get_shared_client", lambda: FakeHttpClient(captured))

    WikiWorker(FakeTaskStore())._callback_java("w1", "k1", dict(_RESULT))

    assert captured["url"] == "http://java:8123/api/internal/agent/wiki-callback"
    assert captured["headers"]["X-Agent-Token"] == "t"
    body = captured["json"]
    assert body["task_id"] == "w1"
    assert body["knowledge_id"] == "k1"
    assert body["status"] == "SUCCESS"
    # 页面字段为 Java WikiCallbackRequest 约定的 snake_case（@JsonProperty）
    page = body["pages"][0]
    assert page["title"] == "总览"
    assert page["vector_doc_id"] == "wiki-abc"
    assert page["source_doc_ids"] == '["1"]'


def test_callback_java_failure_carries_message(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://java:8123/api")
    captured = {}
    monkeypatch.setattr("worker.wiki_worker.get_shared_client", lambda: FakeHttpClient(captured))

    result = {"status": "FAILED", "message": "所有文档解析失败", "pages": []}
    WikiWorker(FakeTaskStore())._callback_java("w1", "k1", result)

    assert captured["json"]["status"] == "FAILED"
    assert captured["json"]["message"] == "所有文档解析失败"
    assert captured["json"]["pages"] == []


def test_callback_java_no_token_skipped(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "")

    def _no_request():
        raise AssertionError("不应发起请求")

    monkeypatch.setattr("worker.wiki_worker.get_shared_client", _no_request)
    WikiWorker(FakeTaskStore())._callback_java("w1", "k1", dict(_RESULT))
