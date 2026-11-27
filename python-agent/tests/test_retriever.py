"""混合检索器单元测试（使用 FakeVectorStore，不加载 embedding）"""
from unittest.mock import Mock

from rag.chunker import Chunker
from rag.retriever import HybridRetriever
from storage.milvus_store import MilvusStore
from storage.vector_store import LocalVectorStore
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


class ConstantEmbedding:
    def embed_documents(self, chunks):
        return [[1.0] for _ in chunks]

    def embed_query(self, query):
        return [1.0]


def test_bm25_only_hit_keeps_chunk_and_document_ids(monkeypatch):
    store = LocalVectorStore(ConstantEmbedding())
    retriever = HybridRetriever(store)
    retriever.add_document("kb", "scholarship", "scholarship eligibility")
    retriever.add_document("kb", "course", "course registration")
    retriever.add_document("kb", "library", "library hours")
    monkeypatch.setattr(store, "search", lambda *args, **kwargs: [])

    hit, = retriever.retrieve("scholarship", "kb")
    assert hit["doc_id"] == "scholarship"
    assert hit["chunk_id"]


def test_same_text_from_different_documents_keeps_both_sources():
    store = LocalVectorStore(ConstantEmbedding())
    retriever = HybridRetriever(store)
    for doc_id in ("current", "archived"):
        retriever.add_document("kb", doc_id, "scholarship eligibility")

    hits = retriever.retrieve("scholarship", "kb")
    assert {hit["doc_id"] for hit in hits} == {"current", "archived"}
    assert len({hit["chunk_id"] for hit in hits}) == 2


def test_milvus_fuses_both_paths_using_the_existing_row_primary_key():
    store = MilvusStore.__new__(MilvusStore)
    store.collection = "existing_chunks"
    store.embedding = ConstantEmbedding()
    row_id = 9007199254740993
    store.client = Mock()
    store.client.query.return_value = [
        {"id": row_id, "doc_id": "10", "text": "scholarship eligibility"},
        {"id": 22, "doc_id": "20", "text": "course registration"},
        {"id": 33, "doc_id": "30", "text": "library hours"},
    ]
    store.client.search.return_value = [[
        {"id": row_id, "distance": 0.8, "entity": {"doc_id": "10", "text": "scholarship eligibility"}},
    ]]

    hit, = HybridRetriever(store).retrieve("scholarship", "kb")

    assert hit["doc_id"] == "10"
    assert hit["chunk_id"] == str(row_id)
    assert hit["vector_rank"] == hit["bm25_rank"] == 1


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
    # 内部索引也应清理（数据源=向量库，回灌后为空）
    assert "kb1" not in retriever._bm25
    assert "kb1" not in retriever._bm25_docs
    assert "kb1" not in retriever._dirty
    # 向量库同步清空
    assert "kb1" not in retriever.vector_store.data


def test_get_chunks_returns_doc_chunks():
    """「向量详情」：按文档返回切片文本（FakeVectorStore 保持入库顺序）"""
    retriever = _make_retriever()
    chunks = retriever.get_chunks("kb1", "doc1")
    assert chunks, "已入库文档应能查到切片"
    assert any("数据结构" in c for c in chunks), "doc1 切片应包含其内容"


def test_get_chunks_unknown_doc_empty():
    """「向量详情」：未入库/不存在的文档返回空列表"""
    retriever = _make_retriever()
    assert retriever.get_chunks("kb1", "no_such_doc") == []
    assert retriever.get_chunks("kb999", "doc1") == []


def test_bm25_rehydrates_from_vector_store():
    """重启回灌：内存索引清空后，下次检索自动从向量库重建（回归：BM25 持久化数据源）"""
    retriever = _make_retriever()
    # 模拟进程重启：内存缓存与 dirty 标记全部丢失，向量库数据仍在
    retriever._bm25.clear()
    retriever._bm25_docs.clear()
    retriever._dirty.clear()
    docs = retriever.retrieve("操作系统", "kb1", top_k=3)
    assert any("操作系统" in d["text"] for d in docs), "重启后 BM25 应能从向量库自动回灌"


def test_retrieve_wiki_only_returns_wiki_docs():
    """泛化检索：只在 doc_id 带 wiki- 前缀的文档内检索，普通文档不参与"""
    store = FakeVectorStore()
    retriever = HybridRetriever(store, chunker=Chunker(chunk_size=20, overlap=2))
    retriever.add_document("kb1", "wiki-page1", "知识库总览：LangGraph 是编排框架。")
    retriever.add_document("kb1", "doc1", "数据结构课程涵盖链表与栈。")
    docs = retriever.retrieve_wiki("总结知识库内容", "kb1", top_k=5)
    assert docs, "有 Wiki 页面时应召回"
    assert all("数据结构" not in d["text"] for d in docs), "普通文档片段不应进入 wiki 专用检索"


def test_retrieve_wiki_empty_without_wiki():
    """无 Wiki 页面时，wiki 专用检索返回空（调用方回退全库检索）"""
    store = FakeVectorStore()
    retriever = HybridRetriever(store, chunker=Chunker(chunk_size=20, overlap=2))
    retriever.add_document("kb1", "doc1", "数据结构课程涵盖链表与栈。")
    assert retriever.retrieve_wiki("总结知识库内容", "kb1", top_k=5) == []
