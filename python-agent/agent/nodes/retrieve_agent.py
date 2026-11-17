"""
检索 Agent：混合检索（BM25 + 向量）召回证据，供回答引用。
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("retrieve")

# 泛化/总结类问法关键词：命中即优先在 Wiki 集合内检索（Wiki 是整理过的概括知识）
_GENERAL_KEYWORDS = (
    "总结", "概括", "概览", "概述", "总览", "综述", "梳理", "归纳",
    "讲什么", "讲了什么", "讲的是什么", "讲哪些", "主要讲", "讲了哪些", "讲些什么",
    "内容是什么", "内容有哪些", "有什么内容", "有哪些内容", "包含什么", "包含哪些",
    "目录", "大纲", "章节", "知识结构", "体系结构",
    "主要内容", "核心内容", "知识点", "学了什么", "学什么", "学到了什么",
    "整体介绍", "整体讲", "全部内容", "知识框架",
)


def _is_general_query(query: str) -> bool:
    """判断是否泛化/总结类问题（问整个知识库讲了什么、核心内容、目录结构等）"""
    text = query or ""
    return any(kw in text for kw in _GENERAL_KEYWORDS)


def retrieve_agent(state):
    retriever = state["retriever"]
    reranker = state.get("reranker")
    knowledge_id = state.get("knowledge_id")
    retry_count = state.get("retry_count", 0) + 1
    # 二次检索优先用反思节点改写的 query（纠正拼写/补全指代）；首次用原始 query
    query = state.get("rewritten_query") or state["query"]
    if not knowledge_id:
        return {"retrieval_context": "", "sources": [], "retry_count": retry_count}
    try:
        if _is_general_query(state["query"]) and state.get("retry_count", 0) == 0:
            # 泛化/总结类问题（仅首次检索）：优先仅在 Wiki 集合内检索（整理过的概括知识，
            # 避开原始文档噪声碎片）；二次检索用改写后的具体 query 走全库兜底
            docs = retriever.retrieve_wiki(query, knowledge_id, top_k=10)
            if not docs:
                # 知识库未生成 Wiki → 回退全库混合检索
                docs = retriever.retrieve(query, knowledge_id, top_k=10)
        else:
            # 召回候选池扩到 10，交给交叉编码器精排后取 top 5；无 reranker 时截断回 5
            docs = retriever.retrieve(query, knowledge_id, top_k=10)
            # 泛化问题的二次检索：把首次 Wiki 证据合并回候选（Wiki 置前、按 doc_id+text 去重）。
            # 否则反思循环一旦触发二次全库检索，sources 被整体覆盖，Wiki 优先形同虚设。
            if _is_general_query(state["query"]) and retry_count == 2:
                seen = {(d.get("doc_id"), d["text"]) for d in docs}
                docs = [d for d in state.get("sources", [])
                        if (d.get("doc_id"), d["text"]) not in seen] + docs
        if reranker is not None and docs:
            docs = reranker.rerank(query, docs, top_k=5)
        else:
            docs = docs[:5]
    except Exception as e:
        logger.error("检索失败: %s", e)
        docs = []
    context = "\n\n".join(d["text"] for d in docs)
    agent_event(logger, "retrieved", knowledge_id=knowledge_id, hits=len(docs), retry=retry_count,
                query=query[:30], rewritten=query != state["query"])
    return {"retrieval_context": context, "sources": docs, "retry_count": retry_count}
