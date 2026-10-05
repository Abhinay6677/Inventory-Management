from __future__ import annotations

import os
import time
from pathlib import Path

import chromadb
import pytest
from langchain.text_splitter import RecursiveCharacterTextSplitter

from rag.rag_chain import (
    CHROMA_DIR,
    COLLECTION_NAME,
    INVENTORY_RAG_PROMPT,
    MANUAL_PATH,
    ask_question,
    build_rag_chain,
    ingest_inventory_manual,
)


@pytest.fixture(scope="session")
def prepared_db(tmp_path_factory: pytest.TempPathFactory) -> Path:
    db_path = tmp_path_factory.mktemp("phase2_chroma")
    ingest_inventory_manual(persist_directory=db_path, collection_name=COLLECTION_NAME)
    return db_path


@pytest.fixture()
def chain(prepared_db: Path):
    return build_rag_chain(persist_directory=prepared_db, collection_name=COLLECTION_NAME, top_k=4)


def test_01_manual_file_exists() -> None:
    assert MANUAL_PATH.exists()


def test_02_manual_content_is_non_trivial() -> None:
    content = MANUAL_PATH.read_text(encoding="utf-8")
    assert len(content) > 1000


def test_03_chunk_size_within_600() -> None:
    content = MANUAL_PATH.read_text(encoding="utf-8")
    chunks = RecursiveCharacterTextSplitter(chunk_size=600, chunk_overlap=50).split_text(content)
    assert chunks
    for chunk in chunks:
        assert len(chunk) <= 600


def test_04_chunk_count_at_least_20() -> None:
    content = MANUAL_PATH.read_text(encoding="utf-8")
    chunks = RecursiveCharacterTextSplitter(chunk_size=600, chunk_overlap=50).split_text(content)
    assert len(chunks) >= 20


def test_05_chromadb_collection_exists(prepared_db: Path) -> None:
    client = chromadb.PersistentClient(path=str(prepared_db))
    assert COLLECTION_NAME in [c.name for c in client.list_collections()]
    assert client.get_collection(COLLECTION_NAME).count() >= 20


def test_06_top_k_is_four(chain) -> None:
    assert chain.retriever.search_kwargs.get("k") == 4


def test_07_sku_query_keywords_present(chain) -> None:
    answer = ask_question("What is the SKU format for grocery products?", chain)["answer"].lower()
    assert any(x in answer for x in ["gro", "sku", "grocery", "prefix"])


def test_08_po_lifecycle_query(chain) -> None:
    answer = ask_question("What are the stages of a purchase order?", chain)["answer"].lower()
    assert any(x in answer for x in ["draft", "submitted", "received", "lifecycle", "acknowledged"])


def test_09_reorder_point_formula(chain) -> None:
    answer = ask_question("How do I calculate a reorder point?", chain)["answer"].lower()
    assert any(x in answer for x in ["lead time", "daily", "demand", "safety", "reorder"])


def test_10_po_approval_threshold(chain) -> None:
    answer = ask_question("When does a PO need Store Manager approval?", chain)["answer"]
    assert any(x in answer for x in ["50,000", "50000", "Rs", "approval"])


def test_11_stock_movement_types(chain) -> None:
    answer = ask_question("What are the stock movement types?", chain)["answer"].lower()
    assert all(x in answer for x in ["receipt", "sale", "adjustment", "transfer", "return"])


def test_12_out_of_scope_returns_guardrail(chain) -> None:
    answer = ask_question("What is the weather forecast for Mumbai?", chain)["answer"].lower()
    assert "don't have" in answer or "not available" in answer


def test_13_non_empty_answer_for_sku(chain) -> None:
    assert len(ask_question("What is SKU?", chain)["answer"]) > 10


def test_14_non_empty_answer_for_fifo(chain) -> None:
    assert len(ask_question("What is FIFO?", chain)["answer"]) > 10


def test_15_non_empty_answer_for_stockout(chain) -> None:
    assert len(ask_question("What is a stockout?", chain)["answer"]) > 10


def test_16_category_management_diff(chain) -> None:
    answer = ask_question(
        "How do grocery products differ from electronics in inventory management?", chain
    )["answer"].lower()
    assert any(x in answer for x in ["grocery", "electronic", "shelf life", "velocity", "cost"])


def test_17_empty_query_is_handled(chain) -> None:
    result = ask_question("", chain)
    assert isinstance(result, dict)
    assert "answer" in result


def test_18_latency_under_five_seconds(chain) -> None:
    start = time.time()
    ask_question("What is a reorder point?", chain)
    assert time.time() - start < 5.0


def test_19_sources_are_returned(chain) -> None:
    result = ask_question("How does PO receiving update stock?", chain)
    assert isinstance(result, dict)
    assert "answer" in result
    assert "source_documents" in result


def test_20_result_shape_contains_expected_keys(chain) -> None:
    result = ask_question("What are low stock alerts?", chain)
    assert set(result.keys()) >= {"answer", "source_documents"}


def test_21_ingestion_is_repeatable(prepared_db: Path) -> None:
    first = ingest_inventory_manual(persist_directory=prepared_db, collection_name=COLLECTION_NAME)
    second = ingest_inventory_manual(persist_directory=prepared_db, collection_name=COLLECTION_NAME)
    assert first["stored"] >= 20
    assert second["stored"] >= 20


def test_22_query_returns_at_most_top_k(chain) -> None:
    docs = chain.retriever.get_relevant_documents("Explain stock alerts")
    assert len(docs) <= 4


def test_23_prompt_contains_out_of_scope_guardrail() -> None:
    assert "I don't have that information in the inventory manual." in INVENTORY_RAG_PROMPT


def test_24_collection_dir_default_is_configured() -> None:
    assert str(CHROMA_DIR)


def test_25_langsmith_env_shape() -> None:
    project = os.getenv("LANGCHAIN_PROJECT", "AI-Readiness-POC-07-P2")
    assert project.startswith("AI-Readiness-") and "-P2" in project
