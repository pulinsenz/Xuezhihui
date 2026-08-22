"""
检索 Agent：混合检索（BM25 + 向量）召回证据，供回答引用。
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("retrieve")


def retrieve_agent(state):
    retriever = state["retriever"]
    knowledge_id = state.get("knowledge_id")
    retry_count = state.get("retry_count", 0) + 1
    if not knowledge_id:
        return {"retrieval_context": "", "sources": [], "retry_count": retry_count}
    try:
        docs = retriever.retrieve(state["query"], knowledge_id, top_k=5)
    except Exception as e:
        logger.error("检索失败: %s", e)
        docs = []
    context = "\n\n".join(d["text"] for d in docs)
    agent_event(logger, "retrieved", knowledge_id=knowledge_id, hits=len(docs), retry=retry_count)
    return {"retrieval_context": context, "sources": docs, "retry_count": retry_count}
