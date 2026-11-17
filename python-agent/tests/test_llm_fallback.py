"""FallbackLLM 测试：LLM 主通道（网关）失败自动切备用（官方 DeepSeek）。
背景：生产网关账号池耗尽曾导致普通用户对话与 Wiki 同时瘫痪（见 mianshi/面试2.md 第 8 节）。"""
import pytest

from agent import runtime
from config import settings
from llm.llm_client import FallbackLLM

GW_ERROR = "Error code: 503 - No available accounts"


class FakeLLM:
    """可编程假 LLM：fail=True 时模拟网关 503，并记录调用次数供断言"""

    def __init__(self, name, fail=False, tokens=("t1", "t2")):
        self.name = name
        self.fail = fail
        self.tokens = tokens
        self.chat_calls = 0
        self.stream_calls = 0

    def chat(self, messages, temperature=0.3, max_tokens=None):
        self.chat_calls += 1
        if self.fail:
            raise RuntimeError(GW_ERROR)
        return f"{self.name}:{messages[-1]['content']}"

    def stream(self, messages, temperature=0.3):
        self.stream_calls += 1
        if self.fail:
            raise RuntimeError(GW_ERROR)
        yield from (f"{self.name}-{t}" for t in self.tokens)


# ---------- chat：非流式 ----------

def test_chat_primary_ok_no_fallback():
    primary, fallback = FakeLLM("primary"), FakeLLM("official")
    out = FallbackLLM(primary, fallback).chat([{"role": "user", "content": "hi"}])
    assert out == "primary:hi"
    assert fallback.chat_calls == 0


def test_chat_primary_fail_falls_back():
    primary, fallback = FakeLLM("gateway", fail=True), FakeLLM("official")
    out = FallbackLLM(primary, fallback).chat([{"role": "user", "content": "hi"}])
    assert out == "official:hi"
    assert primary.chat_calls == 1  # 主通道确实尝试过，不是直接跳过


def test_chat_both_fail_raises_fallback_error():
    primary, fallback = FakeLLM("gateway", fail=True), FakeLLM("official", fail=True)
    with pytest.raises(RuntimeError, match=GW_ERROR):
        FallbackLLM(primary, fallback).chat([{"role": "user", "content": "hi"}])


# ---------- stream：流式 ----------

def test_stream_primary_ok_no_fallback():
    primary, fallback = FakeLLM("primary"), FakeLLM("official")
    out = list(FallbackLLM(primary, fallback).stream([{"role": "user", "content": "hi"}]))
    assert out == ["primary-t1", "primary-t2"]
    assert fallback.stream_calls == 0


def test_stream_fail_before_first_token_falls_back():
    """SSE 场景：请求级失败（连接/5xx）发生在首个 token 前，可无痕转移"""
    primary, fallback = FakeLLM("gateway", fail=True), FakeLLM("official")
    out = list(FallbackLLM(primary, fallback).stream([{"role": "user", "content": "hi"}]))
    assert out == ["official-t1", "official-t2"]


def test_stream_empty_primary_ends_without_fallback():
    """主通道正常但输出为空：按正常结束处理，不触发转移"""
    primary, fallback = FakeLLM("primary", tokens=()), FakeLLM("official")
    out = list(FallbackLLM(primary, fallback).stream([{"role": "user", "content": "hi"}]))
    assert out == []
    assert fallback.stream_calls == 0


def test_stream_both_fail_raises():
    primary, fallback = FakeLLM("gateway", fail=True), FakeLLM("official", fail=True)
    with pytest.raises(RuntimeError, match=GW_ERROR):
        list(FallbackLLM(primary, fallback).stream([{"role": "user", "content": "hi"}]))


def test_stream_empty_fallback_ends_normally():
    primary = FakeLLM("gateway", fail=True)
    fallback = FakeLLM("official", tokens=())
    assert list(FallbackLLM(primary, fallback).stream([])) == []
    assert fallback.stream_calls == 1


def test_stream_failure_after_first_token_does_not_replay():
    class InterruptedLLM:
        def stream(self, messages, temperature=0.3):
            yield "partial"
            raise RuntimeError("stream interrupted")

    fallback = FakeLLM("official")
    stream = FallbackLLM(InterruptedLLM(), fallback).stream([])
    assert next(stream) == "partial"
    with pytest.raises(RuntimeError, match="stream interrupted"):
        next(stream)
    assert fallback.stream_calls == 0


# ---------- runtime 接线：同源不包、异源包 ----------

def test_build_user_llm_wraps_fallback_when_gateway(monkeypatch):
    """网关地址与官方不同源 → user_llm 应为 FallbackLLM，fallback 指向官方实例"""
    monkeypatch.setattr(settings, "user_api_key", "test-key")
    monkeypatch.setattr(settings, "user_llm_model", "test-model")
    monkeypatch.setattr(settings, "user_llm_base_url", "http://gateway:8080/v1")
    default = FakeLLM("official")
    wrapped = runtime._build_user_llm(default)
    assert isinstance(wrapped, FallbackLLM)
    assert wrapped.fallback is default


def test_build_user_llm_no_wrap_when_same_base(monkeypatch):
    """USER_LLM_BASE_URL 已指向官方（如生产故障期切换）：不包转移层，避免同 key 重复计费"""
    monkeypatch.setattr(settings, "user_api_key", "test-key")
    monkeypatch.setattr(settings, "user_llm_model", "deepseek-chat")
    monkeypatch.setattr(settings, "user_llm_base_url", "https://api.deepseek.com")
    default = FakeLLM("official")
    llm = runtime._build_user_llm(default)
    assert not isinstance(llm, FallbackLLM)
