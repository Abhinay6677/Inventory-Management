from __future__ import annotations

import inspect
import logging
from typing import Any, Callable

try:
    import structlog  # type: ignore
except ImportError:  # pragma: no cover
    structlog = None


if structlog is None:  # pragma: no cover
    logging.basicConfig(level=logging.INFO, format="%(message)s")


def get_logger(name: str = "poc07.mcp"):
    if structlog is not None:
        return structlog.get_logger(name)
    return logging.getLogger(name)


class _NoOpSpan:
    def __init__(self, name: str) -> None:
        self.name = name
        self.attributes: dict[str, Any] = {}

    def __enter__(self) -> "_NoOpSpan":
        return self

    def __exit__(self, _exc_type, _exc, _tb) -> bool:
        return False

    def set_attribute(self, key: str, value: Any) -> None:
        self.attributes[key] = value


class _NoOpTracer:
    def start_as_current_span(self, name: str) -> _NoOpSpan:
        return _NoOpSpan(name)


def get_tracer(name: str):
    try:
        from opentelemetry import trace  # type: ignore

        return trace.get_tracer(name)
    except ImportError:  # pragma: no cover
        return _NoOpTracer()


def traceable(*_args: Any, **_kwargs: Any):
    def decorator(fn: Callable[..., Any]) -> Callable[..., Any]:
        return fn

    return decorator


def _json_type(annotation: Any) -> str:
    if annotation in (int, "int"):
        return "integer"
    if annotation in (float, "float"):
        return "number"
    if annotation in (bool, "bool"):
        return "boolean"
    if annotation in (list, "list"):
        return "array"
    if annotation in (dict, "dict"):
        return "object"
    return "string"


class ArgsSchema:
    def __init__(self, properties: dict[str, dict[str, Any]]) -> None:
        self._properties = properties

    def schema(self) -> dict[str, Any]:
        return {"properties": self._properties}


class ToolSpec:
    def __init__(
        self,
        fn: Callable[..., Any],
        name: str | None = None,
        description: str | None = None,
        properties: dict[str, dict[str, Any]] | None = None,
    ) -> None:
        self.fn = fn
        self.name = name or getattr(fn, "__name__", fn.__class__.__name__)
        self.description = (description or inspect.getdoc(fn) or "").strip()
        try:
            self.signature = inspect.signature(fn)
        except (TypeError, ValueError):
            self.signature = None
        if properties is None and self.signature is not None:
            properties = {}
            for param_name, param in self.signature.parameters.items():
                properties[param_name] = {"type": _json_type(param.annotation)}
        self._param_names = list(properties.keys()) if properties else []
        self.args_schema = ArgsSchema(properties) if properties else None

    def invoke(self, value: Any) -> Any:
        params = self._param_names
        if not params:
            return self.fn()

        if len(params) == 1:
            if isinstance(value, dict):
                key = params[0]
                if key in value:
                    return self.fn(value[key])
                if value:
                    return self.fn(next(iter(value.values())))
                return self.fn()
            return self.fn(value)

        if not isinstance(value, dict):
            raise ValueError("Tool expects a dictionary payload for multi-argument input")

        kwargs: dict[str, Any] = {}
        for name in params:
            if name in value:
                kwargs[name] = value[name]
        return self.fn(**kwargs)

    def __call__(self, value: Any) -> Any:
        return self.invoke(value)


class SimpleAgentExecutor:
    def __init__(self, tools: list[ToolSpec], router: Callable[[str], dict[str, Any]]) -> None:
        self.tools = tools
        self.verbose = True
        self.handle_parsing_errors = True
        self.max_iterations = 5
        self._router = router

    def invoke(self, payload: dict[str, Any] | str) -> dict[str, Any]:
        if isinstance(payload, dict):
            message = str(payload.get("input", ""))
        else:
            message = str(payload)
        return self._router(message)
