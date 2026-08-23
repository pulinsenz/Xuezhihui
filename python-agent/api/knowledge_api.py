"""
知识库接口：文档向量化入库 / 删除向量（被 Java 调用）
"""
import tempfile
import urllib.request
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from agent import runtime
from utils.logger_util import get_logger, agent_event

logger = get_logger("knowledge_api")

router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])

# 支持的文本文件类型
TEXT_EXTS = {".txt", ".md", ".markdown", ".csv", ".json", ".html", ".xml"}


class VectorizeRequest(BaseModel):
    knowledge_id: str
    doc_id: str
    file_url: str
    name: str = ""


class DeleteRequest(BaseModel):
    knowledge_id: str
    doc_id: Optional[str] = None


def _load_file(file_url: str, name: str) -> str:
    """加载文件文本：支持本地路径（开发）与 http(s) URL（生产 COS/对象存储）"""
    ext = Path(name).suffix.lower()
    local_path = file_url
    downloaded = file_url.startswith(("http://", "https://"))
    if downloaded:
        local_path = _download(file_url)
    elif not Path(file_url).exists():
        raise HTTPException(status_code=404, detail=f"文件不存在: {file_url}")
    try:
        return _parse(local_path, ext)
    finally:
        if downloaded:
            Path(local_path).unlink(missing_ok=True)


def _download(url: str) -> str:
    """下载远程文件到临时文件"""
    tmp = tempfile.NamedTemporaryFile(suffix=".download", delete=False)
    tmp.close()
    try:
        urllib.request.urlretrieve(url, tmp.name)
    except Exception as e:
        Path(tmp.name).unlink(missing_ok=True)
        raise HTTPException(status_code=502, detail=f"下载文件失败: {e}") from e
    return tmp.name


def _parse(path: str, ext: str) -> str:
    """按文件类型解析文本"""
    if ext in TEXT_EXTS:
        return Path(path).read_text(encoding="utf-8", errors="ignore")
    if ext == ".pdf":
        try:
            from pypdf import PdfReader
            reader = PdfReader(path)
            return "\n".join(page.extract_text() or "" for page in reader.pages)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"PDF 解析失败: {e}") from e
    if ext in (".docx",):
        try:
            from docx import Document
            doc = Document(path)
            return "\n".join(p.text for p in doc.paragraphs if p.text)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Word 解析失败: {e}") from e
    # 其他格式：尝试 utf-8 解码
    return Path(path).read_bytes().decode("utf-8", errors="ignore")


def process_vectorize(knowledge_id: str, doc_id: str, file_url: str, name: str) -> int:
    """向量化入库核心逻辑（API 端点与消息队列 worker 共用）：
    读文件 → 分块 → BM25 + 向量双索引，返回 chunk 数"""
    text = _load_file(file_url, name)
    if not text.strip():
        raise HTTPException(status_code=400, detail="文件内容为空")
    chunk_count = runtime.retriever.add_document(knowledge_id, doc_id, text)
    agent_event(logger, "vectorized", knowledge_id=knowledge_id,
                doc_id=doc_id, chunks=chunk_count)
    return chunk_count


@router.post("/vectorize")
def vectorize(req: VectorizeRequest):
    """文档向量化入库：读文件 → 分块 → BM25 + 向量双索引（保留给 Java 同步调用场景）"""
    try:
        chunk_count = process_vectorize(req.knowledge_id, req.doc_id, req.file_url, req.name)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"读取文件失败: {e}") from e
    return {"code": 0, "message": "ok", "data": {"chunks": chunk_count}}


@router.post("/delete")
def delete(req: DeleteRequest):
    """删除向量：指定 doc_id 删单个文档，否则删整个知识库"""
    if req.doc_id:
        runtime.vector_store.delete_document(req.knowledge_id, req.doc_id)
    else:
        runtime.vector_store.delete_knowledge(req.knowledge_id)
    agent_event(logger, "vectors_deleted", knowledge_id=req.knowledge_id, doc_id=req.doc_id)
    return {"code": 0, "message": "ok", "data": True}
