"""
检索 Agent：混合检索（BM25 + 向量）召回证据，供回答引用。
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("retrieve")


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
        # 召回候选池扩到 10，交给交叉编码器精排后取 top 5；无 reranker 时截断回 5
        docs = retriever.retrieve(query, knowledge_id, top_k=10)
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
