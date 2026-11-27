"""按已识别范围取证，目录与历史证据不进入相似度检索。"""
from agent.nodes.evidence import historical_evidence, numbered_context, outline_evidence
from rag.retriever import retrieve_ranked
from utils.logger_util import get_logger, agent_event

logger = get_logger("retrieve")


def retrieve_agent(state):
    plan = state.get("intent_plan", {})
    kind = plan.get("intent", "knowledge")
    retry_count = state.get("retry_count", 0) + 1
    base = {"retry_count": retry_count, "sources": [], "retrieval_context": "",
            "evidence_error": "", "evidence_complete": False, "evidence_kind": "retrieval"}
    try:
        if kind in ("provenance", "transform"):
            return {**base, **historical_evidence(state)}
        if plan.get("scope") == "knowledge_base" and plan.get("operation") == "overview":
            return {**base, **outline_evidence(state)}
        if not state.get("knowledge_id"):
            return base
        query = state.get("rewritten_query") or plan.get("resolved_query") or state["query"]
        docs = retrieve_ranked(state["retriever"], state.get("reranker"), query, state["knowledge_id"])
        agent_event(logger, "retrieved", knowledge_id=state["knowledge_id"], hits=len(docs),
                    retry=retry_count, query=query[:30])
        return {**base, "sources": docs, "retrieval_context": numbered_context(docs)}
    except Exception as exc:
        logger.error("取证失败: %s", exc)
        return {**base, "evidence_error": "读取所需资料失败，请稍后重试；本次无法确认资料内容。"}
