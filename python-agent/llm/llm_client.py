"""
LLM 统一封装：DeepSeek（OpenAI 兼容接口）。
生产强制要求 DEEPSEEK_API_KEY；仅开发可设 ALLOW_MOCK_LLM=true 用 MockLLM 兜底。
"""
from typing import List

from langchain_openai import ChatOpenAI

from config import settings
from utils.logger_util import get_logger, agent_event
from utils.exceptions import LLMError

logger = get_logger("llm")

DEEPSEEK_BASE_URL = "https://api.deepseek.com"


class LLMClient:
    """LLM 客户端：chat(非流式) / stream(流式 token)"""

    def __init__(self):
        self.api_key = settings.deepseek_api_key
        self.model = settings.llm_model
        use_mock = (not self.api_key) and settings.allow_mock_llm
        if not self.api_key and not use_mock:
            raise RuntimeError("DEEPSEEK_API_KEY 未配置且未开启 ALLOW_MOCK_LLM，拒绝启动")
        self._mock = MockLLM() if use_mock else None
        self._llm = None
        if self.api_key:
            self._llm = ChatOpenAI(
                base_url=DEEPSEEK_BASE_URL,
                api_key=self.api_key,
                model=self.model,
                temperature=0.3,
                max_tokens=1024,
                timeout=60,
                max_retries=2,  # DeepSeek 偶发失败自动重试
            )
            logger.info("DeepSeek LLM 已就绪: model=%s", self.model)
        else:
            logger.warning("MockLLM 模式（仅开发调试，生产禁止）")

    def chat(self, messages: List[dict], temperature: float = 0.3) -> str:
        """非流式对话。messages: [{"role": "system|user|assistant", "content": str}]"""
        if self._mock is not None:
            return self._mock.chat(messages)
        try:
            resp = self._llm.invoke(messages, temperature=temperature)
            content = resp.content
            return content if isinstance(content, str) else str(content)
        except Exception as e:
            agent_event(logger, "llm_error", error=str(e))
            raise LLMError(f"大模型调用失败: {e}") from e

    def stream(self, messages: List[dict], temperature: float = 0.3):
        """流式对话，yield 文本 token"""
        if self._mock is not None:
            yield from self._mock.stream(messages)
            return
        try:
            for chunk in self._llm.stream(messages, temperature=temperature):
                content = chunk.content
                if content:
                    yield content if isinstance(content, str) else str(content)
        except Exception as e:
            agent_event(logger, "llm_stream_error", error=str(e))
            raise LLMError(f"大模型流式调用失败: {e}") from e


class MockLLM:
    """无 API Key 时的兜底实现：
    - 路由/反思节点：识别 system prompt 返回关键词，保证无 key 也能走完整 Agent 链路
    - 回答节点：规则回显
    """

    GREETINGS = ("你好", "您好", "hi", "hello", "嗨", "在吗")

    def chat(self, messages: List[dict]) -> str:
        system = messages[0]["content"] if messages else ""
        if "路由判断器" in system:
            return self._guess_route(messages)
        if "反思校验器" in system:
            return "yes"
        return self._echo(messages)

    def stream(self, messages: List[dict]):
        text = self.chat(messages)
        for token in text.split(" "):
            yield token + " "

    def _guess_route(self, messages) -> str:
        query = self._last_user(messages).lower()
        return "chitchat" if any(g in query for g in self.GREETINGS) else "kb"

    def _echo(self, messages) -> str:
        query = self._last_user(messages)
        return f"[MockLLM] 收到问题：{query[:80]}"

    @staticmethod
    def _last_user(messages: List[dict]) -> str:
        for m in reversed(messages):
            if m.get("role") == "user":
                return m.get("content", "")
        return ""
