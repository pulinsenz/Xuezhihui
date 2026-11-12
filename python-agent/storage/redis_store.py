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

    def __init__(self, host: str = None, port: int = None, password: str = None, max_messages: int = 10):
        self.max_messages = max_messages
        self.client = redis.Redis(
            host=host or os.getenv("REDIS_HOST", "localhost"),
            port=port or int(os.getenv("REDIS_PORT", "6379")),
            # 显式传参优先；缺省回退 env（生产 REDIS_PASSWORD 与 Java/redis 服务同源）
            password=password or os.getenv("REDIS_PASSWORD", "") or None,
            db=0,
            decode_responses=True,
            # 强制 RESP2：redis-py 8.x 默认 RESP3 会发 HELLO 命令，旧版 Redis 不支持
            protocol=2,
        )

    def get_history(self, session_id: str) -> list:
        raw = self.client.get(PREFIX + session_id)
        return json.loads(raw) if raw else []

    # Lua 原子完成「读历史 → 追加 → 窗口裁剪 → 写回 + TTL」：
    # 此前的 get→append→set 三步在并发回答同一会话时互相覆盖，会丢对话轮次。
    # 注意 Redis Lua 的 cjson.encode({}) 会输出 '{}'（对象）而非 '[]'，
    # 但 out 至少含刚追加的 1 条，不会触发该坑。
    _APPEND_SCRIPT = """
local raw = redis.call('GET', KEYS[1])
local arr = {}
if raw then arr = cjson.decode(raw) end
table.insert(arr, cjson.decode(ARGV[1]))
local max_n = tonumber(ARGV[2])
local start = #arr - max_n + 1
if start < 1 then start = 1 end
local out = {}
for i = start, #arr do out[#out + 1] = arr[i] end
local encoded = cjson.encode(out)
redis.call('SET', KEYS[1], encoded, 'EX', ARGV[3])
return encoded
"""

    def append_message(self, session_id: str, role: str, content: str) -> list:
        raw = self.client.eval(
            self._APPEND_SCRIPT, 1, PREFIX + session_id,
            json.dumps({"role": role, "content": content}, ensure_ascii=False),
            self.max_messages, 3600 * 24,
        )
        return json.loads(raw)

    def clear(self, session_id: str):
        self.client.delete(PREFIX + session_id)
        agent_event(logger, "session_cleared", session_id=session_id)
