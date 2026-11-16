"""
反思校验 Agent：幻觉检测 + 检索改写。证据不足/无关 → 改写 query 触发二次检索（循环），
没有足够来源则拒绝编造答案。
"""
import re

from utils.logger_util import get_logger, agent_event

logger = get_logger("reflect")

REFLECT_SYSTEM = (
    "你是回答质量的反思校验器。根据用户问题与检索到的知识库片段，判断现有证据是否足以直接回答用户的问题。\n"
    "注意：片段与问题相关但缺少关键信息，仍应判定为证据不足。例如问「什么时候出现」却只检索到发展阶段概述、"
    "问「具体数值」却只有定性描述，都算证据不足。\n"
    "如果证据不足：第一行输出 no，第二行输出改写后的检索查询（纠正错别字/拼写、补全指代、聚焦缺失的关键信息），格式：\n"
    "no\n改写查询：<改写后的查询>\n"
    "如果证据足够，只输出一个词：yes"
)


def _parse_reflect(reply: str):
    """解析反思输出：(sufficient, rewritten_query)。
    yes → 充分；no → 不充分并提取改写查询（未给出则回退 None，二次检索用原 query）。"""
    text = (reply or "").strip()
    if text.lower().startswith("yes"):
        return True, None
    rewritten = None
    m = re.search(r"改写查询[:：]\s*(.+)", text, re.S)
    if m:
        candidate = m.group(1).strip()
        if candidate and candidate.lower() not in ("无", "none", "null", "n/a", "无需"):
            rewritten = candidate
    return False, rewritten


def reflect_agent(state):
    context = state.get("retrieval_context", "")
    query = state["query"]
    # 空上下文必然不足（无改写依据，二次检索仍用原 query）
    if not context.strip():
        sufficient, rewritten = False, None
    else:
        llm = state["llm"]
        try:
            reply = llm.chat([
                {"role": "system", "content": REFLECT_SYSTEM},
                {"role": "user", "content": f"问题：{query}\n\n知识库片段：\n{context[:1200]}"},
            ])
            sufficient, rewritten = _parse_reflect(reply)
        except Exception as e:
            logger.error("反思校验失败，默认认为证据足够: %s", e)
            sufficient, rewritten = True, None
    agent_event(logger, "reflect_done", query=query[:30], sufficient=sufficient,
                retry=state.get("retry_count", 0), rewritten=bool(rewritten))
    return {"sufficient": sufficient, "rewritten_query": rewritten}
