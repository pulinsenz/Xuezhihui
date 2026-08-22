"""
路由 Agent：判断用户问题类型 → 知识库问答 / 闲聊 / 其他
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("router")

ROUTER_SYSTEM = (
    "你是一个校园知识库问答系统的路由判断器。判断用户问题属于哪一类：\n"
    "- kb：与校园知识库内容相关的问答（课程、教务、校园生活等）\n"
    "- chitchat：闲聊、打招呼、自我介绍等\n"
    "- other：与知识库无关或不合适的问题\n"
    "只输出一个词：kb 或 chitchat 或 other"
)


def router_agent(state):
    llm = state["llm"]
    query = state["query"]
    route = "chitchat"
    try:
        reply = llm.chat([
            {"role": "system", "content": ROUTER_SYSTEM},
            {"role": "user", "content": query},
        ])
        word = reply.strip().lower().split()[0] if reply.strip() else ""
        if word in ("kb", "chitchat", "other"):
            route = word
    except Exception as e:
        logger.error("路由判断失败，兜底为 chitchat: %s", e)
    agent_event(logger, "route_decided", query=query[:30], route=route)
    return {"route": route}
