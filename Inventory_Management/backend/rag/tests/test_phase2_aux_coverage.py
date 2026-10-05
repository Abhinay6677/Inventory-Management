from __future__ import annotations

import importlib
import runpy
import sys
from types import ModuleType

import rag.rag_chain as rag_chain


class _ChatContext:
    def __enter__(self):
        return self

    def __exit__(self, _exc_type, _exc, _tb) -> bool:
        return False


def _make_fake_streamlit(query: str | None) -> ModuleType:
    module = ModuleType("streamlit")
    module.session_state = {}
    module._query = query
    module._calls = []

    def set_page_config(**kwargs):
        module._calls.append(("set_page_config", kwargs))

    def title(value):
        module._calls.append(("title", value))

    def caption(value):
        module._calls.append(("caption", value))

    def chat_message(role):
        module._calls.append(("chat_message", role))
        return _ChatContext()

    def markdown(value):
        module._calls.append(("markdown", value))

    def chat_input(_prompt):
        return module._query

    module.set_page_config = set_page_config
    module.title = title
    module.caption = caption
    module.chat_message = chat_message
    module.markdown = markdown
    module.chat_input = chat_input
    return module


def test_app_bootstrap_without_query(monkeypatch) -> None:
    fake_streamlit = _make_fake_streamlit(query=None)
    monkeypatch.setitem(sys.modules, "streamlit", fake_streamlit)
    monkeypatch.setattr(rag_chain, "build_rag_chain", lambda: "fake-chain")
    monkeypatch.setattr(rag_chain, "ask_question", lambda _q, _c: {"answer": "ok"})

    sys.modules.pop("rag.app", None)
    app_module = importlib.import_module("rag.app")

    assert app_module.st.session_state["rag_chain"] == "fake-chain"
    assert app_module.st.session_state["messages"] == []


def test_app_adds_user_and_assistant_messages(monkeypatch) -> None:
    fake_streamlit = _make_fake_streamlit(query="What is FIFO?")
    monkeypatch.setitem(sys.modules, "streamlit", fake_streamlit)
    monkeypatch.setattr(rag_chain, "build_rag_chain", lambda: "fake-chain")
    monkeypatch.setattr(rag_chain, "ask_question", lambda _q, _c: {"answer": "FIFO means First In First Out."})

    sys.modules.pop("rag.app", None)
    app_module = importlib.import_module("rag.app")

    messages = app_module.st.session_state["messages"]
    assert len(messages) == 2
    assert messages[0]["role"] == "user"
    assert messages[1]["role"] == "assistant"
    assert "FIFO" in messages[1]["content"]


def test_ingest_main_prints_summary(monkeypatch, capsys) -> None:
    monkeypatch.setattr(rag_chain, "ingest_inventory_manual", lambda: {"chunks": 25, "stored": 25})

    runpy.run_module("rag.ingest", run_name="__main__")
    output = capsys.readouterr().out

    assert "Ingestion completed" in output
    assert "chunks=25" in output
    assert "stored=25" in output
