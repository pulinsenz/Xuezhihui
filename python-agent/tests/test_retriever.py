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


def test_revectorize_keeps_other_docs_bm25():
    """重新入库同一文档后，同知识库其他文档的 BM25 索引不应丢失（回归）"""
    retriever = _make_retriever()
    retriever.add_document("kb1", "doc2", "操作系统管理进程、内存与文件系统。")  # 重入 doc2
    docs = retriever.retrieve("数据结构链表", "kb1", top_k=3)
    assert any("数据结构" in d["text"] for d in docs), "doc1 的 BM25 分块应保留"


def test_delete_document_removes_bm25_chunks():
    """删除单文档：向量与 BM25 同步移除，其他文档不受影响"""
    retriever = _make_retriever()
    retriever.delete_document("kb1", "doc1")
    docs = retriever.retrieve("数据结构链表", "kb1", top_k=3)
    assert not any("数据结构" in d["text"] for d in docs), "doc1 内容不应再被检索"
    docs = retriever.retrieve("操作系统", "kb1", top_k=3)
    assert any("操作系统" in d["text"] for d in docs), "doc2 仍应可检索"


def test_delete_knowledge_clears_bm25_and_vectors():
    """删除整个知识库：向量与 BM25 全部清空"""
    retriever = _make_retriever()
    retriever.delete_knowledge("kb1")
    assert retriever.retrieve("数据结构", "kb1", top_k=3) == []
    # 内部索引也应清理
    assert "kb1" not in retriever._bm25
    assert "kb1" not in retriever._bm25_docs
    assert "kb1" not in retriever._chunks
    # 向量库同步清空
    assert "kb1" not in retriever.vector_store.data
