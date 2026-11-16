"""AI Wiki 生成器测试：大纲解析鲁棒性、上下文选取、全流程（MockLLM）、页面级容错"""
import json

import pytest

from wiki import generator
from wiki.generator import (_build_context, _keywords, _parse_outline,
                            WikiGenerationError, process_wiki_generate)


# ---- 大纲解析（鲁棒性：围栏/杂质/非法 sources）----

def test_parse_outline_strips_fences_and_junk():
    raw = '好的，以下是大纲：\n```json\n[{"title":"总览","summary":"s","sources":["1"]}]\n```\n以上。'
    pages = _parse_outline(raw, {"1", "2"}, 10)
    assert len(pages) == 1
    assert pages[0]["title"] == "总览"
    assert pages[0]["sources"] == ["1"]


def test_parse_outline_caps_pages_and_drops_unknown_sources():
    arr = [{"title": f"p{i}", "summary": "s", "sources": ["999"]} for i in range(5)]
    pages = _parse_outline(json.dumps(arr, ensure_ascii=False), {"1"}, 3)
    assert len(pages) == 3, "超出 max_pages 的页面应截断"
    assert pages[0]["sources"] == ["1"], "未知 doc_id 应回退为全部文档"


def test_parse_outline_no_json_raises():
    with pytest.raises(ValueError):
        _parse_outline("这里没有任何 JSON", {"1"}, 10)


def test_parse_outline_empty_pages_raises():
    with pytest.raises(ValueError):
        _parse_outline('[{"summary":"缺标题"}]', {"1"}, 10)


# ---- 上下文选取 ----

def test_build_context_respects_budget_and_source_filter():
    usable = [
        {"doc_id": "1", "name": "A.md", "chunks": ["机器学习 是人工智能的核心分支" + "x" * 40,
                                                    "回归算法 是机器学习的基础" + "y" * 40]},
        {"doc_id": "2", "name": "B.md", "chunks": ["计算机网络 分层架构" + "z" * 200]},
    ]
    entry = {"title": "机器学习回归算法", "summary": "机器学习 回归", "sources": ["1"]}
    ctx = _build_context(entry, usable, budget=120)
    assert all(name == "A.md" for name, _ in ctx), "sources 过滤后不应选入 B.md"
    total = sum(len(c) for _, c in ctx)
    assert total <= 120, f"上下文总长 {total} 应不超 budget=120"


def test_keywords_chinese_and_english():
    kw = _keywords("Transformer 模型的注意力机制 attention")
    assert "attention" in kw
    assert "模" in kw and "型" in kw, "中文按单字提取"
    assert "的" not in kw, "停用词应被剔除"


# ---- 全流程（fake LLM + fake retriever，不连外部服务）----

class FakeWikiLLM:
    """按 system prompt 分流的假 LLM：大纲返回固定 JSON，撰写返回 Markdown"""

    def __init__(self, outline_raw=None, fail_titles=()):
        self.outline_raw = outline_raw
        self.fail_titles = fail_titles
        self.calls = []

    def chat(self, messages, temperature=0.3, max_tokens=None):
        system = messages[0]["content"]
        self.calls.append(system)
        if "Wiki 大纲规划器" in system:
            if self.outline_raw is not None:
                return self.outline_raw
            return json.dumps([
                {"title": "机器学习", "summary": "ML 总览", "sources": ["1"]},
                {"title": "计算机网络", "summary": "网络分层", "sources": ["2"]},
            ], ensure_ascii=False)
        # 撰写：按用户消息里的标题决定成败（页面级容错测试用）
        for t in self.fail_titles:
            if f"页面标题：{t}" in messages[1]["content"]:
                raise RuntimeError("LLM 调用失败")
        return f"# 页面\n\n正文内容" + "长" * 200 + "\n\n## 来源\n\n- A.md"


class FakeRetriever:
    def __init__(self):
        self.added = []

    def add_document(self, knowledge_id, doc_id, text):
        self.added.append((knowledge_id, doc_id, text))
        return 1


DOCS = [
    {"doc_id": "1", "name": "A.md", "file_url": "/data/files/A.md"},
    {"doc_id": "2", "name": "B.md", "file_url": "/data/files/B.md"},
]


