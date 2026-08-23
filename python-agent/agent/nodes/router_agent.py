"""
路由 Agent：判断用户问题类型 → 知识库问答 / 闲聊 / 其他
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("router")

ROUTER_SYSTEM = (
    "你是一个校园知识库问答系统的路由判断器。判断用户问题属于哪一类：\n"
    "- business：查询用户自己的业务数据，如『我的知识库有几个』『我上传了多少文档』『向量化完成了吗』『我的统计』这类个人数据/账户概况问题\n"
    "- kb：与校园知识库内容相关的问答（课程、教务、校园生活等具体知识内容），"
    "以及用户上传到知识库的个人记录/学习日志/笔记内容的问答（如『我某天做了什么』『日记/笔记里记了什么』）\n"
    "- chitchat：闲聊、打招呼、自我介绍等\n"
    "- other：与知识库无关或不合适的问题\n"
    "注意：询问『我的知识库/我的文档』的数据概况属于 business；只有询问知识库内的具体内容（如某个知识点）才属于 kb。\n"
    "只输出一个词：business 或 kb 或 chitchat 或 other"
)

# 每个路由的匹配关键词（英文输出优先，中文标签兜底，容忍带标点/解释）
ROUTE_KEYWORDS = {
    "business": ("business", "业务"),
    "kb": ("kb", "知识库"),
    "chitchat": ("chitchat", "闲聊"),
    "other": ("other", "其他"),
}


def _parse_route(reply: str) -> str:
    """解析路由 LLM 输出。模型偶尔输出带标点或解释（如 "kb。"、"chitchat（闲聊）"、
    "知识库问答"），首词包含关键词即命中；首词失败再匹配整句，兜底 chitchat。"""
    if not reply or not reply.strip():
        return "chitchat"
    text = reply.strip().lower()
    first = text.split()[0]
    for route, keywords in ROUTE_KEYWORDS.items():
        if any(kw in first for kw in keywords):
            return route
    # 首词没匹配上（如整句中文输出），退而匹配整句
    for route, keywords in ROUTE_KEYWORDS.items():
        if any(kw in text for kw in keywords):
            return route
    return "chitchat"


def router_agent(state):
    llm = state["llm"]
    query = state["query"]
    route = "chitchat"
    try:
        reply = llm.chat([
            {"role": "system", "content": ROUTER_SYSTEM},
            {"role": "user", "content": query},
        ])
        route = _parse_route(reply)
    except Exception as e:
        logger.error("路由判断失败，兜底为 chitchat: %s", e)
    agent_event(logger, "route_decided", query=query[:30], route=route)
    return {"route": route}
