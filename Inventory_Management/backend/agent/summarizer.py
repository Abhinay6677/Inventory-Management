from __future__ import annotations

import os
from typing import Any

from agent.telemetry import get_tracer

try:
    from langchain.chains.summarize import load_summarize_chain  # type: ignore
except Exception:  # pragma: no cover
    def load_summarize_chain(*_args: Any, **_kwargs: Any) -> Any:
        class _FallbackSummaryChain:
            def run(self, text: str) -> str:
                lines = [line.strip() for line in text.splitlines() if line.strip()]
                joined = " ".join(lines)
                if len(joined) > 400:
                    return joined[:400] + "..."
                return joined

        return _FallbackSummaryChain()


LONG_RESPONSE_THRESHOLD = int(os.getenv("PHASE3_SUMMARY_THRESHOLD", "2000"))
tracer = get_tracer("poc-07-agent")


def _summarize_if_long(text: str) -> str:
    if len(text) <= LONG_RESPONSE_THRESHOLD:
        return text

    with tracer.start_as_current_span("agent.summarize") as span:
        span.set_attribute("summarize.input_length", len(text))
        try:
            summary = load_summarize_chain(None).run(text)
        except Exception:
            summary = text[:800] + "..."
        span.set_attribute("summarize.output_length", len(summary))
        return summary