@pytest.fixture
def wiki_env(monkeypatch):
    """patch _load_file / runtime.retriever / runtime.user_llm（generator 持有的是 agent.runtime 模块引用）"""
    monkeypatch.setattr("wiki.generator._load_file",
                        lambda url, name: f"{name} 的正文内容" * 30)
    retriever = FakeRetriever()
    monkeypatch.setattr(generator.runtime, "retriever", retriever, raising=False)
    monkeypatch.setattr(generator.runtime, "user_llm", None, raising=False)
    return retriever


def _run(llm, monkeypatch):
    monkeypatch.setattr(generator.runtime, "user_llm", llm, raising=False)
    return process_wiki_generate("k1", DOCS)


def test_process_wiki_generate_success_flow(wiki_env, monkeypatch):
    llm = FakeWikiLLM()
    result = _run(llm, monkeypatch)

    assert result["status"] == "SUCCESS"
    assert len(result["pages"]) == 2
    assert result["pages"][0]["title"] == "机器学习"
    for page in result["pages"]:
        assert page["status"] == "SUCCESS"
        assert page["vector_doc_id"].startswith("wiki-"), "回灌 Milvus 的 doc_id 应带 wiki- 前缀"
        assert page["content"]
    assert len(wiki_env.added) == 2, "每个成功页面都应回灌一次"
    assert wiki_env.added[0][0] == "k1"


def test_process_wiki_generate_outline_failure_degrades_to_overview(wiki_env, monkeypatch):
    llm = FakeWikiLLM(outline_raw="这不是 JSON")
    result = _run(llm, monkeypatch)

    assert result["status"] == "SUCCESS", "大纲失败应降级单页总览而非整任务失败"
    assert len(result["pages"]) == 1
    assert result["pages"][0]["title"] == "知识库总览"


def test_process_wiki_generate_partial_page_failure(wiki_env, monkeypatch):
    llm = FakeWikiLLM(fail_titles=("计算机网络",))
    result = _run(llm, monkeypatch)

    assert result["status"] == "SUCCESS", "≥1 页成功即任务 SUCCESS"
    failed = [p for p in result["pages"] if p["status"] == "FAILED"]
    assert len(failed) == 1
    assert "LLM 调用失败" in failed[0]["error_msg"]
    assert failed[0]["vector_doc_id"] is None, "失败页不应有向量"


def test_process_wiki_generate_all_pages_failed(wiki_env, monkeypatch):
    llm = FakeWikiLLM(fail_titles=("机器学习", "计算机网络"))
    result = _run(llm, monkeypatch)

    assert result["status"] == "FAILED"
    assert all(p["status"] == "FAILED" for p in result["pages"])
    assert wiki_env.added == [], "全败不应回灌任何向量"


def test_process_wiki_generate_all_docs_unreadable_raises(monkeypatch):
    def boom(url, name):
        raise RuntimeError("文件不存在")

    monkeypatch.setattr("wiki.generator._load_file", boom)
    with pytest.raises(WikiGenerationError):
        process_wiki_generate("k1", DOCS)


# ---- MockLLM 兜底分支（无 API Key 开发环境跑通全链路）----

def test_mock_llm_outline_branch():
    from llm.llm_client import MockLLM
    messages = [
        {"role": "system", "content": generator.OUTLINE_SYSTEM.format(max_pages=10)},
        {"role": "user", "content": "知识库文档清单：\n- [1] 机器学习.md：机器学习是人工智能的核心\n- [2] 网络.md：计算机网络分层"},
    ]
    arr = json.loads(MockLLM().chat(messages))
    assert isinstance(arr, list) and len(arr) == 2
    assert arr[0]["sources"] == ["1"]


def test_mock_llm_page_branch():
    from llm.llm_client import MockLLM
    messages = [
        {"role": "system", "content": generator.PAGE_SYSTEM},
        {"role": "user", "content": "页面标题：机器学习\n页面摘要：ML 总览"},
    ]
    text = MockLLM().chat(messages)
    assert "机器学习" in text and "# " in text
