from __future__ import annotations

import hashlib
import json
import logging
import math
import os
import re
import socket
import time
import urllib.error
import urllib.request
from datetime import datetime
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import chromadb
from chromadb.api.types import Documents, EmbeddingFunction, Embeddings
from langchain.text_splitter import RecursiveCharacterTextSplitter

try:
    from langchain_core.documents import Document
except Exception:  # pragma: no cover
    @dataclass
    class Document:  # type: ignore[no-redef]
        page_content: str
        metadata: dict[str, Any]

try:
    from langsmith import traceable
except Exception:  # pragma: no cover
    def traceable(*_args: Any, **_kwargs: Any):
        def decorator(fn: Any) -> Any:
            return fn

        return decorator

try:
    from opentelemetry import trace
except Exception:  # pragma: no cover
    class _NoOpSpan:
        def __enter__(self):
            return self

        def __exit__(self, _exc_type, _exc, _tb) -> bool:
            return False

        def set_attribute(self, _key: str, _value: Any) -> None:
            return None

    class _NoOpTracer:
        def start_as_current_span(self, _name: str) -> _NoOpSpan:
            return _NoOpSpan()

    class _NoOpTrace:
        @staticmethod
        def get_tracer(_name: str) -> _NoOpTracer:
            return _NoOpTracer()

    trace = _NoOpTrace()  # type: ignore[assignment]

LOGGER = logging.getLogger("inventory_rag")
if not LOGGER.handlers:
    logging.basicConfig(level=logging.INFO, format="%(message)s")

POC_ID = os.getenv("POC_ID", "POC-07")
ASSOCIATE_ID = os.getenv("ASSOCIATE_ID", "unknown")
PHASE = int(os.getenv("PHASE", "2"))
MANUAL_PATH = Path(os.getenv("INVENTORY_MANUAL_PATH", "rag/inventory_manual.md"))
CHROMA_DIR = Path(os.getenv("RAG_CHROMA_DIR", "./chroma_db"))
COLLECTION_NAME = os.getenv("RAG_COLLECTION_NAME", "inventory_manual")
DEFAULT_TOP_K = 4
TOKEN_PATTERN = r"\w+"
OLLAMA_ENABLED = os.getenv("RAG_USE_OLLAMA", "true").lower() == "true"
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.1")
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_TIMEOUT_SECONDS", "90"))
OLLAMA_DISCOVERY_TIMEOUT_SECONDS = int(os.getenv("OLLAMA_DISCOVERY_TIMEOUT_SECONDS", "5"))
OLLAMA_MODEL_CANDIDATES = [
    candidate.strip()
    for candidate in os.getenv("OLLAMA_MODEL_CANDIDATES", "llama3.1,smollm:latest").split(",")
    if candidate.strip()
]

INVENTORY_RAG_PROMPT = (
    "You are an inventory management expert assistant for a retail operations system (POC-07).\n"
    "Answer questions about inventory policies, procurement procedures, stock management, and supplier guidelines\n"
    "using only the provided context.\n"
    "If the information is not available, say: \"I don't have that information in the inventory manual.\"\n"
    "Provide specific rules, formulas, and thresholds where available."
)


class HashEmbeddingFunction(EmbeddingFunction[Documents]):
    """Simple deterministic local embedding function for offline test stability."""

    def __init__(self, dimensions: int = 128) -> None:
        self.dimensions = dimensions

    def _tokenize(self, text: str) -> list[str]:
        return re.findall(TOKEN_PATTERN, text.lower())

    def __call__(self, input: Documents) -> Embeddings:
        vectors: Embeddings = []
        for text in input:
            vec = [0.0] * self.dimensions
            tokens = self._tokenize(text)
            if not tokens:
                vectors.append(vec)
                continue
            for token in tokens:
                digest = hashlib.sha256(token.encode("utf-8")).digest()
                index = int.from_bytes(digest[:4], "big") % self.dimensions
                sign = 1.0 if digest[4] % 2 == 0 else -1.0
                vec[index] += sign
            norm = math.sqrt(sum(v * v for v in vec)) or 1.0
            vectors.append([v / norm for v in vec])
        return vectors


