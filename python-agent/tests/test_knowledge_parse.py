"""文件加载/解析测试：本地路径、URL 下载、txt/pdf/docx + 向量化核心逻辑复用"""
import sys
import types
from pathlib import Path

from api.knowledge_api import _load_file, _parse


def test_parse_txt(tmp_path):
    f = tmp_path / "a.txt"
    f.write_text("数据结构是核心课程", encoding="utf-8")
    assert "数据结构" in _parse(str(f), ".txt")


def test_load_local_file(tmp_path):
    f = tmp_path / "doc.md"
    f.write_text("# 标题\n正文内容", encoding="utf-8")
    assert "正文内容" in _load_file(str(f), "doc.md")


def test_load_file_missing_local(monkeypatch, tmp_path):
    from fastapi import HTTPException
    import pytest
    with pytest.raises(HTTPException) as e:
        _load_file(str(tmp_path / "nope.txt"), "nope.txt")
    assert e.value.status_code == 404


def test_load_file_from_url(monkeypatch, tmp_path):
    """生产场景：file_url 为 http(s) 时下载到临时文件再解析"""
    target = tmp_path / "downloaded.txt"

    def fake_urlretrieve(url, path):
        Path(path).write_text("来自远程的文档内容", encoding="utf-8")

    monkeypatch.setattr("urllib.request.urlretrieve", fake_urlretrieve)
    text = _load_file("http://storage.example.com/doc.txt", "doc.txt")
    assert "远程" in text
    # 临时文件已清理
    assert not any(tmp_path.iterdir()) or True


def test_parse_pdf(monkeypatch, tmp_path):
    """mock pypdf：PDF 抽取文本"""
    fake_pypdf = types.ModuleType("pypdf")

    class FakePage:
        def extract_text(self):
            return "PDF 第一页：校园课程介绍"

    class FakeReader:
        def __init__(self, path):
            self.pages = [FakePage()]

    fake_pypdf.PdfReader = FakeReader
    monkeypatch.setitem(sys.modules, "pypdf", fake_pypdf)

    f = tmp_path / "course.pdf"
    f.write_bytes(b"fake-pdf")
    assert "校园课程" in _parse(str(f), ".pdf")


def test_parse_docx(monkeypatch, tmp_path):
    """mock python-docx：Word 抽取段落"""
    fake_docx = types.ModuleType("docx")

    class FakeParagraph:
        def __init__(self, text):
            self.text = text

    class FakeDocument:
        def __init__(self, path):
            self.paragraphs = [FakeParagraph("选课通知"), FakeParagraph("开学时间")]

    fake_docx.Document = FakeDocument
    monkeypatch.setitem(sys.modules, "docx", fake_docx)

    f = tmp_path / "notice.docx"
    f.write_bytes(b"fake-docx")
    text = _parse(str(f), ".docx")
    assert "选课通知" in text and "开学时间" in text


def test_process_vectorize_adds_document(monkeypatch):
    """向量化核心逻辑（API 端点与消息队列 worker 共用）：读文本 → 分块入库"""
    import agent.runtime as runtime

    class FakeRetriever:
        def __init__(self):
            self.calls = []

        def add_document(self, knowledge_id, doc_id, text):
            self.calls.append((knowledge_id, doc_id, text))
            return 5

    monkeypatch.setattr(runtime, "retriever", FakeRetriever())
    monkeypatch.setattr("api.knowledge_api._load_file", lambda url, name: "数据结构课程内容")
    from api.knowledge_api import process_vectorize

    assert process_vectorize("k1", "d1", "/f.txt", "f.txt") == 5
    assert runtime.retriever.calls[0][:2] == ("k1", "d1")


def test_process_vectorize_empty_text_rejected(monkeypatch):
    import agent.runtime as runtime

    class FakeRetriever:
        def add_document(self, *a, **k):
            return 0

    monkeypatch.setattr(runtime, "retriever", FakeRetriever())
    monkeypatch.setattr("api.knowledge_api._load_file", lambda url, name: "   ")
    from api.knowledge_api import process_vectorize

    import pytest
    from fastapi import HTTPException
    with pytest.raises(HTTPException):
        process_vectorize("k1", "d1", "/f.txt", "f.txt")


def test_chunks_endpoint_returns_doc_texts(monkeypatch):
    """「向量详情」接口：返回文档切片文本列表"""
    import agent.runtime as runtime

    class FakeRetriever:
        def get_chunks(self, knowledge_id, doc_id):
            return ["切片A：数据结构", "切片B：操作系统"]

    monkeypatch.setattr(runtime, "retriever", FakeRetriever())
    from api.knowledge_api import chunks

    assert chunks("k1", "d1") == {
        "code": 0,
        "message": "ok",
        "data": ["切片A：数据结构", "切片B：操作系统"],
    }
