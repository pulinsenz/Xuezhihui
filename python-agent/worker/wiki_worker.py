"""
AI Wiki 长任务消费者：从 Redis List 取 Wiki 生成任务 → 执行 → 更新 Redis 状态 → 回调 Java 回写 MySQL。
与 VectorizeWorker 同模式（BRPOP 阻塞消费 / Hash 状态 / X-Agent-Token 回调），队列 key 不同。
"""
import json
import threading
import time

from config import settings
from storage.task_store import TaskStore
from utils.java_client import get_shared_client
from utils.logger_util import get_logger, agent_event
from wiki.generator import process_wiki_generate

logger = get_logger("wiki_worker")


class WikiWorker:
    """AI Wiki 生成 worker：BRPOP 阻塞消费 Redis List 队列"""

    def __init__(self, task_store: TaskStore):
        self.task_store = task_store
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        self._thread = threading.Thread(target=self._run, name="wiki-worker", daemon=True)
        self._thread.start()
        logger.info("Wiki worker 已启动，队列: %s", settings.task_queue_wiki)

    def stop(self):
        self._stop.set()
        logger.info("Wiki worker 收到停止信号")

    def _run(self):
        while not self._stop.is_set():
            try:
                task = self.task_store.brpop(settings.task_queue_wiki, timeout=5)
                if task:
                    self._handle(task)
            except Exception as e:
                # 循环保护：单次异常不拖垮 worker
                logger.error("Wiki worker 循环异常: %s", e)
                time.sleep(1)

    def _handle(self, task: dict):
        task_id = task.get("task_id")
        knowledge_id = task.get("knowledge_id")
        docs = task.get("docs", [])
        self.task_store.update_status(task_id, "PROCESSING", finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
        agent_event(logger, "task_started", task_id=task_id, knowledge_id=knowledge_id, docs=len(docs))
        try:
            result = process_wiki_generate(knowledge_id, docs)
            if result["status"] == "SUCCESS":
                self.task_store.update_status(task_id, "SUCCESS",
                                              finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
            else:
                self.task_store.update_status(task_id, "FAILED", message=result.get("message") or "Wiki 生成失败",
                                              finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
            self._callback_java(task_id, knowledge_id, result)
            agent_event(logger, "task_finished", task_id=task_id,
                        status=result["status"], pages=len(result["pages"]))
        except Exception as e:
            msg = str(e)[:500]
            logger.error("Wiki 生成任务失败: task_id=%s, error=%s", task_id, msg)
            self.task_store.update_status(task_id, "FAILED", message=msg,
                                          finish_time=time.strftime("%Y-%m-%dT%H:%M:%S"))
            self._callback_java(task_id, knowledge_id,
                                {"status": "FAILED", "message": msg, "pages": []})

    def _callback_java(self, task_id, knowledge_id, result: dict):
        """回写 Java：SUCCESS 整体替换 wiki_page，FAILED 旧 Wiki 保留（Python 不直连 MySQL）"""
        if not settings.java_token:
            logger.warning("JAVA_TOKEN 未配置，Wiki 任务结果无法回写 Java")
            return
        url = f"{settings.java_base_url.rstrip('/')}/internal/agent/wiki-callback"
        body = {
            "task_id": str(task_id),
            "knowledge_id": str(knowledge_id),
            "status": result["status"],
            "pages": [
                {
                    "title": p.get("title", ""),
                    "summary": p.get("summary", ""),
                    "content": p.get("content", ""),
                    "source_doc_ids": p.get("source_doc_ids", "[]"),
                    "vector_doc_id": p.get("vector_doc_id"),
                    "status": p.get("status", "FAILED"),
                    "error_msg": p.get("error_msg"),
                }
                for p in result.get("pages", [])
            ],
        }
        if result.get("message"):
            body["message"] = result["message"]
        try:
            # trust_env=False：内部回调直连，避开 Windows 系统代理（踩坑见 改错.md）；复用共享连接池
            resp = get_shared_client().post(url, json=body,
                                            headers={"X-Agent-Token": settings.java_token}, timeout=10)
            if resp.status_code != 200:
                logger.warning("Wiki 任务结果回写失败: status=%s", resp.status_code)
        except Exception as e:
            logger.warning("Wiki 任务结果回写异常: %s", e)
