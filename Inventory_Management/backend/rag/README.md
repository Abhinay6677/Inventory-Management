# Phase 2 - RAG Application (POC-07)

This folder contains the complete Phase 2 RAG implementation for inventory and procurement Q&A.

Generation uses local Ollama by default (`RAG_USE_OLLAMA=true`) and falls back to deterministic context-grounded output if Ollama is unavailable.
The runtime now auto-selects the first installed model from `OLLAMA_MODEL` and `OLLAMA_MODEL_CANDIDATES`.

## What is included
- `inventory_manual.md`: 15-section inventory operations knowledge base.
- `rag_chain.py`: ingestion, retrieval, answer generation, observability spans, and query API.
- `ingest.py`: one-command ingestion entry point.
- `app.py`: Streamlit Q&A chat interface.
- `api_server.py`: lightweight HTTP API for frontend integration (`POST /api/v1/rag/ask`).
- `tests/test_phase2_rag_25cases.py`: dedicated 25-case pytest suite.
- `tests/test_phase2_spec_cases.py`: spec-aligned 20-case suite from the attached Phase 2 test document.
- `.env.example`: required environment variables.

## Install
```bash
pip install -r requirements-phase2.txt
```

## Start Ollama model (example)
```bash
ollama run llama3.1
```

## Ingest manual into ChromaDB
```bash
python -m rag.ingest
```

## Run Streamlit app
```bash
streamlit run rag/app.py
```

## Run Phase 2 tests
```bash
pytest rag/tests/test_phase2_rag_25cases.py -v
pytest rag/tests/test_phase2_spec_cases.py -v
```

## Run frontend-compatible RAG API server
```bash
python -m rag.api_server
```

If your Ollama endpoint or model is different, set `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, and `OLLAMA_MODEL_CANDIDATES` in `.env`.

## Debug generation path (rule/ollama/fallback)
Use the config endpoint:
```bash
GET /api/v1/rag/debug/config
```

Request debug details per question:
```json
POST /api/v1/rag/ask
{
	"question": "How do I calculate reorder point?",
	"debug": true
}
```

Response includes:
- `debug.generationMode`: `general_utility`, `rule`, `ollama`, `fallback`, or `out_of_scope`
- `debug.ollamaEnabled`
- `debug.ollamaModelConfigured`
- `debug.ollamaModelActive`
- `debug.ollamaRuntimeReady`

## Run SonarQube Phase 2 scan
```powershell
./run_sonarqube_phase2.ps1 -SonarToken "<your-sonar-token>"
```
