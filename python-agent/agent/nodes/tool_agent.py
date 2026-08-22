"""
工具 Agent：业务数据查询。
信任边界：Python Agent 不直接访问 MySQL，业务数据一律回调 Java 服务获取
（Java internal 接口校验 X-Agent-Token）。拿到的统计格式化为文本，
供 answer 节点如实转述，杜绝编造数字。
"""
from utils.java_client import fetch_user_stats
from utils.logger_util import get_logger, agent_event

logger = get_logger("tool")


def _format_stats(stats: dict) -> str:
    """Java UserStatsVO（snake_case）→ 可读文本。缺省补 0，避免 None 拼进回答。"""
    def num(key: str) -> int:
        value = stats.get(key)
        return int(value) if value not in (None, "") else 0

    lines = [
        f"知识库数量：{num('knowledge_count')} 个",
        f"上传文档总数：{num('doc_count')} 篇",
        f"其中已向量化：{num('vector_success')} 篇，"
        f"处理中：{num('vector_pending')} 篇，失败：{num('vector_failed')} 篇",
    ]
    last = stats.get("last_upload_time")
    if last:
        lines.append(f"最近上传时间：{last}")
    return "；".join(lines)


def tool_agent(state):
    """回调 Java 获取用户业务数据。无 user_id 或回调失败 → 降级为空上下文（走普通回答）。"""
    user_id = state.get("user_id")
    called = False
    context = ""
    if user_id:
        stats = fetch_user_stats(user_id)
        if stats:
            called = True
            context = _format_stats(stats)
    agent_event(logger, "tool_done", user_id=user_id, called=called, has_context=bool(context))
    return {"tool_called": called, "tool_context": context}
