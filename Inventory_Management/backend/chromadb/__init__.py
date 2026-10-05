from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any


def _tokenize(text: str) -> list[str]:
    token = []
    tokens: list[str] = []
    for ch in text.lower():
        if ch.isalnum() or ch == "_":
            token.append(ch)
        elif token:
            tokens.append("".join(token))
            token = []
    if token:
        tokens.append("".join(token))
    return tokens


def _hash_embed(text: str, dims: int = 128) -> list[float]:
    vec = [0.0] * dims
    for tok in _tokenize(text):
        h = hashlib.sha256(tok.encode("utf-8")).digest()
        idx = int.from_bytes(h[:4], "big") % dims
        vec[idx] += 1.0 if h[4] % 2 == 0 else -1.0
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [v / norm for v in vec]


def _cosine_distance(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    return max(0.0, 1.0 - dot)


@dataclass
class _CollectionInfo:
    name: str


class Collection:
    def __init__(self, path: Path, name: str, embedding_function: Any = None, metadata: dict[str, Any] | None = None):
        self._path = path
        self.name = name
        self.embedding_function = embedding_function
        self.metadata = metadata or {}
        self._file = self._path / f"{name}.json"
        self._ensure_loaded()

    def _ensure_loaded(self) -> None:
        self._path.mkdir(parents=True, exist_ok=True)
        if not self._file.exists():
            self._file.write_text(json.dumps({"ids": [], "documents": [], "metadatas": []}), encoding="utf-8")

    def _load(self) -> dict[str, Any]:
        self._ensure_loaded()
        return json.loads(self._file.read_text(encoding="utf-8"))

    def _save(self, payload: dict[str, Any]) -> None:
        self._file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    def add(self, ids: list[str], documents: list[str], metadatas: list[dict[str, Any]] | None = None) -> None:
        data = self._load()
        metadatas = metadatas or [{} for _ in documents]
        id_map = {existing_id: i for i, existing_id in enumerate(data["ids"])}
        for i, doc_id in enumerate(ids):
            meta = metadatas[i] if i < len(metadatas) else {}
            if doc_id in id_map:
                idx = id_map[doc_id]
                data["documents"][idx] = documents[i]
                data["metadatas"][idx] = meta
            else:
                data["ids"].append(doc_id)
                data["documents"].append(documents[i])
                data["metadatas"].append(meta)
        self._save(data)

    def get(self, include: list[str] | None = None) -> dict[str, Any]:
        data = self._load()
        include = include or ["documents", "metadatas"]
        out = {"ids": data["ids"]}
        if "documents" in include:
            out["documents"] = data["documents"]
        if "metadatas" in include:
            out["metadatas"] = data["metadatas"]
        return out

    def delete(self, ids: list[str]) -> None:
        data = self._load()
        keep = [i for i, doc_id in enumerate(data["ids"]) if doc_id not in ids]
        data["ids"] = [data["ids"][i] for i in keep]
        data["documents"] = [data["documents"][i] for i in keep]
        data["metadatas"] = [data["metadatas"][i] for i in keep]
        self._save(data)

    def count(self) -> int:
        return len(self._load()["ids"])

    def query(self, query_texts: list[str], n_results: int = 4) -> dict[str, Any]:
        data = self._load()
        docs = data["documents"]
        metas = data["metadatas"]
        if not query_texts:
            return {"documents": [[]], "metadatas": [[]], "distances": [[]]}

        q = query_texts[0]
        if self.embedding_function:
            qv = self.embedding_function([q])[0]
            dvs = self.embedding_function(docs) if docs else []
        else:
            qv = _hash_embed(q)
            dvs = [_hash_embed(d) for d in docs]

        scored = []
        for i, dv in enumerate(dvs):
            scored.append((i, _cosine_distance(qv, dv)))
        scored.sort(key=lambda x: x[1])

        chosen = scored[: max(0, n_results)]
        out_docs = [docs[i] for i, _ in chosen]
        out_metas = [metas[i] for i, _ in chosen]
        out_dists = [dist for _, dist in chosen]

        return {
            "documents": [out_docs],
            "metadatas": [out_metas],
            "distances": [out_dists],
        }


class PersistentClient:
    def __init__(self, path: str = "./chroma_db") -> None:
        self._path = Path(path)
        self._path.mkdir(parents=True, exist_ok=True)

    def list_collections(self) -> list[_CollectionInfo]:
        names = [p.stem for p in self._path.glob("*.json")]
        return [_CollectionInfo(name=n) for n in sorted(names)]

    def get_or_create_collection(self, name: str, metadata: dict[str, Any] | None = None, embedding_function: Any = None) -> Collection:
        return Collection(self._path, name=name, embedding_function=embedding_function, metadata=metadata)

    def get_collection(self, name: str) -> Collection:
        file_path = self._path / f"{name}.json"
        if not file_path.exists():
            raise ValueError(f"Collection not found: {name}")
        return Collection(self._path, name=name)
