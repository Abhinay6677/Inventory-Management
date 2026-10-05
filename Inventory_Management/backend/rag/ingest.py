from __future__ import annotations

from rag.rag_chain import ingest_inventory_manual


if __name__ == "__main__":
    result = ingest_inventory_manual()
    print(f"Ingestion completed: chunks={result['chunks']}, stored={result['stored']}")
