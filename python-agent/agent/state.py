"""
LangGraph 状态定义：Agent 间共享上下文，支持循环重试。
"""
from typing import Any, List, Optional, TypedDict


class AgentState(TypedDict, total=False):
    # 输入
    query: str
    session_id: str
    knowledge_id: Optional[str]
    history: List[dict]

    # 运行时注入的组件
    llm: Any
    retriever: Any

    # 节点间流转
    route: str                    # kb / chitchat / other
    retrieval_context: str        # 检索到的文档拼接文本
    sources: List[dict]           # 引用来源
    sufficient: bool              # 反思校验：证据是否足够
    retry_count: int
    max_retries: int

    # 输出
    answer: Optional[str]
