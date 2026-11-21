"""答案缓存测试：精确/语义命中、写入侧安全过滤、失效、降级、chat_api 集成"""
import json

import pytest

from storage.answer_cache import AnswerCache, _normalize_query
from api import chat_api


# ---------- 测试替身 ----------

class FakeRedis:
    """最小 Redis 替身：String / ZSET / Hash / pipeline，无 TTL 过期模拟"""

    def __init__(self):
        self.strings = {}
        self.zsets = {}
        self.hashes = {}
        self.fail = False  # 置 True 模拟 Redis 宕机

    def _check(self):
        if self.fail:
            raise ConnectionError("redis down")

    def get(self, key):
        self._check()
        return self.strings.get(key)

    def set(self, key, value, ex=None):
        self._check()
        self.strings[key] = value

    def delete(self, *keys):
        self._check()
        n = 0
        for k in keys:
            if k in self.strings:
                del self.strings[k]
                n += 1
            if k in self.zsets:
                del self.zsets[k]
                n += 1
        return n

    def expire(self, key, ttl):
        self._check()
        return True

    def zadd(self, key, mapping):
        self._check()
        self.zsets.setdefault(key, {}).update(mapping)

    def zrange(self, key, start, end, desc=False):
        self._check()
        vals = [m for m, _ in sorted(self.zsets.get(key, {}).items(), key=lambda kv: kv[1])]
        if end == -1:
            return vals[start:]
        return vals[start:end + 1]

    def zrem(self, key, member):
        self._check()
        return 1 if self.zsets.get(key, {}).pop(member, None) is not None else 0

    def zremrangebyrank(self, key, start, end):
        self._check()
        members = sorted(self.zsets.get(key, {}).items(), key=lambda kv: kv[1])
        vals = [m for m, _ in members]
        e = len(vals) + end + 1 if end < 0 else end + 1
        removed = vals[start:e]
        for m in removed:
            self.zsets[key].pop(m, None)
        return len(removed)

    def hincrby(self, key, field, delta=1):
        self._check()
        h = self.hashes.setdefault(key, {})
        h[field] = h.get(field, 0) + delta
        return h[field]

    def hgetall(self, key):
        self._check()
        return dict(self.hashes.get(key, {}))

    def pipeline(self):
        return FakePipeline(self)


class FakePipeline:
    def __init__(self, client):
        self.client = client
        self.ops = []

    def set(self, k, v, ex=None):
        self.ops.append(("set", k, v))
        return self

    def zadd(self, key, mapping):
        self.ops.append(("zadd", key, mapping))
        return self

    def zremrangebyrank(self, key, s, e):
        self.ops.append(("zremrangebyrank", key, s, e))
        return self

    def expire(self, key, ttl):
        self.ops.append(("expire", key, ttl))
        return self

    def delete(self, *keys):
        self.ops.append(("delete", keys))
        return self

    def execute(self):
        for op in self.ops:
            if op[0] == "set":
                self.client.set(op[1], op[2])
            elif op[0] == "zadd":
                self.client.zadd(op[1], op[2])
            elif op[0] == "zremrangebyrank":
                self.client.zremrangebyrank(op[1], op[2], op[3])
            elif op[0] == "expire":
                self.client.expire(op[1], op[2])
            elif op[0] == "delete":
                self.client.delete(*op[1])
        return []


class FakeEmbed:
    """可控向量替身：按文本查表，缺省 [1,0]；记录调用次数"""

    def __init__(self):
        self.vectors = {}
        self.calls = 0

    def __call__(self, text):
        self.calls += 1
        return list(self.vectors.get(text, [1.0, 0.0]))


def _cache(redis=None, embed=None, **kw):
    return AnswerCache(redis or FakeRedis(), embed_fn=embed or FakeEmbed(), **kw)


# ---------- AnswerCache 单元 ----------

def test_store_and_exact_hit():
    r, c = FakeRedis(), None
    c = _cache(r)
    c.store("kb1", "什么是数据结构", "答案A", [{"text": "s1"}], "kb", "knowledge")
    hit = c.lookup("kb1", "什么是数据结构")
    assert hit and hit["match"] == "exact"
    assert hit["answer"] == "答案A"
    assert hit["route"] == "kb"


