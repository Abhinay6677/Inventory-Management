from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class Document:
    page_content: str
    metadata: dict[str, Any]


class TextLoader:
    def __init__(self, file_path: str, encoding: str = "utf-8") -> None:
        self.file_path = file_path
        self.encoding = encoding

    def load(self) -> list[Document]:
        path = Path(self.file_path)
        text = path.read_text(encoding=self.encoding)
        return [Document(page_content=text, metadata={"source": str(path)})]
