"""
长任务消费者：从 Redis List 取向量化任务 → 执行 → 更新 Redis 状态 → 回调 Java 回写 MySQL。
与 Java 的 Redis List 队列 / Hash 状态 key 约定一致；lifespan 启动后台线程，退出时优雅停止。
"""
import threading
import time

import httpx

from api.knowledge_api import process_vectorize
from config import settings
from storage.task_store import TaskStore
from utils.logger_util import get_logger, agent_event

logger = get_logger("worker")


class VectorizeWorker:
    """文档向量化 worker：BRPOP 阻塞消费 Redis List 队列"""

    def __init__(self, task_store: TaskStore):
        self.task_store = task_store
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        self._thread = threading.Thread(target=self._run, name="vectorize-worker", daemon=True)
        self._thread.start()
        logger.info("向量化 worker 已启动，队列: %s", settings.task_queue_vectorize)

    def stop(self):
        self._stop.set()
        logger.info("向量化 worker 收到停止信号")

    def _run(self):
        while not self._stop.is_set():
            try:
                task = self.task_store.brpop(settings.task_queue_vectorize, timeout=5)
                if task:
                    self._handle(task)
            except Exception as e:
                # 循环保护：单次异常不拖垮 worker
                logger.error("worker 循环异常: %s", e)
                time.sleep(1)

    def _handle(self, task: dict):
        task_id = task.get("task_id")
        knowledge_id = task.get("knowledge_id")
        doc_id = task.get("doc_id")
        file_url = task.get("file_url", "")
        name = task.get("name", "")
        self.task_store.update_status(task_id, "PROCESSING", finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
        agent_event(logger, "task_started", task_id=task_id, doc_id=doc_id)
        try:
            process_vectorize(knowledge_id, doc_id, file_url, name)
            self.task_store.update_status(task_id, "SUCCESS",
                                          finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
            self._callback_java(doc_id, "SUCCESS", None)
            agent_event(logger, "task_success", task_id=task_id, doc_id=doc_id)
        except Exception as e:
            msg = str(e)[:500]
            logger.error("向量化任务失败: task_id=%s, error=%s", task_id, msg)
            self.task_store.update_status(task_id, "FAILED", message=msg,
                                          finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
            self._callback_java(doc_id, "FAILED", msg)
            agent_event(logger, "task_failed", task_id=task_id, doc_id=doc_id, error=msg)

    def _callback_java(self, doc_id, status: str, error_msg: str):
        """回写 Java：更新 MySQL 文档向量化状态（Python 不直连 MySQL）"""
        if not settings.java_token:
            logger.warning("JAVA_TOKEN 未配置，任务结果无法回写 Java")
            return
        url = f"{settings.java_base_url.rstrip('/')}/internal/agent/vector-callback"
        body = {"doc_id": str(doc_id), "status": status}
        if error_msg:
            body["error_msg"] = error_msg
        try:
            # trust_env=False：内部回调直连，避开 Windows 系统代理（踩坑见 改错.md）
            resp = httpx.post(url, json=body,
                              headers={"X-Agent-Token": settings.java_token},
                              timeout=5, trust_env=False)
            if resp.status_code != 200:
                logger.warning("任务结果回写失败: status=%s", resp.status_code)
        except Exception as e:
            logger.warning("任务结果回写异常: %s", e)
