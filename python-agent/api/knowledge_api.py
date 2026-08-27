"""
知识库接口：文档向量化入库 / 删除向量（被 Java 调用）

安全约束（防任意文件读取 / SSRF）：
  - 本地路径必须位于 FILE_BASE_DIR 之内，否则拒绝（防读取 /proc/self/environ、/app/.env 等任意文件）；
  - 远程仅 http(s)，且目标解析地址禁止内网/回环/链路本地/云元数据，并限制下载大小。
"""
import ipaddress
import socket
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from agent import runtime
from config import settings
from utils.logger_util import get_logger, agent_event

logger = get_logger("knowledge_api")

router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])

# 支持的文本文件类型
TEXT_EXTS = {".txt", ".md", ".markdown", ".csv", ".json", ".html", ".xml"}

# 远程下载大小上限：与 Java 上传上限(10MB)对齐，防无限下载拖垮服务
MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024


class VectorizeRequest(BaseModel):
    knowledge_id: str
    doc_id: str
    file_url: str
    name: str = ""


class DeleteRequest(BaseModel):
    knowledge_id: str
    doc_id: Optional[str] = None


def _load_file(file_url: str, name: str) -> str:
    """加载文件文本：支持本地路径（共享卷）与 http(s) URL（COS/对象存储）"""
    ext = Path(name).suffix.lower()
    local_path = file_url
    downloaded = file_url.startswith(("http://", "https://"))
    if downloaded:
        local_path = _download(file_url)
    else:
        _check_local_path(file_url)
        if not Path(file_url).exists():
            raise HTTPException(status_code=404, detail=f"文件不存在: {file_url}")
    try:
        return _parse(local_path, ext)
    finally:
        if downloaded:
            Path(local_path).unlink(missing_ok=True)


def _check_local_path(file_url: str) -> None:
    """本地文件读取必须位于 FILE_BASE_DIR 之内，防止读取任意系统文件（/proc/self/environ、/app/.env 等）"""
    base = Path(settings.file_base_dir).resolve()
    target = Path(file_url).resolve()
    if not target.is_relative_to(base):
        raise HTTPException(status_code=403, detail="文件不在允许目录内")


def _download(url: str) -> str:
    """下载远程文件到临时文件：SSRF 防护（拒内网/回环/元数据）+ 下载大小上限"""
    if _is_private_target(url):
        raise HTTPException(status_code=403, detail="不允许访问内网/回环地址")
    tmp = tempfile.NamedTemporaryFile(suffix=".download", delete=False)
    tmp.close()
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "xuezhihui-agent/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            with open(tmp.name, "wb") as out:
                total = 0
                while True:
                    chunk = resp.read(64 * 1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > MAX_DOWNLOAD_BYTES:
                        raise HTTPException(status_code=413, detail="文件超过下载大小上限")
                    out.write(chunk)
    except HTTPException:
        Path(tmp.name).unlink(missing_ok=True)
        raise
    except Exception as e:
        Path(tmp.name).unlink(missing_ok=True)
        raise HTTPException(status_code=502, detail=f"下载文件失败: {e}") from e
    return tmp.name


def _is_private_target(url: str) -> bool:
    """解析主机，命中内网/回环/链路本地/多播/保留地址即拒绝（防 SSRF 打内网与云元数据）"""
    try:
        host = urllib.parse.urlparse(url).hostname
    except ValueError:
        return True
    if not host:
        return True
    try:
        infos = socket.getaddrinfo(host, None)
    except OSError:
        # 解析失败视为不可达，直接拒绝，避免误放行
        return True
    for info in infos:
        try:
            ip = ipaddress.ip_address(info[4][0])
        except ValueError:
            continue
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved:
            return True
    return False


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
    """删除向量：指定 doc_id 删单个文档，否则删整个知识库（同时清理 BM25 索引）"""
    if req.doc_id:
        runtime.retriever.delete_document(req.knowledge_id, req.doc_id)
    else:
        runtime.retriever.delete_knowledge(req.knowledge_id)
    agent_event(logger, "vectors_deleted", knowledge_id=req.knowledge_id, doc_id=req.doc_id)
    return {"code": 0, "message": "ok", "data": True}


@router.get("/chunks")
def chunks(knowledge_id: str, doc_id: str):
    """查询文档切片文本列表（「向量详情」，Java 代理调用）"""
    texts = runtime.retriever.get_chunks(knowledge_id, doc_id)
    return {"code": 0, "message": "ok", "data": texts}
