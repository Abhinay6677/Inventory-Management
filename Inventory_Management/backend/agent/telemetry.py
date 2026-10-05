from __future__ import annotations

from typing import Any


class _NoOpSpan:
    def __enter__(self) -> "_NoOpSpan":
        return self

    def __exit__(self, _exc_type: Any, _exc: Any, _tb: Any) -> bool:
        return False

    def set_attribute(self, _key: str, _value: Any) -> None:
        return None


class _NoOpTracer:
    def start_as_current_span(self, _name: str) -> _NoOpSpan:
        return _NoOpSpan()


def get_tracer(name: str) -> Any:
    try:
        from opentelemetry import trace  # type: ignore

        return trace.get_tracer(name)
    except Exception:  # pragma: no cover
        return _NoOpTracer()
