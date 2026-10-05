from __future__ import annotations

import os
from typing import Any

import requests

from mcp_server._compat import get_logger, get_tracer

try:
    from fastmcp import FastMCP  # type: ignore
except ImportError:  # pragma: no cover
    class FastMCP:
        def __init__(self, name: str) -> None:
            self.name = name
            self.tools: list[Any] = []

        def tool(self):
            def decorator(fn):
                self.tools.append(fn)
                return fn

            return decorator

        def run(self) -> None:
            return None


logger = get_logger("poc07.mcp")
tracer = get_tracer("poc-07-mcp")
BASE_URL = os.getenv("PHASE1_API_BASE_URL", "http://localhost:8000/api/v1")
REQUEST_CONNECTION_ERROR = getattr(getattr(requests, "exceptions", object), "ConnectionError", ConnectionError)

mcp = FastMCP("Inventory Management Server")


def _log_tool_call(tool: str, **extra: Any) -> None:
    payload = {"poc_id": "POC-07", "phase": 4, "tool": tool}
    payload.update(extra)
    logger.info("mcp_tool_called", extra=payload)


def _get(path: str, params: dict[str, Any] | None = None) -> dict[str, Any] | list[Any]:
    try:
        response = requests.get(f"{BASE_URL}{path}", params=params or {}, timeout=10)
        response.raise_for_status()
        return response.json()
    except REQUEST_CONNECTION_ERROR:
        return {"error": "API unavailable"}
    except Exception as exc:
        return {"error": str(exc)}


def _post(path: str, payload: dict[str, Any]) -> dict[str, Any]:
    try:
        response = requests.post(f"{BASE_URL}{path}", json=payload, timeout=10)
        response.raise_for_status()
        return response.json()
    except REQUEST_CONNECTION_ERROR:
        return {"error": "API unavailable"}
    except Exception as exc:
        return {"error": str(exc)}


@mcp.tool()
def update_stock(
    product_id: int,
    movement_type: str,
    quantity: int,
    reference_number: str | None = None,
    notes: str | None = None,
) -> dict[str, Any]:
    """Update stock for a product by recording a stock movement."""
    with tracer.start_as_current_span("mcp.tool.update_stock") as span:
        span.set_attribute("poc_id", "POC-07")
        payload = {
            "movement_type": movement_type,
            "quantity": quantity,
            "reference_number": reference_number,
            "notes": notes,
        }
        result = _post(f"/products/{product_id}/stock", payload)
        _log_tool_call("update_stock", product_id=product_id)
        return result


@mcp.tool()
def create_purchase_order(
    supplier_id: int,
    order_date: str,
    items: list[dict[str, Any]],
    expected_delivery: str | None = None,
) -> dict[str, Any]:
    """Create a new purchase order for a supplier."""
    with tracer.start_as_current_span("mcp.tool.create_purchase_order") as span:
        span.set_attribute("poc_id", "POC-07")
        payload = {
            "supplier_id": supplier_id,
            "order_date": order_date,
            "items": items,
            "expected_delivery": expected_delivery,
        }
        result = _post("/orders", payload)
        _log_tool_call("create_purchase_order", supplier_id=supplier_id)
        return result


@mcp.tool()
def get_low_stock_products() -> list[Any]:
    """Get low stock and out-of-stock products currently at or below their reorder point."""
    with tracer.start_as_current_span("mcp.tool.get_low_stock_products") as span:
        span.set_attribute("poc_id", "POC-07")
        result = _get("/stock/low-alerts")
        _log_tool_call("get_low_stock_products")
        return result


@mcp.tool()
def get_supplier_catalog(supplier_id: int) -> list[Any]:
    """Get a supplier's product catalog with SKUs, product names, and cost prices."""
    with tracer.start_as_current_span("mcp.tool.get_supplier_catalog") as span:
        span.set_attribute("poc_id", "POC-07")
        result = _get(f"/suppliers/{supplier_id}/catalog")
        _log_tool_call("get_supplier_catalog", supplier_id=supplier_id)
        return result


@mcp.tool()
def get_purchase_orders(status: str | None = None, supplier_id: int | None = None) -> list[Any]:
    """List purchase orders with optional filtering by status or supplier."""
    with tracer.start_as_current_span("mcp.tool.get_purchase_orders") as span:
        span.set_attribute("poc_id", "POC-07")
        params: dict[str, Any] = {}
        if status is not None:
            params["status"] = status
        if supplier_id is not None:
            params["supplier_id"] = supplier_id
        result = _get("/orders", params)
        _log_tool_call("get_purchase_orders", **params)
        return result


@mcp.tool()
def get_inventory_dashboard() -> dict[str, Any]:
    """Get overall health metrics for the inventory management dashboard."""
    with tracer.start_as_current_span("mcp.tool.get_inventory_dashboard") as span:
        span.set_attribute("poc_id", "POC-07")
        result = _get("/dashboard")
        _log_tool_call("get_inventory_dashboard")
        return result


if __name__ == "__main__":
    mcp.run()
