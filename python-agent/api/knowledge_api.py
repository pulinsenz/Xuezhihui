"""
知识库接口：文档向量化入库 / 删除向量（被 Java 调用）
"""
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


def _read_file(file_url: str, name: str) -> str:
    """读取本地文件文本（LocalFileStorageService 存的是绝对路径）"""
    path = Path(file_url)
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"文件不存在: {file_url}")
    ext = Path(name).suffix.lower()
    if ext in TEXT_EXTS:
        return path.read_text(encoding="utf-8", errors="ignore")
    # 其他格式（pdf/docx 等）：先尝试按 utf-8 解码文本，未支持格式由上层提示
    data = path.read_bytes()
    return data.decode("utf-8", errors="ignore")


@router.post("/vectorize")
def vectorize(req: VectorizeRequest):
    """文档向量化入库：读文件 → 分块 → BM25 + 向量双索引"""
    try:
        text = _read_file(req.file_url, req.name)
    except HTTPException:
        raise
    if not text.strip():
        raise HTTPException(status_code=400, detail="文件内容为空")
    chunk_count = runtime.retriever.add_document(req.knowledge_id, req.doc_id, text)
    agent_event(logger, "vectorized", knowledge_id=req.knowledge_id,
                doc_id=req.doc_id, chunks=chunk_count)
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
