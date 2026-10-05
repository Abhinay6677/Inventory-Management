from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class _SimpleDocument:
    page_content: str
    metadata: dict[str, Any]


class RecursiveCharacterTextSplitter:
    def __init__(self, chunk_size: int = 600, chunk_overlap: int = 50) -> None:
        if chunk_size <= 0:
            raise ValueError("chunk_size must be > 0")
        if chunk_overlap < 0:
            raise ValueError("chunk_overlap must be >= 0")
        if chunk_overlap >= chunk_size:
            raise ValueError("chunk_overlap must be less than chunk_size")
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap

    def split_text(self, text: str) -> list[str]:
        if not text:
            return []

        chunks: list[str] = []
        step = self.chunk_size - self.chunk_overlap
        start = 0
        text_len = len(text)

        while start < text_len:
            end = min(start + self.chunk_size, text_len)
            chunk = text[start:end]
            chunks.append(chunk)
            if end >= text_len:
                break
            start += step

        return chunks

    def create_documents(self, texts: list[str], metadatas: list[dict[str, Any]] | None = None) -> list[_SimpleDocument]:
        docs: list[_SimpleDocument] = []
        for idx, text in enumerate(texts):
            metadata = metadatas[idx] if metadatas and idx < len(metadatas) else {}
            for chunk in self.split_text(text):
                docs.append(_SimpleDocument(page_content=chunk, metadata=dict(metadata)))
        return docs

    def split_documents(self, docs: list[_SimpleDocument]) -> list[_SimpleDocument]:
        output: list[_SimpleDocument] = []
        for doc in docs:
            for chunk in self.split_text(doc.page_content):
                output.append(_SimpleDocument(page_content=chunk, metadata=dict(doc.metadata)))
        return output