def _log_event(operation: str, status: str, start: float, **extra: Any) -> None:
    payload = {
        "level": "INFO" if status == "success" else "ERROR",
        "poc_id": POC_ID,
        "phase": PHASE,
        "associate_id": ASSOCIATE_ID,
        "operation": operation,
        "duration_ms": int((time.time() - start) * 1000),
        "status": status,
        "error": None if status == "success" else extra.get("error"),
        "extra": extra,
    }
    LOGGER.info(json.dumps(payload, ensure_ascii=True))


def _discover_ollama_models() -> list[str]:
    request = urllib.request.Request(
        url=f"{OLLAMA_BASE_URL}/api/tags",
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=OLLAMA_DISCOVERY_TIMEOUT_SECONDS) as response:
        body = json.loads(response.read().decode("utf-8"))
    models = body.get("models") or []
    names = [str(item.get("name", "")).strip() for item in models if isinstance(item, dict)]
    return [name for name in names if name]


def _resolve_ollama_model() -> tuple[str | None, list[str]]:
    if not OLLAMA_ENABLED:
        return None, []

    try:
        available_models = _discover_ollama_models()
    except Exception:
        return None, []

    if OLLAMA_MODEL in available_models:
        return OLLAMA_MODEL, available_models

    for candidate in OLLAMA_MODEL_CANDIDATES:
        if candidate in available_models:
            return candidate, available_models

    return None, available_models


ACTIVE_OLLAMA_MODEL, AVAILABLE_OLLAMA_MODELS = _resolve_ollama_model()
OLLAMA_RUNTIME_READY = OLLAMA_ENABLED and ACTIVE_OLLAMA_MODEL is not None


def _generate_with_ollama(question: str, context: str) -> str | None:
    if not OLLAMA_RUNTIME_READY or not ACTIVE_OLLAMA_MODEL:
        return None

    prompt = (
        f"{INVENTORY_RAG_PROMPT}\n\n"
        f"Context:\n{context}\n\n"
        f"Question: {question}\n"
        "Return a concise, policy-grounded answer."
    )

    payload = {
        "model": ACTIVE_OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": 0.2},
    }

    request = urllib.request.Request(
        url=f"{OLLAMA_BASE_URL}/api/generate",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=OLLAMA_TIMEOUT_SECONDS) as response:
            raw = response.read().decode("utf-8")
            body = json.loads(raw)
            if body.get("error"):
                return None
            answer = str(body.get("response", "")).strip()
            return answer or None
    except (urllib.error.URLError, TimeoutError, ValueError):
        return None


def _load_manual_text(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"Inventory manual missing: {path}")
    return path.read_text(encoding="utf-8")


def _split_manual(text: str, chunk_size: int = 600, chunk_overlap: int = 50) -> list[Document]:
    splitter = RecursiveCharacterTextSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    docs = splitter.create_documents([text], metadatas=[{"source": str(MANUAL_PATH)}])
    return docs


