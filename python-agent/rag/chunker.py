"""
文档分块：段落优先 + 长度限制 + 重叠窗口。
"""
import re
from typing import List


class Chunker:
    """文本分块器"""

    def __init__(self, chunk_size: int = 500, overlap: int = 50):
        self.chunk_size = chunk_size
        self.overlap = overlap

    def split(self, text: str) -> List[str]:
        text = (text or "").strip()
        if not text:
            return []
        # 1. 按空行/换行切成段落
        paragraphs = [p.strip() for p in re.split(r"\n\s*\n|\n", text) if p.strip()]
        chunks: List[str] = []
        current = ""

        for para in paragraphs:
            if len(para) > self.chunk_size:
                # 超长段落：先收尾当前块，再按长度切（带重叠）
                if current:
                    chunks.append(current)
                    current = ""
                for i in range(0, len(para), self.chunk_size - self.overlap):
                    chunks.append(para[i:i + self.chunk_size])
            elif len(current) + len(para) + 1 <= self.chunk_size:
                current = f"{current}\n{para}" if current else para
            else:
                chunks.append(current)
                current = para

        if current:
            chunks.append(current)
        return chunks or ([text] if text else [])
