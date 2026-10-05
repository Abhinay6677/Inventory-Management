from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from rag.rag_chain import (
    ACTIVE_OLLAMA_MODEL,
    OLLAMA_BASE_URL,
    OLLAMA_ENABLED,
    OLLAMA_MODEL,
    OLLAMA_RUNTIME_READY,
    ask_question,
    build_rag_chain,
)

CHAIN = build_rag_chain()
DEBUG_RESPONSE_DEFAULT = os.getenv('RAG_DEBUG_RESPONSE', 'false').lower() == 'true'


class RagRequestHandler(BaseHTTPRequestHandler):
    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS, GET')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS, GET')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        if self.path == '/health':
            self._send_json(200, {'status': 'ok', 'service': 'inventory-rag-phase2'})
            return
        if self.path == '/api/v1/rag/debug/config':
            self._send_json(
                200,
                {
                    'debugResponseDefault': DEBUG_RESPONSE_DEFAULT,
                    'ollamaEnabled': OLLAMA_ENABLED,
                    'ollamaModelConfigured': OLLAMA_MODEL,
                    'ollamaModelActive': ACTIVE_OLLAMA_MODEL,
                    'ollamaRuntimeReady': OLLAMA_RUNTIME_READY,
                    'ollamaBaseUrl': OLLAMA_BASE_URL,
                },
            )
            return
        self._send_json(404, {'error': 'Not found'})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != '/api/v1/rag/ask':
            self._send_json(404, {'error': 'Not found'})
            return

        try:
            length = int(self.headers.get('Content-Length', '0'))
            raw = self.rfile.read(length) if length > 0 else b'{}'
            payload = json.loads(raw.decode('utf-8'))
            question = str(payload.get('question', '')).strip()
            debug = bool(payload.get('debug', False)) or DEBUG_RESPONSE_DEFAULT
            result = ask_question(question, CHAIN)
            answer = result.get('answer', '')
            sources = result.get('source_documents', [])
            response_payload: dict[str, Any] = {
                'question': question,
                'answer': answer,
                'sourceCount': len(sources),
            }
            if debug:
                response_payload['debug'] = {
                    'generationMode': result.get('generation_mode', 'unknown'),
                    'ollamaEnabled': OLLAMA_ENABLED,
                    'ollamaModelConfigured': OLLAMA_MODEL,
                    'ollamaModelActive': ACTIVE_OLLAMA_MODEL,
                    'ollamaRuntimeReady': OLLAMA_RUNTIME_READY,
                }
            self._send_json(200, response_payload)
        except json.JSONDecodeError:
            self._send_json(400, {'error': 'Invalid JSON payload'})
        except Exception as exc:  # pragma: no cover
            self._send_json(500, {'error': f'Failed to process question: {exc}'})


def run_server(host: str = '0.0.0.0', port: int = 8008) -> None:
    server = ThreadingHTTPServer((host, port), RagRequestHandler)
    server.daemon_threads = True
    print(f'Phase 2 RAG API server listening on http://{host}:{port}')
    server.serve_forever()


if __name__ == '__main__':
    run_server()