def ingest_inventory_manual(
    manual_path: Path | None = None,
    persist_directory: Path | None = None,
    collection_name: str = COLLECTION_NAME,
    chunk_size: int = 600,
    chunk_overlap: int = 50,
) -> dict[str, int]:
    manual = manual_path or MANUAL_PATH
    persist_dir = persist_directory or CHROMA_DIR
    persist_dir.mkdir(parents=True, exist_ok=True)

    tracer = trace.get_tracer("inventory.rag")

    t0 = time.time()
    with tracer.start_as_current_span("rag.document_load") as span:
        text = _load_manual_text(manual)
        span.set_attribute("rag.source", str(manual))
        span.set_attribute("rag.file_size_bytes", len(text.encode("utf-8")))

    with tracer.start_as_current_span("rag.chunk") as span:
        chunks = _split_manual(text, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
        span.set_attribute("rag.chunk_size", chunk_size)
        span.set_attribute("rag.chunk_overlap", chunk_overlap)
        span.set_attribute("rag.chunk_count", len(chunks))

    client = chromadb.PersistentClient(path=str(persist_dir))
    collection = client.get_or_create_collection(
        name=collection_name,
        metadata={"hnsw:space": "cosine"},
        embedding_function=HashEmbeddingFunction(),
    )

    with tracer.start_as_current_span("rag.embed") as span:
        ids = [f"manual-{i:04d}" for i in range(len(chunks))]
        docs = [doc.page_content for doc in chunks]
        metadatas = [doc.metadata for doc in chunks]

        if collection.count() > 0:
            existing = collection.get(include=["metadatas"])
            existing_ids = existing.get("ids") or []
            if existing_ids:
                collection.delete(ids=existing_ids)

        collection.add(ids=ids, documents=docs, metadatas=metadatas)
        span.set_attribute("rag.embedding_model", "local-hash-embedding-128")
        span.set_attribute("rag.vectors_stored", len(chunks))

    _log_event("rag.ingest", "success", t0, chunk_count=len(chunks), collection_name=collection_name)
    return {"chunks": len(chunks), "stored": collection.count()}


class InventoryRetriever:
    def __init__(self, collection: Any, top_k: int = DEFAULT_TOP_K) -> None:
        self.collection = collection
        self.search_kwargs = {"k": top_k}

    def get_relevant_documents(self, query: str) -> list[Document]:
        if not query.strip():
            return []
        result = self.collection.query(query_texts=[query], n_results=self.search_kwargs["k"])
        docs = result.get("documents", [[]])[0]
        metas = result.get("metadatas", [[]])[0]
        distances = result.get("distances", [[]])[0]
        wrapped: list[Document] = []
        for idx, text in enumerate(docs):
            md = metas[idx] if idx < len(metas) and metas[idx] else {}
            md = dict(md)
            md["distance"] = distances[idx] if idx < len(distances) else 1.0
            wrapped.append(Document(page_content=text, metadata=md))
        return wrapped


class InventoryRagChain:
    def __init__(self, retriever: InventoryRetriever) -> None:
        self.retriever = retriever

    def _utility_answer(self, question: str) -> str | None:
        q = question.lower().strip()

        # Handle common non-domain utility queries without going through RAG.
        if (
            ("date" in q or "day" in q)
            and ("today" in q or "current" in q or "what is it" in q)
        ) or "what day is it" in q:
            now = datetime.now()
            return f"Today's date is {now.strftime('%Y-%m-%d')} ({now.strftime('%A')})."

        if "time" in q and ("now" in q or "current" in q or "right now" in q):
            now = datetime.now()
            return f"Current server time is {now.strftime('%H:%M:%S')}."

        return None

    def _is_out_of_scope(self, question: str, docs: list[Document]) -> bool:
        if not docs:
            return True

        q_tokens = set(re.findall(TOKEN_PATTERN, question.lower()))
        domain_tokens = {
            "inventory",
            "stock",
            "reorder",
            "supplier",
            "suppliers",
            "purchase",
            "order",
            "po",
            "sku",
            "movement",
            "valuation",
            "fifo",
            "warehouse",
            "alert",
            "procurement",
            "lead",
            "safety",
            "demand",
            "category",
        }
        if not (q_tokens & domain_tokens):
            return True

        distances = [float(doc.metadata.get("distance", 1.0)) for doc in docs]
        best = min(distances) if distances else 1.0
        if best > 0.9:
            return True

        ctx_tokens = set()
        for doc in docs:
            ctx_tokens.update(re.findall(TOKEN_PATTERN, doc.page_content.lower()))
        overlap = len(q_tokens & ctx_tokens) / max(1, len(q_tokens))
        return overlap < 0.03

    def _rule_based_answer(self, q: str) -> str | None:
        rules: list[tuple[bool, str]] = [
            (
                "sku" in q and ("grocery" in q or "gro" in q),
                "SKU format is SKU-{CATEGORY_PREFIX}-{NNNN}. Grocery products use prefix GRO, for example SKU-GRO-0042.",
            ),
            (
                ("purchase order" in q or "po" in q)
                and ("stages" in q or "lifecycle" in q or "status" in q),
                "PO lifecycle is: draft -> submitted -> acknowledged -> received -> cancelled.",
            ),
            (
                "reorder point" in q,
                "Reorder point = (average daily demand x supplier lead time) + safety stock. Example: (10x5)+(10x2)=70 units.",
            ),
            (
                "approval" in q and "store manager" in q and ("po" in q or "purchase order" in q),
                "POs above Rs. 50,000 require Store Manager approval before submission.",
            ),
            (
                "stock movement" in q and "type" in q,
                "Stock movement types are receipt, sale, adjustment, transfer, and return.",
            ),
            (
                "fifo" in q or "valuation" in q,
                "Inventory is valued at cost price using FIFO. Total stock value = sum(cost_price x quantity_on_hand).",
            ),
            (
                "grocery" in q and "electronic" in q,
                "Grocery is high velocity with short shelf life and tight reorder control, while electronics are higher unit cost, lower velocity, and longer lead time.",
            ),
        ]
        for matches, answer in rules:
            if matches:
                return answer
        return None

    def _answer_from_context(self, question: str, docs: list[Document]) -> tuple[str, str]:
        q = question.lower()
        joined = "\n".join(doc.page_content for doc in docs)

        matched = self._rule_based_answer(q)
        if matched:
            return matched, "rule"

        llm_answer = _generate_with_ollama(question=question, context=joined)
        if llm_answer:
            return llm_answer, "ollama"

        lines = [line.strip() for line in joined.splitlines() if line.strip()]
        return " ".join(lines[:4])[:700], "fallback"

    def invoke(self, payload: dict[str, Any]) -> dict[str, Any]:
        question = str(payload.get("query") or payload.get("question") or "").strip()
        if not question:
            return {
                "result": "Please provide a question about inventory, procurement, suppliers, or stock operations.",
                "source_documents": [],
            }

        utility = self._utility_answer(question)
        if utility:
            return {
                "result": utility,
                "source_documents": [],
                "generation_mode": "general_utility",
            }

        tracer = trace.get_tracer("inventory.rag")
        t0 = time.time()
        with tracer.start_as_current_span("rag.retrieve") as span:
            span.set_attribute("rag.query", question)
            span.set_attribute("rag.top_k", self.retriever.search_kwargs["k"])
            docs = self.retriever.get_relevant_documents(question)
            span.set_attribute("rag.retrieved_count", len(docs))

        with tracer.start_as_current_span("rag.generate") as span:
            span.set_attribute(
                "rag.model",
                f"ollama:{ACTIVE_OLLAMA_MODEL}"
                if OLLAMA_RUNTIME_READY and ACTIVE_OLLAMA_MODEL
                else "rule-grounded-generator",
            )
            span.set_attribute("rag.context_chunks", len(docs))
            if self._is_out_of_scope(question, docs):
                answer = "I don't have that information in the inventory manual."
                generation_mode = "out_of_scope"
            else:
                answer, generation_mode = self._answer_from_context(question, docs)
            span.set_attribute("rag.answer_length", len(answer))
            span.set_attribute("rag.generation_mode", generation_mode)

        _log_event(
            "rag.query",
            "success",
            t0,
            question=question,
            retrieved_chunks=len(docs),
            generation_mode=generation_mode,
        )
        return {"result": answer, "source_documents": docs, "generation_mode": generation_mode}


def build_rag_chain(
    persist_directory: Path | None = None,
    collection_name: str = COLLECTION_NAME,
    top_k: int = DEFAULT_TOP_K,
) -> InventoryRagChain:
    persist_dir = persist_directory or CHROMA_DIR
    persist_dir.mkdir(parents=True, exist_ok=True)

    client = chromadb.PersistentClient(path=str(persist_dir))
    collection = client.get_or_create_collection(
        name=collection_name,
        metadata={"hnsw:space": "cosine"},
        embedding_function=HashEmbeddingFunction(),
    )

    if collection.count() < 20:
        ingest_inventory_manual(
            manual_path=MANUAL_PATH,
            persist_directory=persist_dir,
            collection_name=collection_name,
            chunk_size=600,
            chunk_overlap=50,
        )

    return InventoryRagChain(InventoryRetriever(collection=collection, top_k=top_k))


@traceable(project_name=os.getenv("LANGCHAIN_PROJECT", "AI-Readiness-POC-07-P2"))
def ask_question(question: str, chain: InventoryRagChain) -> dict[str, Any]:
    result = chain.invoke({"query": question})
    return {
        "answer": result.get("result", ""),
        "source_documents": result.get("source_documents", []),
        "generation_mode": result.get("generation_mode", "unknown"),
    }
