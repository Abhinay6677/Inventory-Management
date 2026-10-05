from __future__ import annotations

import inspect
import os
from typing import Any

from agent.telemetry import get_tracer

try:
    import requests  # type: ignore
except Exception:  # pragma: no cover
    class _HTTPError(Exception):
        def __init__(self, message: str = "HTTP error", status_code: int = 500) -> None:
            super().__init__(message)
            self.response = type("Resp", (), {"status_code": status_code})()

    class _RequestsExceptions:
        connection_error = ConnectionError
        http_error = _HTTPError

    class _RequestsFallback:
        exceptions = _RequestsExceptions

        @staticmethod
        def get(*_args: Any, **_kwargs: Any) -> Any:
            raise ConnectionError("requests package is unavailable")

    requests = _RequestsFallback()  # type: ignore[assignment]

try:
    import structlog  # type: ignore

    logger = structlog.get_logger()
except Exception:  # pragma: no cover
    import logging

    logging.basicConfig(level=logging.INFO, format="%(message)s")
    logger = logging.getLogger("poc07.phase3")

class _ArgsSchema:
    def __init__(self, properties: dict[str, dict[str, str]]) -> None:
        self._properties = properties

    def schema(self) -> dict[str, Any]:
        return {"properties": self._properties}


class _SimpleTool:
    def __init__(self, name: str, description: str, fn: Any) -> None:
        self.name = name
        self.description = description
        self._fn = fn
        self._signature = inspect.signature(fn)
        properties = {
            pname: {"type": "string"}
            for pname in self._signature.parameters.keys()
        }
        self.args_schema = _ArgsSchema(properties) if properties else None

    def invoke(self, value: Any) -> str:
        params = list(self._signature.parameters.keys())
        if not params:
            return self._fn()
        if len(params) == 1:
            key = params[0]
            if isinstance(value, dict):
                arg_value = value.get(key)
                if arg_value is None and value:
                    arg_value = next(iter(value.values()))
                return self._fn(str(arg_value or ""))
            return self._fn(str(value))
        if isinstance(value, dict):
            prepared = {k: str(v) for k, v in value.items() if k in self._signature.parameters}
            return self._fn(**prepared)
        raise ValueError("Tool expects a dictionary payload for multi-argument input")

    def __call__(self, value: Any) -> str:
        return self.invoke(value)


def tool(name: str):
    def decorator(fn: Any) -> Any:
        desc = (inspect.getdoc(fn) or "").strip()
        return _SimpleTool(name=name, description=desc, fn=fn)

    return decorator


BASE_URL = os.getenv("PHASE1_API_BASE_URL", "http://localhost:8000/api/v1")
tracer = get_tracer("poc-07-agent")
REQUEST_CONNECTION_ERROR = getattr(getattr(requests, "exceptions", object), "ConnectionError", ConnectionError)
REQUEST_HTTP_ERROR = getattr(getattr(requests, "exceptions", object), "HTTPError", Exception)


def _auth_headers() -> dict[str, str]:
    token = os.getenv("PHASE1_API_BEARER_TOKEN", "").strip()
    if not token:
        return {}
    return {"Authorization": f"Bearer {token}"}


def _api_get(path: str, params: dict[str, Any] | None = None) -> dict[str, Any] | list[dict[str, Any]]:
    try:
        response = requests.get(
            f"{BASE_URL}{path}",
            params=params or {},
            headers=_auth_headers(),
            timeout=10,
        )
        response.raise_for_status()
        return response.json()
    except (REQUEST_CONNECTION_ERROR, ConnectionError):
        return {"error": "API unavailable"}
    except REQUEST_HTTP_ERROR as exc:
        status = getattr(getattr(exc, "response", None), "status_code", None)
        if status == 404:
            return {"error": "not_found"}
        return {"error": str(exc)}
    except Exception as exc:  # pragma: no cover
        return {"error": str(exc)}


def _pick(payload: dict[str, Any], *keys: str, default: Any = None) -> Any:
    for key in keys:
        if key in payload:
            return payload.get(key)
    return default


@tool("get_low_stock_alerts")
def get_low_stock_alerts(query: str = "") -> str:
    """Use for low stock, out-of-stock, and reorder-priority questions. Returns all products where quantity_available is at or below reorder_point so procurement teams can prioritize replenishment quickly and safely."""
    del query
    with tracer.start_as_current_span("tool.get_low_stock_alerts") as span:
        span.set_attribute("poc_id", "POC-07")
        data = _api_get("/stock/low-alerts")
        if isinstance(data, dict) and "error" in data:
            return f"Error: {data['error']}"
        if not data:
            return "No low stock alerts. All products are above reorder points."

        lines = []
        for product in data[:20]:
            sku = _pick(product, "sku", "productSku", default="NA")
            name = _pick(product, "name", "productName", default="Unknown")
            quantity_available = _pick(product, "quantity_available", "quantityAvailable", default=0)
            reorder_point = _pick(product, "reorder_point", "reorderPoint", default=0)
            lines.append(
                f"- {sku}: {name} - {quantity_available} available (reorder at {reorder_point})"
            )

        if hasattr(logger, "info"):
            logger.info("tool_called", extra={"tool": "get_low_stock_alerts", "phase": "P3"})
        return f"{len(data)} products need reorder:\n" + "\n".join(lines)