def test_normalize_query_still_hits():
    """归一化命中：压缩连续空白、去首尾——多打空格/回车不影响精确命中"""
    c = _cache()
    c.store(None, "hello  world", "答案A", [], "kb", "knowledge")
    hit = c.lookup(None, "  hello world \n")
    assert hit and hit["match"] == "exact"


def test_semantic_hit_above_threshold():
    embed = FakeEmbed()
    embed.vectors = {"什么是数据结构": [1.0, 0.0], "数据结构是什么": [0.99, 0.141]}
    c = _cache(embed=embed)
    c.store("kb1", "什么是数据结构", "答案A", [], "kb", "knowledge")
    hit = c.lookup("kb1", "数据结构是什么")
    assert hit and hit["match"] == "semantic"
    assert hit["score"] >= 0.95


def test_no_hit_below_threshold():
    embed = FakeEmbed()
    embed.vectors = {"什么是数据结构": [1.0, 0.0], "今天天气怎么样": [-1.0, 0.0]}
    c = _cache(embed=embed)
    c.store("kb1", "什么是数据结构", "答案A", [], "kb", "knowledge")
    assert c.lookup("kb1", "今天天气怎么样") is None


def test_business_intent_not_cached():
    """business 路由拉取该用户业务数据，跨用户共享会泄露——绝不入库"""
    c = _cache()
    c.store("kb1", "我有几个知识库", "3 个", [], "business", "business")
    c.store("kb1", "上一条依据是什么", "引用X", [], "kb", "provenance")
    c.store("kb1", "简短一点", " shortened", [], "kb", "transform")
    assert c.lookup("kb1", "我有几个知识库") is None
    assert c.lookup("kb1", "上一条依据是什么") is None
    assert c.lookup("kb1", "简短一点") is None


def test_empty_answer_not_cached():
    c = _cache()
    c.store("kb1", "问题", "  ", [], "kb", "knowledge")
    assert c.lookup("kb1", "问题") is None


def test_disabled_cache_noop():
    c = AnswerCache(FakeRedis(), enabled=False, embed_fn=FakeEmbed())
    c.store("kb1", "问题", "答案", [], "kb", "knowledge")
    assert c.lookup("kb1", "问题") is None


def test_invalidate_rotates_exact_and_clears_semantic():
    embed = FakeEmbed()
    c = _cache(embed=embed)
    c.store("kb1", "问题A", "旧答案", [], "kb", "knowledge")
    assert c.lookup("kb1", "问题A") is not None
    c.invalidate("kb1")
    # 精确：版本轮换后同问题不命中；语义：条目已删
    assert c.lookup("kb1", "问题A") is None
    # 失效后重新写入用新版本，可正常命中
    c.store("kb1", "问题A", "新答案", [], "kb", "knowledge")
    hit = c.lookup("kb1", "问题A")
    assert hit and hit["answer"] == "新答案"


def test_invalidate_scoped_by_domain():
    """失效只影响该知识库域，别的库/无知识库域不受牵连"""
    c = _cache()
    c.store("kb1", "问题A", "答案A", [], "kb", "knowledge")
    c.store("kb2", "问题B", "答案B", [], "kb", "knowledge")
    c.invalidate("kb1")
    assert c.lookup("kb1", "问题A") is None
    assert c.lookup("kb2", "问题B") is not None


def test_redis_down_lookup_returns_none():
    r = FakeRedis()
    c = _cache(r)
    c.store("kb1", "问题", "答案", [], "kb", "knowledge")
    r.fail = True
    assert c.lookup("kb1", "问题") is None  # 降级为未命中，不抛异常


def test_redis_down_store_silent():
    r = FakeRedis()
    r.fail = True
    c = _cache(r)
    c.store("kb1", "问题", "答案", [], "kb", "knowledge")  # 不抛异常
    assert c.lookup("kb1", "问题") is None


