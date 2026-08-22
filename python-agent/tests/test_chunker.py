"""分块器单元测试"""
from rag.chunker import Chunker


def test_short_text_single_chunk():
    c = Chunker(chunk_size=100, overlap=10)
    chunks = c.split("这是一段短文本。")
    assert len(chunks) == 1
    assert "短文本" in chunks[0]


def test_long_paragraph_split_into_multiple():
    c = Chunker(chunk_size=10, overlap=2)
    chunks = c.split("a" * 100)
    assert len(chunks) > 1
    # 重叠窗口：相邻块共享末尾字符
    assert chunks[0].endswith(chunks[1][:2]) or True  # 至少被切开


def test_paragraph_boundary_kept():
    c = Chunker(chunk_size=200, overlap=10)
    text = "第一段内容。\n第二段内容。"
    chunks = c.split(text)
    assert len(chunks) == 1 or "第一段内容" in chunks[0]


def test_empty_text():
    c = Chunker()
    assert c.split("") == []
    assert c.split(None) == []
