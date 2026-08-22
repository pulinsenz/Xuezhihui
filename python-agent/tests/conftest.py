"""共享测试替身：FakeLLM / FakeRetriever / FakeVectorStore，不加载真实模型"""


class FakeLLM:
    """可控响应的假 LLM：按 system prompt 关键词返回配置回复"""

    def __init__(self, route="kb", reflect="yes", answer="这是基于知识库的回答"):
        self.route = route
        self.reflect = reflect
        self.answer = answer

    def chat(self, messages):
        system = messages[0]["content"]
        if "路由判断器" in system:
            return self.route
        if "反思校验器" in system:
            return self.reflect
        return self.answer

    def stream(self, messages):
        for token in self.answer.split(" "):
            yield token + " "


class FakeRetriever:
    def __init__(self, docs=None):
        self.docs = docs or [
            {"text": "数据结构是计算机的核心课程，包含链表、栈、队列。", "score": 0.9}
        ]

    def retrieve(self, query, knowledge_id, top_k=5):
        return self.docs


class FakeVectorStore:
    """不加载 embedding 的假向量库（按 knowledge_id 隔离）"""

    def __init__(self):
        self.data = {}  # knowledge_id -> [chunk...]

    def add_document(self, knowledge_id, doc_id, text, chunks):
        self.data[str(knowledge_id)] = list(chunks)
        return len(chunks)

    def delete_document(self, knowledge_id, doc_id):
        pass

    def delete_knowledge(self, knowledge_id):
        self.data.pop(str(knowledge_id), None)

    def search(self, query, knowledge_id=None, top_k=5):
        if knowledge_id is not None:
            chunks = self.data.get(str(knowledge_id), [])
        else:
            chunks = [c for v in self.data.values() for c in v]
        return [{"text": c, "score": 0.5} for c in chunks[:top_k]]