def test_stats_counts():
    r = FakeRedis()
    embed = FakeEmbed()
    # 显式给两个问题配可区分的向量（缺省向量会让不同问题互相语义命中）
    embed.vectors = {"问题": [1.0, 0.0], "没问过的问题XYZ": [-1.0, 0.0]}
    c = _cache(r, embed)
    c.store("kb1", "问题", "答案", [], "kb", "knowledge")
    c.lookup("kb1", "问题")
    c.lookup("kb1", "没问过的问题XYZ")
    stats = c.stats()
    assert stats["write"] == 1
    assert stats["hit_exact"] == 1
    assert stats["miss"] == 1


def test_max_entries_lru_trim():
    c = _cache(max_entries=3, ttl_hours=1)
    for i in range(5):
        embed = FakeEmbed()
        embed.vectors[_normalize_query(f"问题{i}")] = [0.1 * i, 1.0]
        c.embed_fn = embed
        c.store("kb1", f"问题{i}", f"答案{i}", [], "kb", "knowledge")
    domain_idx = c.client.zsets
    idx_key = [k for k in domain_idx if "semidx" in k][0]
    assert len(domain_idx[idx_key]) <= 3


# ---------- chat_api 集成 ----------

class FakeStoreWithHistory:
    def __init__(self, history=None):
        self._history = history or []
        self.calls = []

    def get_history(self, session_id):
        return self._history

    def append_message(self, session_id, role, content):
        self.calls.append((role, content))


class FakeGraph:
    def __init__(self, result):
        self.result = result
        self.invoked = 0
        self.streamed = 0

    def invoke(self, state):
        self.invoked += 1
        return dict(self.result)

    async def astream(self, state, stream_mode=None):
        self.streamed += 1
        yield ("updates", {"router": {"route": self.result.get("route", "kb"),
                                       "intent_plan": self.result.get("intent_plan", {})}})
        yield ("updates", {"answer": {"answer": self.result.get("answer", "答案"),
                                      "sources": self.result.get("sources", [])}})


def _setup(monkeypatch, result=None, history=None):
    from fastapi.testclient import TestClient
    import main as app_main

    result = result or {"answer": "基于知识库的回答", "route": "kb",
                        "sources": [{"text": "片段"}], "intent_plan": {"intent": "knowledge"}}
    fake_cache = AnswerCache(FakeRedis(), embed_fn=FakeEmbed())
    graph = FakeGraph(result)
    monkeypatch.setattr(chat_api.runtime, "redis_store", FakeStoreWithHistory(history))
    monkeypatch.setattr(chat_api.runtime, "answer_cache", fake_cache)
    monkeypatch.setattr(chat_api.runtime, "llm", object())
    monkeypatch.setattr(chat_api, "persist_chat", lambda *a, **k: None)
    monkeypatch.setattr(chat_api, "get_graph", lambda: graph)
    monkeypatch.setattr(app_main.settings, "agent_token", "t")
    return TestClient(app_main.app), fake_cache, graph


def test_chat_first_question_writes_cache(monkeypatch):
    client, cache, graph = _setup(monkeypatch)
    resp = client.post("/api/agent/chat", json={"session_id": "s1", "query": "什么是数据结构",
                                                "knowledge_id": "kb1"},
                       headers={"X-Agent-Token": "t"})
    assert resp.status_code == 200
    assert resp.json()["data"]["answer"] == "基于知识库的回答"
    assert graph.invoked == 1
    # 首问结果已入缓存
    assert cache.lookup("kb1", "什么是数据结构") is not None


def test_chat_second_session_hits_exact(monkeypatch):
    """新会话同问题命中精确缓存，不再调 graph（省检索+反思+回答全链路）"""
    client, cache, graph = _setup(monkeypatch)
    client.post("/api/agent/chat", json={"session_id": "s1", "query": "什么是数据结构",
                                         "knowledge_id": "kb1"},
                headers={"X-Agent-Token": "t"})
    resp2 = client.post("/api/agent/chat", json={"session_id": "s2", "query": "什么是数据结构",
                                                 "knowledge_id": "kb1"},
                        headers={"X-Agent-Token": "t"})
    assert resp2.json()["data"]["answer"] == "基于知识库的回答"
    assert graph.invoked == 1  # 第二问走缓存


