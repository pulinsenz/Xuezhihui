"""
Redis 会话记忆：读写最近 N 轮对话，窗口裁剪防止上下文膨胀。
"""
import json
import os

import redis

from utils.logger_util import get_logger, agent_event

logger = get_logger("redis")

PREFIX = "xzh:session:"


class RedisStore:
    """基于 Redis String(JSON) 的会话记忆，key: xzh:session:{session_id}"""

    def __init__(self, host: str = None, port: int = None, max_messages: int = 10):
        self.max_messages = max_messages
        self.client = redis.Redis(
            host=host or os.getenv("REDIS_HOST", "localhost"),
            port=port or int(os.getenv("REDIS_PORT", "6379")),
            db=0,
            decode_responses=True,
            # 强制 RESP2：redis-py 8.x 默认 RESP3 会发 HELLO 命令，旧版 Redis 不支持
            protocol=2,
        )

    def get_history(self, session_id: str) -> list:
        raw = self.client.get(PREFIX + session_id)
        return json.loads(raw) if raw else []

    def append_message(self, session_id: str, role: str, content: str) -> list:
        history = self.get_history(session_id)
        history.append({"role": role, "content": content})
        # 窗口裁剪：只保留最近 max_messages 条，防止上下文无限膨胀
        history = history[-self.max_messages:]
        self.client.set(PREFIX + session_id, json.dumps(history, ensure_ascii=False), ex=3600 * 24)
        return history

    def clear(self, session_id: str):
        self.client.delete(PREFIX + session_id)
        agent_event(logger, "session_cleared", session_id=session_id)
