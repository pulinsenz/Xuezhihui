"""
任务存储：长任务消息队列（Redis List）+ 任务状态（Redis Hash）。
Java 提交任务（LPUSH 消息 + 写 Hash 状态），本服务 worker 消费（BRPOP）执行并更新状态，
前端通过 Java /task/{id} 轮询状态。队列与状态共用 Redis，与 Java 端 key 约定一致。
"""
import json
import os

from typing import Optional

import redis

PREFIX_TASK = "xzh:task:"


class TaskStore:
    """基于 Redis 的长任务队列与状态存取（独立连接，BRPOP 阻塞不占用会话连接）"""

    def __init__(self, host: str = None, port: int = None):
        self.client = redis.Redis(
            host=host or os.getenv("REDIS_HOST", "localhost"),
            port=port or int(os.getenv("REDIS_PORT", "6379")),
            db=0,
            decode_responses=True,
            protocol=2,  # 兼容旧版 Redis（RESP2）
        )

    def brpop(self, queue: str, timeout: int = 5) -> Optional[dict]:
        """阻塞取任务，超时返回 None"""
        item = self.client.brpop(queue, timeout=timeout)
        if not item:
            return None
        _, raw = item
        return json.loads(raw)

    def update_status(self, task_id: str, status: str, message: str = None, **extra) -> None:
        """更新任务状态（Redis Hash），message 截断防撑爆。
        注意：本机 Redis 为 3.2（老 Windows 版），HSET 只支持单字段
        （多字段 HSET 是 Redis 4.0+ 才支持，会报 wrong number of arguments），
        因此逐字段写；Java 侧 putAll 走 HMSET 兼容老版本。
        """
        mapping = {"status": status}
        if message is not None:
            mapping["message"] = str(message)[:500]
        if extra:
            mapping.update(extra)
        key = PREFIX_TASK + task_id
        for field, value in mapping.items():
            self.client.hset(key, field, value)

    def get_task(self, task_id: str) -> Optional[dict]:
        return self.client.hgetall(PREFIX_TASK + task_id) or None