def test_chat_business_not_stored(monkeypatch):
    """business 回答（拉取该用户业务数据）不入缓存：换会话再问必须重新走 graph"""
    result = {"answer": "你有 3 个知识库", "route": "business", "sources": [],
              "intent_plan": {"intent": "business"}}
    client, cache, graph = _setup(monkeypatch, result=result)
    client.post("/api/agent/chat", json={"session_id": "s1", "query": "我有几个知识库"},
                headers={"X-Agent-Token": "t"})
    assert graph.invoked == 1
    client.post("/api/agent/chat", json={"session_id": "s2", "query": "我有几个知识库"},
                headers={"X-Agent-Token": "t"})
    assert graph.invoked == 2  # 未命中缓存，重新执行


def test_chat_with_history_skips_cache(monkeypatch):
    """有会话历史（追问/改写）不走缓存：答案依赖历史，缓存会答错"""
    client, cache, graph = _setup(monkeypatch, history=[{"role": "user", "content": "之前的问题"}])
    client.post("/api/agent/chat", json={"session_id": "s1", "query": "展开第二点"},
                headers={"X-Agent-Token": "t"})
    assert graph.invoked == 1
    assert cache.lookup("kb1", "展开第二点") is None  # 有历史不写入


def test_stream_cache_hit_replays_sse(monkeypatch):
    """流式命中缓存：thinking + 分块 token + done(cached:true)，graph 不执行"""
    client, cache, graph = _setup(monkeypatch)
    cache.store("kb1", "什么是数据结构", "缓存命中答案", [{"text": "来源"}], "kb", "knowledge")
    resp = client.post("/api/agent/stream",
                       json={"session_id": "s1", "query": "什么是数据结构", "knowledge_id": "kb1"},
                       headers={"X-Agent-Token": "t"})
    assert resp.status_code == 200
    assert "命中答案缓存" in resp.text
    assert '"type":"token"' in resp.text.replace(" ", "") or '"type": "token"' in resp.text
    assert '"cached"' in resp.text
    assert graph.streamed == 0  # 命中不执行图
    # 会话记忆照常保存（缓存命中也进历史）
    assert ("assistant", "缓存命中答案") in chat_api.runtime.redis_store.calls


def test_stream_miss_stores(monkeypatch):
    """流式未命中：正常走图，完成后写入缓存"""
    client, cache, graph = _setup(monkeypatch)
    resp = client.post("/api/agent/stream",
                       json={"session_id": "s1", "query": "什么是数据结构", "knowledge_id": "kb1"},
                       headers={"X-Agent-Token": "t"})
    assert resp.status_code == 200
    assert '"type":"done"' in resp.text.replace(" ", "") or '"type": "done"' in resp.text
    assert '"cached"' not in resp.text
    assert graph.streamed == 1
    assert cache.lookup("kb1", "什么是数据结构") is not None


def test_vectorize_invalidates_cache(monkeypatch):
    """知识库入库后该域缓存失效（防过时答案）"""
    from api import knowledge_api

    client, cache, graph = _setup(monkeypatch)
    cache.store("kb1", "什么是数据结构", "旧答案", [], "kb", "knowledge")
    assert cache.lookup("kb1", "什么是数据结构") is not None

    monkeypatch.setattr(knowledge_api.runtime, "retriever",
                        type("R", (), {"add_document": lambda self, k, d, t: 3})())
    monkeypatch.setattr(knowledge_api.runtime, "answer_cache", cache)
    monkeypatch.setattr(knowledge_api, "_load_file", lambda url, name: "内容")
    resp = client.post("/api/knowledge/vectorize",
                       json={"knowledge_id": "kb1", "doc_id": "d1", "file_url": "x", "name": "a.md"},
                       headers={"X-Agent-Token": "t"})
    assert resp.status_code == 200
    assert cache.lookup("kb1", "什么是数据结构") is None  # 已失效
