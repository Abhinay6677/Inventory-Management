from __future__ import annotations

from typing import Protocol, TypeVar

Documents = list[str]
Embeddings = list[list[float]]

T = TypeVar("T")


class EmbeddingFunction(Protocol[T]):
    def __call__(self, input: T) -> Embeddings: ...
