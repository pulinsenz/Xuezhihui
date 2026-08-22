"""
反思校验 Agent：幻觉检测。证据不足/无关 → 触发二次检索（循环），
没有足够来源则拒绝编造答案。
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("reflect")

REFLECT_SYSTEM = (
    "你是回答质量的反思校验器。根据用户问题与检索到的知识库片段，"
    "判断现有证据是否足以回答问题。\n"
    "如果知识库片段为空、与问题无关或信息不足，回答 no。否则回答 yes。\n"
    "只输出一个词：yes 或 no"
)


def reflect_agent(state):
    context = state.get("retrieval_context", "")
    query = state["query"]
    # 空上下文必然不足
    if not context.strip():
        sufficient = False
    else:
        llm = state["llm"]
        try:
            reply = llm.chat([
                {"role": "system", "content": REFLECT_SYSTEM},
                {"role": "user", "content": f"问题：{query}\n\n知识库片段：\n{context[:1200]}"},
            ])
            sufficient = reply.strip().lower().startswith("yes")
        except Exception as e:
            logger.error("反思校验失败，默认认为证据足够: %s", e)
            sufficient = True
    agent_event(logger, "reflect_done", query=query[:30], sufficient=sufficient,
                retry=state.get("retry_count", 0))
    return {"sufficient": sufficient}
