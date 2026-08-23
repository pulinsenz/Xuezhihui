"""长任务 worker 测试：消费队列执行向量化、状态流转、回调 Java 回写"""
from config import settings

from worker.vectorize_worker import VectorizeWorker


class FakeTaskStore:
    """记录 update_status 调用，不连真实 Redis"""

    def __init__(self):
        self.calls = []

    def update_status(self, task_id, status, message=None, **extra):
        self.calls.append({"task_id": task_id, "status": status, "message": message, "extra": extra})

    def brpop(self, queue, timeout=5):
        return None


def _task(**overrides):
    base = {"task_id": "t1", "knowledge_id": "k1", "doc_id": "d1",
            "file_url": "/data/files/course.txt", "name": "course.txt"}
    base.update(overrides)
    return base


def test_handle_success_updatesStatusAndCallbacks(monkeypatch):
    store = FakeTaskStore()
    worker = VectorizeWorker(store)
    monkeypatch.setattr("worker.vectorize_worker.process_vectorize", lambda *a, **k: 3)
    callbacks = []
    worker._callback_java = lambda doc_id, status, err: callbacks.append((doc_id, status))

    worker._handle(_task())

    statuses = [c["status"] for c in store.calls]
    assert statuses == ["PROCESSING", "SUCCESS"], f"状态应 PROCESSING→SUCCESS，实际 {statuses}"
    assert callbacks == [("d1", "SUCCESS")], "成功后应回调 Java 回写 MySQL"


def test_handle_failure_updatesFailedAndCallbacks(monkeypatch):
    store = FakeTaskStore()
    worker = VectorizeWorker(store)

    def boom(*a, **k):
        raise RuntimeError("解析失败")

    monkeypatch.setattr("worker.vectorize_worker.process_vectorize", boom)
    callbacks = []
    worker._callback_java = lambda doc_id, status, err: callbacks.append((doc_id, status, err))

    worker._handle(_task())

    statuses = [c["status"] for c in store.calls]
    assert statuses == ["PROCESSING", "FAILED"]
    assert callbacks == [("d1", "FAILED", "解析失败")]
    failed = [c for c in store.calls if c["status"] == "FAILED"][0]
    assert "解析失败" in failed["message"]


def test_worker_loop_continues_on_error(monkeypatch):
    """单次任务异常不拖垮 worker 主循环：异常被捕获，循环继续消费"""
    store = FakeTaskStore()
    worker = VectorizeWorker(store)
    monkeypatch.setattr("worker.vectorize_worker.time.sleep", lambda s: None)

    state = {"count": 0}

    def brpop(queue, timeout=5):
        if state["count"] == 0:
            state["count"] += 1
            raise RuntimeError("redis 瞬时错误")
        # 第二次：设置停止信号并返回空，让循环正常退出（验证异常后仍继续消费）
        worker._stop.set()
        return None

    store.brpop = brpop
    worker._run()  # 不应抛异常
    assert state["count"] == 1, "第一次的异常应被捕获，worker 继续消费"


# ---- 回调 Java（信任边界：Python 不直连 MySQL，回写走 internal 接口） ----

def test_callback_java_sends_token(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://java:8123/api")
    captured = {}

    class FakeResp:
        status_code = 200

    def fake_post(url, json=None, headers=None, timeout=None, trust_env=None):
        captured.update({"url": url, "json": json, "headers": headers, "trust_env": trust_env})
        return FakeResp()

    monkeypatch.setattr("worker.vectorize_worker.httpx.post", fake_post)
    VectorizeWorker(FakeTaskStore())._callback_java("d1", "SUCCESS", None)

    assert captured["url"] == "http://java:8123/api/internal/agent/vector-callback"
    assert captured["json"] == {"doc_id": "d1", "status": "SUCCESS"}
    assert captured["headers"]["X-Agent-Token"] == "t"
    assert captured["trust_env"] is False, "内部回调应直连，避开系统代理"


def test_callback_java_with_error_msg(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "t")
    monkeypatch.setattr(settings, "java_base_url", "http://java:8123/api")
    captured = {}

    class FakeResp:
        status_code = 200

    def fake_post(url, json=None, headers=None, timeout=None, trust_env=None):
        captured["json"] = json
        return FakeResp()

    monkeypatch.setattr("worker.vectorize_worker.httpx.post", fake_post)
    VectorizeWorker(FakeTaskStore())._callback_java("d1", "FAILED", "解析失败")
    assert captured["json"] == {"doc_id": "d1", "status": "FAILED", "error_msg": "解析失败"}


def test_callback_java_no_token_skipped(monkeypatch):
    monkeypatch.setattr(settings, "java_token", "")
    monkeypatch.setattr("worker.vectorize_worker.httpx.post", lambda *a, **k: (_ for _ in ()).throw(AssertionError("不应发起请求")))
    VectorizeWorker(FakeTaskStore())._callback_java("d1", "SUCCESS", None)
