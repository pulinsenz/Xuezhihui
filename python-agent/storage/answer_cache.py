"""
LLM 答案缓存：新会话首问的精确匹配 + 语义匹配，重复问题直接返回历史答案，省 token 与延迟。

安全边界（写入侧过滤，读取侧不可知路由）：
- 只缓存 intent ∈ {knowledge, conversation} 且 route ∈ {kb, chitchat, other} 的回答；
  business（工具回调拉取该用户业务数据）与 provenance/transform（依赖会话历史）永不入库。
- 语义命中阈值保守（默认 0.95），命中/写入全量打审计日志。
- 知识库内容变更（向量化入库 / 删向量 / Wiki 回灌）时按域失效，防过时答案：
  语义条目显式删除；精确条目 key 含域版本号，失效即换版本，旧条目等 TTL 自然回收。

降级约定：Redis 或 embedding 任何异常 → 记 warning 日志并按未命中处理，不影响问答主链路。
"""
import base64
import hashlib
import json
import struct
import time
import uuid

from utils.logger_util import get_logger, agent_event

logger = get_logger("answer_cache")

# 缓存中的旧引用缺少切片标识，升级键前缀让它们按原 TTL 淘汰。
EXACT_PREFIX = "xzh:anscache:v2:exact:"
SEM_ENTRY_PREFIX = "xzh:anscache:v2:sem:"
SEM_INDEX_PREFIX = "xzh:anscache:v2:semidx:"
VERSION_PREFIX = "xzh:anscache:ver:"
STATS_KEY = "xzh:anscache:stats"

# 可缓存的回答属性：intent 决定是否依赖会话历史/用户数据（写入侧硬过滤）
CACHABLE_INTENTS = {"knowledge", "conversation"}
CACHABLE_ROUTES = {"kb", "chitchat", "other"}

GLOBAL_DOMAIN = "_global"


def _domain(knowledge_id: str | None) -> str:
    return knowledge_id or GLOBAL_DOMAIN


def _normalize_query(query: str) -> str:
    """查询归一：去首尾、压缩连续空白——同一句话多打几个空格也命中精确缓存"""
    return " ".join(query.split())


def _vec_encode(vec: list[float]) -> str:
    """向量 → base64(float32[])，384 维约 2KB（JSON 数组约 8KB，省 4 倍内存与带宽）"""
    return base64.b64encode(struct.pack(f"<{len(vec)}f", *vec)).decode("ascii")


def _vec_decode(raw: str) -> list[float]:
    blob = base64.b64decode(raw)
    return list(struct.unpack(f"<{len(blob) // 4}f", blob))


def _cosine(a: list[float], b: list[float]) -> float:
    """归一化向量的余弦相似度即点积；维度不一致视为不可比"""
    if len(a) != len(b):
        return 0.0
    return sum(x * y for x, y in zip(a, b))


