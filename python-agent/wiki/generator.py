"""
AI Wiki 生成器核心逻辑（worker 消费，模式同 process_vectorize：核心与触发分离）。

链路：解析知识库全部文档 → LLM 大纲规划（JSON，页数封顶 wiki_max_pages）
      → 逐页撰写（关键词重叠从源文档 chunk 贪心选上下文，控 token 成本）
      → 每页回灌 Milvus（doc_id=wiki-{uuid}，doc_type 与普通文档区分）。

容错策略：
  - 单篇文档解析失败 → 跳过该篇（日志记录），不阻断整任务；
  - 大纲 JSON 解析失败 → 降级为单页「知识库总览」，仍可产出可用 Wiki；
  - 单页撰写失败 → 该页置 FAILED（带原因），其余页照常入库，≥1 页成功即任务 SUCCESS。
"""
import json
import re
import uuid
from typing import List

from agent import runtime
from api.knowledge_api import _load_file
from config import settings
from rag.chunker import Chunker
from utils.logger_util import get_logger, agent_event

logger = get_logger("wiki")

OUTLINE_SYSTEM = (
    "你是学智汇平台的 Wiki 大纲规划器。根据知识库的文档清单，规划一套结构化的 Wiki 目录。"
    "要求：3~{max_pages} 个页面，覆盖全部文档的核心知识，页面之间不重复、不遗漏；"
    "每页指明主要依据的文档。只输出 JSON 数组，不要输出任何其他文字，格式："
    '[{{"title":"页面标题","summary":"一句话摘要","sources":["文档id"]}}]'
)

PAGE_SYSTEM = (
    "你是学智汇平台的 Wiki 页面撰写器，负责把知识库文档内容整理为结构严谨的 Markdown 百科页面。"
    "要求：只依据给定资料撰写，忠实原文、不编造；用 Markdown 标题/列表/表格组织内容，"
    "正文提到有独立章节的相关主题时，从可链接章节清单复制对应 Markdown 链接，放在首次提及处。"
    "仅链接相关章节，不编造链接，也不要为了添加链接引入参考资料之外的事实。"
    "结尾用「## 来源」小节列出引用的文档名。直接输出 Markdown 正文。"
)

# 上下文打分用停用词（中文单字虚词 + 常见英文虚词）；中文按字重叠已足够贪心排序用
_STOP = set("的了是在和与或及等对从向于把被为之这那有也都就还很更最又再把一到让"
            "a an the of to in and or is are for with on at by be as it its this that")


class WikiGenerationError(Exception):
    """Wiki 生成核心逻辑的业务失败（worker 拿到的是裸异常，消息才可读）"""


def process_wiki_generate(knowledge_id: str, docs: List[dict]) -> dict:
    """Wiki 生成核心逻辑：解析 → 大纲 → 逐页 → 回灌。
    docs: [{"doc_id", "name", "file_url"}]；返回回调 Java 的结果体。"""
    llm = runtime.user_llm or runtime.llm

    # 1. 解析文档 + 分块缓存（单篇失败跳过，不阻断整任务）
    usable: List[dict] = []
    for doc in docs:
        doc_id, name = str(doc.get("doc_id", "")), str(doc.get("name", ""))
        try:
            text = _load_file(doc.get("file_url", ""), name)
            chunks = Chunker().split(text)
            if not text.strip() or not chunks:
                logger.warning("Wiki 源文档内容为空，跳过: knowledge_id=%s, doc=%s", knowledge_id, name)
                continue
            usable.append({"doc_id": doc_id, "name": name, "chunks": chunks})
        except Exception as e:
            logger.warning("Wiki 源文档解析失败，跳过: knowledge_id=%s, doc=%s, error=%s",
                           knowledge_id, name, e)
    if not usable:
        raise WikiGenerationError("所有文档解析失败或内容为空，无法生成 Wiki")

    # 2. 大纲规划（解析失败降级单页总览，保证产出可用）
    doc_ids = {d["doc_id"] for d in usable}
    try:
        outline = _parse_outline(
            llm.chat([{"role": "system", "content": OUTLINE_SYSTEM.format(max_pages=settings.wiki_max_pages)},
                      {"role": "user", "content": _outline_user(usable)}]),
            doc_ids, settings.wiki_max_pages)
    except Exception as e:
        logger.warning("大纲解析失败，降级为单页总览: %s", e)
        outline = [{"title": "知识库总览", "summary": "知识库全部文档的内容总览",
                    "sources": sorted(doc_ids)}]

    # 3. 逐页撰写 + 回灌（页面级容错：单页失败不影响其余页）
    pages = []
    for idx, entry in enumerate(outline, start=1):
        page = {"title": entry["title"], "summary": entry["summary"],
                "source_doc_ids": json.dumps(entry["sources"], ensure_ascii=False),
                "content": "", "vector_doc_id": None,
                "status": "FAILED", "error_msg": None}
        try:
            context = _build_context(entry, usable, settings.wiki_page_context_chars)
            content = llm.chat(
                [{"role": "system", "content": PAGE_SYSTEM},
                 {"role": "user", "content": _page_user(entry, context, outline, idx)}],
                max_tokens=4096).strip()
            if not content:
                raise WikiGenerationError("页面内容为空")
            # 回灌 Milvus：RAG 可检索到「整理过」的知识；doc_id 带 wiki- 前缀便于清理
            vector_doc_id = f"wiki-{uuid.uuid4().hex}"
            runtime.retriever.add_document(knowledge_id, vector_doc_id, content)
            page.update(content=content, vector_doc_id=vector_doc_id, status="SUCCESS")
            agent_event(logger, "wiki_page_done", knowledge_id=str(knowledge_id),
                        title=entry["title"][:30], chars=len(content))
        except Exception as e:
            page["error_msg"] = str(e)[:500]
            logger.warning("Wiki 页面生成失败: knowledge_id=%s, title=%s, error=%s",
                           knowledge_id, entry["title"], page["error_msg"])
        pages.append(page)

    succeeded = [p for p in pages if p["status"] == "SUCCESS"]
    status = "SUCCESS" if succeeded else "FAILED"
    message = None if succeeded else "所有页面生成失败"
    # Wiki 回灌改变了知识库内容：失效该库答案缓存，防返回过时答案
    if succeeded and runtime.answer_cache:
        runtime.answer_cache.invalidate(knowledge_id)
    agent_event(logger, "wiki_done", knowledge_id=str(knowledge_id),
                total=len(pages), success=len(succeeded), status=status)
    return {"status": status, "message": message, "pages": pages}