@tool("get_product_stock")
def get_product_stock(product_id: str) -> str:
    """Use for questions on a specific product's stock, quantity on hand, available balance, reserved quantity, or reorder configuration. Returns product stock details and reorder thresholds for decisions."""
    with tracer.start_as_current_span("tool.get_product_stock") as span:
        span.set_attribute("poc_id", "POC-07")
        data = _api_get(f"/products/{product_id}")
        if isinstance(data, dict) and "error" in data:
            if "not_found" in str(data.get("error", "")):
                return f"Product {product_id} not found."
            return f"Error: {data.get('error')}"

        stock = {}
        if isinstance(data, dict):
            stock = _pick(data, "stock_level", "stockLevel", default={}) or {}

        sku = _pick(data, "sku", default="NA") if isinstance(data, dict) else "NA"
        name = _pick(data, "name", default="Unknown") if isinstance(data, dict) else "Unknown"
        quantity_on_hand = _pick(stock, "quantity_on_hand", "quantityOnHand", default=0)
        quantity_available = _pick(stock, "quantity_available", "quantityAvailable", default=0)
        quantity_reserved = _pick(stock, "quantity_reserved", "quantityReserved", default=0)
        reorder_point = _pick(data, "reorder_point", "reorderPoint", default=None) if isinstance(data, dict) else None
        reorder_quantity = _pick(data, "reorder_quantity", "reorderQuantity", default=None) if isinstance(data, dict) else None

        if hasattr(logger, "info"):
            logger.info("tool_called", extra={"tool": "get_product_stock", "phase": "P3"})
        return (
            f"Product: {sku} - {name}\n"
            f"  On hand: {quantity_on_hand}, "
            f"Available: {quantity_available}, "
            f"Reserved: {quantity_reserved}\n"
            f"  Reorder point: {reorder_point}, "
            f"Reorder qty: {reorder_quantity}"
        )


@tool("get_supplier_catalog")
def get_supplier_catalog(supplier_id: str) -> str:
    """Use for supplier catalog and pricing questions, including which products are available from a supplier and at what cost. Returns a compact product list with SKU, name, unit cost, and unit of measure."""
    with tracer.start_as_current_span("tool.get_supplier_catalog") as span:
        span.set_attribute("poc_id", "POC-07")
        data = _api_get(f"/suppliers/{supplier_id}/catalog")
        if isinstance(data, dict) and "error" in data:
            return f"Supplier {supplier_id} catalog unavailable."

        catalog_products = data
        if isinstance(data, dict):
            catalog_products = data.get("products", [])

        if not catalog_products:
            return f"Supplier {supplier_id} has no products in catalog."

        lines = []
        for product in catalog_products[:20]:
            cost = float(_pick(product, "cost_price", "costPrice", default=0.0))
            sku = _pick(product, "sku", default="NA")
            name = _pick(product, "name", default="Unknown")
            uom = _pick(product, "unit_of_measure", "unitOfMeasure", default="unit")
            lines.append(
                f"- {sku}: {name} - Rs {cost:.2f}/{uom}"
            )

        if hasattr(logger, "info"):
            logger.info("tool_called", extra={"tool": "get_supplier_catalog", "phase": "P3"})
        return f"Supplier {supplier_id} catalog ({len(catalog_products)} products):\n" + "\n".join(lines)


@tool("get_dashboard_stats")
def get_dashboard_stats(query: str = "") -> str:
    """Use for overall inventory dashboard summaries including total products, low stock count, out-of-stock count, open purchase orders, and total stock value for management reporting and operational decisions."""
    del query
    with tracer.start_as_current_span("tool.get_dashboard_stats") as span:
        span.set_attribute("poc_id", "POC-07")
        data = _api_get("/dashboard")
        if isinstance(data, dict) and "error" in data:
            return f"Dashboard unavailable: {data.get('error')}"

        if hasattr(logger, "info"):
            logger.info("tool_called", extra={"tool": "get_dashboard_stats", "phase": "P3"})
        total_products = _pick(data, "total_products", "totalProducts", default=0)
        low_stock_count = _pick(data, "low_stock_count", "lowStockCount", default=0)
        out_of_stock_count = _pick(data, "out_of_stock_count", "outOfStockCount", default=0)
        open_po_count = _pick(data, "open_po_count", "openPoCount", default=0)
        total_stock_value = _pick(data, "total_stock_value", "totalStockValue", default=0)
        return (
            "Inventory Dashboard:\n"
            f"  Total products: {total_products}\n"
            f"  Low stock: {low_stock_count}\n"
            f"  Out of stock: {out_of_stock_count}\n"
            f"  Open POs: {open_po_count}\n"
            f"  Total stock value: Rs {float(total_stock_value):.2f}"
        )


@tool("search_inventory_policy")
def search_inventory_policy(question: str) -> str:
    """Use for inventory policy questions such as reorder rules, purchase-order process, stock movement definitions, supplier governance, and operating procedures. Uses the inventory manual RAG chain to answer grounded policy queries."""
    with tracer.start_as_current_span("tool.search_inventory_policy") as span:
        span.set_attribute("poc_id", "POC-07")
        try:
            from rag.rag_chain import ask_question, build_rag_chain

            result = ask_question(question, build_rag_chain())
            if hasattr(logger, "info"):
                logger.info("tool_called", extra={"tool": "search_inventory_policy", "phase": "P3"})
            return str(result.get("answer", "No answer found."))
        except Exception as exc:
            return f"Policy search error: {str(exc)}"