class AnswerCache:
    """精确（String）+ 语义（String 条目 + ZSET 时间索引，按域隔离）双层答案缓存。

    embed_fn 由调用方注入（runtime.embedding.embed_query），便于测试替换。
    """

    def __init__(self, client, enabled: bool = True, sem_threshold: float = 0.95,
                 ttl_hours: int = 168, max_entries: int = 300, embed_fn=None):
        self.client = client
        self.enabled = enabled
        self.sem_threshold = sem_threshold
        self.ttl_seconds = max(1, ttl_hours) * 3600
        self.max_entries = max(1, max_entries)
        self.embed_fn = embed_fn

    # ---------- 对外接口 ----------

    def lookup(self, knowledge_id: str | None, query: str):
        """返回 {answer, sources, route, match: exact|semantic} 或 None。任何异常按未命中。"""
        if not self.enabled:
            return None
        domain = _domain(knowledge_id)
        try:
            hit = self._lookup_exact(domain, query)
            match = "exact"
            if not hit:
                hit = self._lookup_semantic(domain, query)
                match = "semantic"
            if hit:
                self._bump("hit_" + match)
                agent_event(logger, "answer_cache_hit", match=match, domain=domain,
                            route=hit.get("route", ""), query=_normalize_query(query)[:80])
                hit["match"] = match
                return hit
            self._bump("miss")
            return None
        except Exception as e:
            logger.warning("答案缓存查询失败，按未命中处理: %s", e)
            self._bump("error")
            return None

    def store(self, knowledge_id: str | None, query: str, answer: str,
              sources: list, route: str, intent: str, embed_fn=None):
        """写入精确 + 语义条目。只收可缓存属性（写入侧硬过滤）。任何异常静默忽略。"""
        if not self.enabled:
            return
        if intent not in CACHABLE_INTENTS or route not in CACHABLE_ROUTES:
            agent_event(logger, "answer_cache_skip", reason="uncachable_route",
                        route=route or "", intent=intent or "")
            return
        if not answer or not answer.strip():
            return
        embed = embed_fn or self.embed_fn
        if embed is None:
            return
        domain = _domain(knowledge_id)
        norm = _normalize_query(query)
        now = time.time()
        entry_id = uuid.uuid4().hex
        try:
            vec = _vec_encode(embed(norm))
            entry = {"query": norm, "answer": answer, "sources": sources or [],
                     "route": route, "intent": intent, "ts": now}
            pipe = self.client.pipeline()
            pipe.set(self._exact_key(domain, norm),
                     json.dumps(entry, ensure_ascii=False), ex=self.ttl_seconds)
            pipe.set(SEM_ENTRY_PREFIX + domain + ":" + entry_id,
                     json.dumps({**entry, "vec": vec}, ensure_ascii=False), ex=self.ttl_seconds)
            pipe.zadd(SEM_INDEX_PREFIX + domain, {entry_id: now})
            # LRU 上限：按时间淘汰最旧（条目本身有 TTL，索引同步裁剪）
            pipe.zremrangebyrank(SEM_INDEX_PREFIX + domain, 0, -(self.max_entries + 1))
            pipe.expire(SEM_INDEX_PREFIX + domain, self.ttl_seconds)
            pipe.execute()
            self._bump("write")
            agent_event(logger, "answer_cache_store", domain=domain, route=route,
                        intent=intent, query=norm[:80])
        except Exception as e:
            logger.warning("答案缓存写入失败（忽略）: %s", e)
            self._bump("error")

    def invalidate(self, knowledge_id: str | None):
        """知识库内容变更（入库/删向量/Wiki 回灌）后清该域全部条目：
        语义条目与索引显式删除；精确层换版本号使旧 key 不可达（等 TTL 回收）。"""
        domain = _domain(knowledge_id)
        try:
            idx_key = SEM_INDEX_PREFIX + domain
            entry_ids = self.client.zrange(idx_key, 0, -1)
            pipe = self.client.pipeline()
            for entry_id in entry_ids:
                pipe.delete(SEM_ENTRY_PREFIX + domain + ":" + entry_id)
            pipe.delete(idx_key)
            pipe.set(VERSION_PREFIX + domain, str(int(time.time() * 1000)),
                     ex=self.ttl_seconds)
            pipe.execute()
            agent_event(logger, "answer_cache_invalidate", domain=domain,
                        entries=len(entry_ids))
        except Exception as e:
            logger.warning("答案缓存失效失败（忽略）: %s", e)

    def stats(self) -> dict:
        """命中统计（管理端展示用）。异常返回空字典。"""
        try:
            raw = self.client.hgetall(STATS_KEY)
            return {k: int(v) for k, v in raw.items()}
        except Exception as e:
            logger.warning("答案缓存统计读取失败: %s", e)
            return {}

    # ---------- 内部实现 ----------

    def _exact_key(self, domain: str, norm_query: str) -> str:
        digest = hashlib.sha256(norm_query.encode()).hexdigest()
        return f"{EXACT_PREFIX}{domain}:{self._version(domain)}:{digest}"

    def _version(self, domain: str) -> str:
        try:
            return self.client.get(VERSION_PREFIX + domain) or "v0"
        except Exception:
            return "v0"

    def _lookup_exact(self, domain: str, query: str):
        raw = self.client.get(self._exact_key(domain, _normalize_query(query)))
        return json.loads(raw) if raw else None

    def _lookup_semantic(self, domain: str, query: str):
        if self.embed_fn is None:
            return None
        idx_key = SEM_INDEX_PREFIX + domain
        entry_ids = self.client.zrange(idx_key, 0, -1)
        if not entry_ids:
            return None
        qvec = self.embed_fn(_normalize_query(query))
        best, best_score = None, 0.0
        for entry_id in entry_ids:
            raw = self.client.get(SEM_ENTRY_PREFIX + domain + ":" + entry_id)
            if not raw:
                # 索引里的条目已过期：顺手清索引，防止越积越多
                self.client.zrem(idx_key, entry_id)
                continue
            entry = json.loads(raw)
            score = _cosine(qvec, _vec_decode(entry["vec"]))
            if score > best_score:
                best, best_score = entry, score
        if best is not None and best_score >= self.sem_threshold:
            best["score"] = round(best_score, 4)
            return best
        return None

    def _bump(self, metric: str):
        try:
            self.client.hincrby(STATS_KEY, metric, 1)
            self.client.expire(STATS_KEY, 30 * 86400)
        except Exception:
            pass
