"""混合检索器单元测试（使用 FakeVectorStore，不加载 embedding）"""
from rag.chunker import Chunker
from rag.retriever import HybridRetriever
from tests.conftest import FakeVectorStore


def _make_retriever():
    store = FakeVectorStore()
    retriever = HybridRetriever(store, chunker=Chunker(chunk_size=20, overlap=2))
    retriever.add_document("kb1", "doc1", "数据结构课程涵盖链表、栈、队列、树。")
    retriever.add_document("kb1", "doc2", "操作系统管理进程、内存与文件系统。")
    return retriever


def test_retrieve_returns_scored_docs():
    retriever = _make_retriever()
    docs = retriever.retrieve("数据结构包括哪些内容", "kb1", top_k=2)
    assert len(docs) >= 1
    for d in docs:
        assert "text" in d and "score" in d


def test_retrieve_knowledge_scoped():
    retriever = _make_retriever()
    docs = retriever.retrieve("操作系统", "kb1", top_k=3)
    # 至少有一条与"操作系统"相关的片段
    assert any("操作系统" in d["text"] for d in docs)


def test_retrieve_empty_knowledge():
    retriever = _make_retriever()
    docs = retriever.retrieve("不存在的知识库", "kb999", top_k=3)
    assert docs == []
