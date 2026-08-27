"""文件加载/解析测试：本地路径白名单、URL 下载（SSRF 防护）、txt/pdf/docx + 向量化核心逻辑复用"""
from pathlib import Path

import pytest
from fastapi import HTTPException

from api.knowledge_api import _load_file, _parse
from config import settings


def _allow_dir(tmp_path, monkeypatch):
    """把文件读取白名单目录指向 tmp_path，模拟生产 FILE_BASE_DIR"""
    monkeypatch.setattr(settings, "file_base_dir", str(tmp_path))


def test_parse_txt(tmp_path):
    f = tmp_path / "a.txt"
    f.write_text("数据结构是核心课程", encoding="utf-8")
    assert "数据结构" in _parse(str(f), ".txt")


def test_load_local_file(tmp_path, monkeypatch):
    _allow_dir(tmp_path, monkeypatch)
    f = tmp_path / "doc.md"
    f.write_text("# 标题\n正文内容", encoding="utf-8")
    assert "正文内容" in _load_file(str(f), "doc.md")


def test_load_file_missing_local(monkeypatch, tmp_path):
    _allow_dir(tmp_path, monkeypatch)
    with pytest.raises(HTTPException) as e:
        _load_file(str(tmp_path / "nope.txt"), "nope.txt")
    assert e.value.status_code == 404


def test_load_local_file_outside_base_denied(tmp_path, monkeypatch):
    """安全回归：本地路径不在 FILE_BASE_DIR 内必须拒绝（防任意文件读取 /proc/self/environ 等）"""
    _allow_dir(tmp_path, monkeypatch)
    outside = tmp_path.parent / "secret.txt"
    outside.write_text("secret", encoding="utf-8")
    with pytest.raises(HTTPException) as e:
        _load_file(str(outside), "secret.txt")
    assert e.value.status_code == 403


def test_load_file_from_url(monkeypatch, tmp_path):
    """生产场景：file_url 为 http(s) 时下载到临时文件再解析（通过 SSRF 检查）"""
    _allow_dir(tmp_path, monkeypatch)

    class FakeResp:
        def __init__(self):
            self._body = "来自远程的文档内容".encode("utf-8")
            self._done = False

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def read(self, n=-1):
            if self._done:
                return b""
            self._done = True
            return self._body

    monkeypatch.setattr("urllib.request.urlopen", lambda req, timeout: FakeResp())
    # 放行 SSRF 检查（模拟目标已解析为公网地址）
    monkeypatch.setattr("api.knowledge_api._is_private_target", lambda url: False)
    text = _load_file("http://storage.example.com/doc.txt", "doc.txt")
    assert "远程" in text
