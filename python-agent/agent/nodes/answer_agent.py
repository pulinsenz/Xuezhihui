"""
回答组装 Agent：基于检索证据生成答案（流式），引用来源编号。
"""
from utils.logger_util import get_logger, agent_event

logger = get_logger("answer")

ANSWER_SYSTEM_TEMPLATE = (
    "你是一个校园知识库问答助手「学智汇」。根据提供的知识库内容回答用户问题。\n"
    "规则：\n"
    "1. 只能基于提供的知识库内容回答，不得编造\n"
    "2. 如果知识库内容与问题无关或为空，明确告知用户『知识库中暂无相关信息』\n"
    "3. 回答简洁清晰，必要时分点\n"
    "4. 引用相关内容时标注来源编号，如 [1][2]\n\n"
    "知识库内容：\n{context}"
)

CHITCHAT_SYSTEM = (
    "你是校园知识库问答助手「学智汇」，回答简洁、友好、准确。\n"
    "仅用于闲聊、打招呼、自我介绍等非知识问答场景。"
)

NO_KB_SYSTEM = (
    "你是校园知识库问答助手「学智汇」。用户问的是校园知识类问题（课程、教务、校园生活等具体内容），"
    "但没有选择知识库，无法基于资料回答。\n"
    "回复要求：\n"
    "1. 明确告知用户当前没有选择知识库，引导其在对话上方选择知识库后重新提问\n"
    "2. 语气友好简洁，不要编造知识库内容，不要长篇客套\n"
    "示例：「这个问题需要先选择知识库才能基于资料回答～ 你可以在对话上方选择对应知识库，我再帮你查。」"
)


TOOL_SYSTEM_TEMPLATE = (
    "你是校园知识库问答助手「学智汇」。用户刚才查询了自己的业务数据，"
    "请根据以下系统统计数据如实转述，不要编造或夸大数字。\n\n"
    "业务数据：\n{context}"
)


def answer_agent(state):
    query = state["query"]
    context = state.get("retrieval_context", "").strip()
    tool_context = state.get("tool_context", "").strip()
    history = state.get("history", [])
    route = state.get("route", "")
    llm = state["llm"]

    # 上下文优先级：工具业务数据 > 知识库检索证据 > 知识问题但未选知识库(引导) > 闲聊
    if tool_context:
        system = TOOL_SYSTEM_TEMPLATE.format(context=tool_context)
    elif context:
        system = ANSWER_SYSTEM_TEMPLATE.format(context=context)
    elif route == "kb":
        # 知识类问题但没选知识库 → 明确引导，避免空泛闲聊
        system = NO_KB_SYSTEM
    else:
        system = CHITCHAT_SYSTEM
    messages = [{"role": "system", "content": system}]
    # 窗口裁剪：最多带 6 条历史
    messages.extend(history[-6:])
    messages.append({"role": "user", "content": query})

    # 流式生成：token 会被外部 graph.astream(stream_mode="messages") 捕获
    chunks = []
    for token in llm.stream(messages):
        chunks.append(token)
    answer = "".join(chunks).strip()
    agent_event(logger, "answer_done", query=query[:30], context_hits=len(state.get("sources", [])))
    return {"answer": answer}
