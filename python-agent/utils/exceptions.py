"""
自定义异常
"""


class AgentError(Exception):
    """Agent 业务异常基类"""

    def __init__(self, message: str, code: int = 50000):
        super().__init__(message)
        self.message = message
        self.code = code


class LLMError(AgentError):
    """大模型调用异常"""

    def __init__(self, message: str):
        super().__init__(message, code=50002)


class VectorStoreError(AgentError):
    """向量库异常"""

    def __init__(self, message: str):
        super().__init__(message, code=50003)


class RetrieverError(AgentError):
    """检索异常"""

    def __init__(self, message: str):
        super().__init__(message, code=50004)
