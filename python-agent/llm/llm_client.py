"""
LLM 统一封装：OpenAI 兼容接口（默认 DeepSeek；普通用户走 USER_API_KEY 的独立 LLM）。
生产强制要求 API Key；仅开发可设 ALLOW_MOCK_LLM=true 用 MockLLM 兜底。
"""
from typing import List

from langchain_openai import ChatOpenAI

from config import DEEPSEEK_BASE_URL, settings
from utils.logger_util import get_logger, agent_event
from utils.exceptions import LLMError

logger = get_logger("llm")


class LLMClient:
    """LLM 客户端：chat(非流式) / stream(流式 token)
    支持按 key/model/base_url 创建多实例：缺省用 DEEPSEEK_API_KEY（管理员），
    也可用 USER_API_KEY 建普通用户实例。
    """

    def __init__(self, api_key=None, model=None, base_url=None, label="DeepSeek",
                 temperature: float = 0.3, max_tokens: int = 1024):
        self.api_key = api_key if api_key is not None else settings.deepseek_api_key
        self.model = model if model is not None else settings.llm_model
        self.base_url = base_url or DEEPSEEK_BASE_URL
        use_mock = (not self.api_key) and settings.allow_mock_llm
        if not self.api_key and not use_mock:
            raise RuntimeError(f"{label} 的 API Key 未配置且未开启 ALLOW_MOCK_LLM，拒绝启动")
        self._mock = MockLLM() if use_mock else None
        self._llm = None
        if self.api_key:
            self._llm = ChatOpenAI(
                base_url=self.base_url,
                api_key=self.api_key,
                model=self.model,
                temperature=temperature,
                max_tokens=max_tokens,
                timeout=60,
                max_retries=2,  # 上游偶发失败自动重试
            )
            logger.info("%s LLM 已就绪: model=%s, base_url=%s", label, self.model, self.base_url)
        else:
            logger.warning("%s 无 API Key，MockLLM 模式（仅开发调试，生产禁止）", label)

    def chat(self, messages: List[dict], temperature: float = 0.3, max_tokens: int = None) -> str:
        """非流式对话。messages: [{"role": "system|user|assistant", "content": str}]
        max_tokens 可选：单次调用放宽输出上限（Wiki 整页 Markdown 等长文场景），
        不传用实例默认值。"""
        if self._mock is not None:
            return self._mock.chat(messages)
        try:
            kwargs = {"temperature": temperature}
            if max_tokens:
                kwargs["max_tokens"] = max_tokens
            resp = self._llm.invoke(messages, **kwargs)
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
        if "Wiki 大纲规划器" in system:
            return self._wiki_outline(messages)
        if "Wiki 页面撰写器" in system:
            return self._wiki_page(messages)
        return self._echo(messages)

    def stream(self, messages: List[dict]):
        text = self.chat(messages)
        for token in text.split(" "):
            yield token + " "

    def _guess_route(self, messages) -> str:
        query = self._last_user(messages).lower()
        if any(g in query for g in self.GREETINGS):
            return "chitchat"
        # 业务数据：询问「我的知识库/文档/统计」概况
        if "我的" in query and any(k in query for k in ("几个", "多少", "统计", "上传", "文档", "知识库", "状态")):
            return "business"
        return "kb"

    def _echo(self, messages) -> str:
        query = self._last_user(messages)
        return f"[MockLLM] 收到问题：{query[:80]}"

    @staticmethod
    def _last_user(messages: List[dict]) -> str:
        for m in reversed(messages):
            if m.get("role") == "user":
                return m.get("content", "")
        return ""

    @staticmethod
    def _wiki_outline(messages) -> str:
        """Wiki 大纲兜底：从用户消息的文档清单行解析出逐文档页面，保证无 key 也能跑通全链路"""
        import json
        import re
        user = MockLLM._last_user(messages)
        # 文档清单行格式（见 wiki/generator.py）：- [doc_id] 文件名：预览
        docs = re.findall(r"- \[([^\]]+)\] ([^：\n]+)", user)
        outline = [
            {"title": (name or f"文档 {i + 1}").strip()[:60],
             "summary": f"整理「{name.strip()}」的核心知识点",
             "sources": [doc_id]}
            for i, (doc_id, name) in enumerate(docs)
        ] or [{"title": "知识库总览", "summary": "知识库内容总览", "sources": []}]
        return json.dumps(outline, ensure_ascii=False)

    @staticmethod
    def _wiki_page(messages) -> str:
        """Wiki 页面兜底：回显标题 + 提示内容为模拟生成"""
        import re
        user = MockLLM._last_user(messages)
        m = re.search(r"页面标题[：:](.+)", user)
        title = m.group(1).strip() if m else "未命名页面"
        return f"# {title}\n\n（MockLLM 模拟页面：本地开发无 API Key 时的兜底内容）\n\n- 要点一\n- 要点二\n\n## 来源\n\n- 模拟文档"