# ---------- 大纲 ----------

def _outline_user(usable: List[dict]) -> str:
    """大纲用户消息：文档清单（名称 + 截断预览），LLM 据此规划目录"""
    lines = []
    for d in usable:
        preview = d["chunks"][0][:200].replace("\n", " ")
        lines.append(f"- [{d['doc_id']}] {d['name']}：{preview}")
    return "知识库文档清单：\n" + "\n".join(lines)


def _parse_outline(raw: str, doc_ids: set, max_pages: int) -> List[dict]:
    """鲁棒解析大纲 JSON：剥 ```json 围栏 → 截取首 [ 至末 ] → 逐项清洗。
    sources 里的未知 doc_id 丢弃，全部无效则回退全部文档。"""
    text = (raw or "").strip()
    fence = re.search(r"```(?:json)?\s*(.+?)```", text, re.S)
    if fence:
        text = fence.group(1).strip()
    start, end = text.find("["), text.rfind("]")
    if start == -1 or end == -1 or end < start:
        raise ValueError("大纲响应中未找到 JSON 数组")
    arr = json.loads(text[start:end + 1])
    pages = []
    for item in arr[:max_pages]:
        if not isinstance(item, dict) or not str(item.get("title", "")).strip():
            continue
        sources = [str(s) for s in (item.get("sources") or []) if str(s) in doc_ids]
        pages.append({
            "title": str(item["title"]).strip()[:120],
            "summary": str(item.get("summary", "")).strip()[:500],
            "sources": sources or sorted(doc_ids),
        })
    if not pages:
        raise ValueError("大纲 JSON 解析后无有效页面")
    return pages


# ---------- 页面上下文 ----------

def _keywords(text: str) -> set:
    """提取关键词集合：英文/数字单词 + 中文单字（重叠打分贪心排序够用）"""
    words = re.findall(r"[A-Za-z0-9_]+|[一-鿿]", text or "")
    return {w for w in words if w not in _STOP}


def _build_context(entry: dict, usable: List[dict], budget: int) -> List[tuple]:
    """按「页面标题+摘要 与 chunk 的关键词重叠分」贪心选取源文档 chunk，总字符数不超 budget。
    返回 [(文档名, chunk), ...]；列表已按得分降序。"""
    query_kw = _keywords(entry["title"] + entry["summary"])
    scored = []
    for doc in usable:
        if entry["sources"] and doc["doc_id"] not in entry["sources"]:
            continue
        for chunk in doc["chunks"]:
            score = len(query_kw & _keywords(chunk))
            scored.append((score, doc["name"], chunk))
    # 得分降序，同分保持文档原有顺序（stable sort）
    scored.sort(key=lambda x: -x[0])
    selected, total = [], 0
    for score, name, chunk in scored:
        if total >= budget:
            break
        if total + len(chunk) > budget and selected:
            continue  # 装不下当前块则跳过，尝试更短的后续块
        selected.append((name, chunk))
        total += len(chunk)
    if not selected and scored:
        selected.append((scored[0][1], scored[0][2]))
    return selected


def _page_user(entry: dict, context: List[tuple], outline: List[dict], current_order: int) -> str:
    parts = [f"页面标题：{entry['title']}", f"页面摘要：{entry['summary']}"]
    if len(outline) > 1:
        parts.extend(["", "可链接章节："])
        # Java 按大纲顺序保存章节；正文用序号引用，改标题后仍指向同一页。
        for order, page in enumerate(outline, start=1):
            if order != current_order:
                title = re.sub(r"([\\\[\]])", r"\\\1", page["title"])
                parts.append(f"- [{title}](#wiki-page-{order})")
    parts.extend(["", "参考资料："])
    for name, chunk in context:
        parts.append(f"【{name}】\n{chunk}\n")
    return "\n".join(parts)
