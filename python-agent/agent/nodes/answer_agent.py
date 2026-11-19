"""
回答组装 Agent：基于检索证据生成答案（流式），引用来源编号。
"""
import re

from utils.logger_util import get_logger, agent_event

logger = get_logger("answer")

# 正文引用编号：容忍连写 [1][3]、逗号列举 [2, 4]，不支持范围写法
_CITE_RE = re.compile(r"\[(\d+(?:\s*[,，、;；]\s*\d+)*)\]")

ANSWER_SYSTEM_TEMPLATE = (
    "你是一个校园知识库问答助手「学智汇」。根据提供的知识库内容回答用户问题。\n"
    "规则：\n"
    "1. 只能基于提供的知识库内容回答，不得编造\n"
    "2. 如果知识库内容与问题无关或为空，明确告知用户『知识库中暂无相关信息』\n"
    "3. 回答简洁清晰，必要时分点\n"
    "4. 引用相关内容时标注来源编号，如 [1][2]；只标注实际支撑回答内容的编号，未用到的资料不要标注\n\n"
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


def filter_cited_sources(answer, sources):
    """只保留回答正文实际引用的分片（编号为 numbered_context 的 1 起始序号）。
    兜底：无来源原样返回；解析不到任何有效编号（LLM 未标注）不裁剪，宁多勿少；
    保留分片原有顺序，附加 cite_no 原始编号供前端展示与正文编号一致。"""
    if not sources:
        return sources
    total = len(sources)
    cited = set()
    for group in _CITE_RE.findall(answer or ""):
        for piece in re.split(r"[,，、;；]", group):
            n = int(piece)
            if 1 <= n <= total:
                cited.add(n)
    if not cited:
        return sources
    return [{**source, "cite_no": n} for n, source in enumerate(sources, 1) if n in cited]


def answer_agent(state):
    query = state["query"]
    context = state.get("retrieval_context", "").strip()
    tool_context = state.get("tool_context", "").strip()
    history = state.get("history", [])
    route = state.get("route", "")
    llm = state["llm"]

    plan = state.get("intent_plan", {})
    evidence_kind = state.get("evidence_kind")
    evidence_error = state.get("evidence_error")
    # 上下文优先级：工具业务数据 > 知识库检索证据 > 知识问题但未选知识库(引导) > 闲聊
    if plan.get("needs_clarification"):
        system = "请仅向用户澄清以下缺失信息，不猜测内容：" + plan.get("clarification_question", "请说明要询问的对象。")
    elif evidence_error:
        system = "如实向用户说明以下取证结果，不补写未经核实的事实：" + evidence_error
    elif evidence_kind == "outline":
        system = ANSWER_SYSTEM_TEMPLATE.format(context=context) + "\n这是完整目录概览。覆盖每个主题，允许合并相关条目，但不得只选一个主题。无正文的目录仅说明内容范围。未覆盖文档和目录局限需明确说明。"
    elif evidence_kind == "provenance":
        system = ANSWER_SYSTEM_TEMPLATE.format(context=context) + "\n用户在核对历史回答。只解释该回答实际保存的依据，给出对应原文和引用；不得把新检索或模型知识称作旧依据。"
    elif evidence_kind == "transform":
        system = "按用户要求加工指定历史回答，保持事实及引用对应关系，不添加新事实。若要求扩展而现有依据不足，明确说明。\n" + context
    elif tool_context:
        system = TOOL_SYSTEM_TEMPLATE.format(context=tool_context)
    elif context:
        system = ANSWER_SYSTEM_TEMPLATE.format(context=context)
    elif route == "kb" and not state.get("knowledge_id"):
        system = NO_KB_SYSTEM
    elif route == "kb":
        system = "当前所选知识库未检索到足够的相关依据。请明确说明这一限制，不将其说成未选择知识库，不编造答案。"
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
    return {"answer": answer, "sources": filter_cited_sources(answer, state.get("sources", []))}
