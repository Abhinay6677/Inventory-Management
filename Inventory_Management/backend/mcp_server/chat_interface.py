from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import date
from typing import Any

from mcp_server._compat import SimpleAgentExecutor, ToolSpec, get_logger, get_tracer, traceable
from mcp_server.mcp_app import (
    create_purchase_order,
    get_inventory_dashboard,
    get_low_stock_products,
    get_purchase_orders,
    get_supplier_catalog,
    update_stock,
)

logger = get_logger("poc07.mcp")
tracer = get_tracer("poc-07-chat")

_CHAT_SESSIONS: dict[str, "ChatSession"] = {}


@dataclass
class ChatSession:
    session_id: str
    history: list[dict[str, str]]

    def __init__(self, session_id: str) -> None:
        self.session_id = session_id
        self.history = []

    def add_message(self, role: str, content: str) -> None:
        self.history.append({"role": role, "content": content})

    def get_history_for_llm(self) -> list[dict[str, str]]:
        return self.history[-10:]


def _tool_specs() -> list[ToolSpec]:
    return [
        ToolSpec(
            update_stock,
            name="update_stock",
            description="Update stock for a product by recording a stock movement.",
            properties={
                "product_id": {"type": "integer"},
                "movement_type": {"type": "string"},
                "quantity": {"type": "integer"},
                "reference_number": {"type": "string"},
                "notes": {"type": "string"},
            },
        ),
        ToolSpec(
            create_purchase_order,
            name="create_purchase_order",
            description="Create a new purchase order for a supplier.",
            properties={
                "supplier_id": {"type": "integer"},
                "order_date": {"type": "string"},
                "items": {"type": "array"},
                "expected_delivery": {"type": "string"},
            },
        ),
        ToolSpec(
            get_low_stock_products,
            name="get_low_stock_products",
            description="Get low stock and out-of-stock products currently at or below their reorder point.",
            properties={},
        ),
        ToolSpec(
            get_supplier_catalog,
            name="get_supplier_catalog",
            description="Get a supplier's product catalog with SKUs, product names, and cost prices.",
            properties={"supplier_id": {"type": "integer"}},
        ),
        ToolSpec(
            get_purchase_orders,
            name="get_purchase_orders",
            description="List purchase orders with optional filtering by status or supplier.",
            properties={
                "status": {"type": "string"},
                "supplier_id": {"type": "integer"},
            },
        ),
        ToolSpec(
            get_inventory_dashboard,
            name="get_inventory_dashboard",
            description="Get overall health metrics for the inventory management dashboard.",
            properties={},
        ),
    ]


def _extract_int(message: str) -> int | None:
    match = re.search(r"\b(\d+)\b", message)
    if match is None:
        return None
    return int(match.group(1))


def _extract_status(message: str) -> str | None:
    for status in ("draft", "submitted", "acknowledged", "received", "cancelled"):
        if status in message:
            return status
    return None


def _render_output(value: Any) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, default=str)


def _route_message(message: str) -> dict[str, Any]:
    text = message.strip().lower()
    intermediate_steps: list[tuple[str, dict[str, Any]]] = []

    if not text or any(word in text for word in ("dashboard", "metrics", "summary", "health")):
        result = get_inventory_dashboard()
        intermediate_steps.append(("get_inventory_dashboard", {}))
        return {"output": _render_output(result), "intermediate_steps": intermediate_steps}

    if any(word in text for word in ("low stock", "out of stock", "reorder")):
        result = get_low_stock_products()
        intermediate_steps.append(("get_low_stock_products", {}))
        return {"output": _render_output(result), "intermediate_steps": intermediate_steps}

    if "supplier" in text and any(word in text for word in ("catalog", "price", "pricing")):
        supplier_id = _extract_int(message) or 1
        result = get_supplier_catalog(supplier_id=supplier_id)
        intermediate_steps.append(("get_supplier_catalog", {"supplier_id": supplier_id}))
        return {"output": _render_output(result), "intermediate_steps": intermediate_steps}

    if "purchase order" in text or re.search(r"\bpo\b", text):
        if any(word in text for word in ("list", "show", "status", "open", "find")):
            supplier_id = _extract_int(message)
            status = _extract_status(text)
            result = get_purchase_orders(status=status, supplier_id=supplier_id)
            payload = {"status": status, "supplier_id": supplier_id}
            intermediate_steps.append(("get_purchase_orders", payload))
            return {"output": _render_output(result), "intermediate_steps": intermediate_steps}

        supplier_id = _extract_int(message) or 1
        result = create_purchase_order(
            supplier_id=supplier_id,
            order_date=date.today().isoformat(),
            items=[{"product_id": supplier_id, "quantity_ordered": 1, "unit_cost": 1.0}],
            expected_delivery=None,
        )
        payload = {"supplier_id": supplier_id}
        intermediate_steps.append(("create_purchase_order", payload))
        return {"output": _render_output(result), "intermediate_steps": intermediate_steps}

    if any(word in text for word in ("stock", "receipt", "sale", "adjustment", "transfer", "return")):
        product_id = _extract_int(message) or 1
        quantity = _extract_int(message) or 1
        movement_type = next((word for word in ("receipt", "sale", "adjustment", "transfer", "return") if word in text), "adjustment")
        result = update_stock(
            product_id=product_id,
            movement_type=movement_type,
            quantity=quantity,
            reference_number=None,
            notes=message[:120],
        )
        payload = {"product_id": product_id, "movement_type": movement_type, "quantity": quantity}
        intermediate_steps.append(("update_stock", payload))
        return {"output": _render_output(result), "intermediate_steps": intermediate_steps}

    result = get_inventory_dashboard()
    intermediate_steps.append(("get_inventory_dashboard", {}))
    return {"output": _render_output(result), "intermediate_steps": intermediate_steps}


def build_chat_executor() -> SimpleAgentExecutor:
    return SimpleAgentExecutor(_tool_specs(), _route_message)


@traceable(project_name="AI-Readiness-POC-07-P4")
def process_message(message: str, session_id: str = "default") -> dict[str, Any]:
    session = _CHAT_SESSIONS.setdefault(session_id, ChatSession(session_id=session_id))
    session.add_message("user", message)

    try:
        executor = build_chat_executor()
        logger.info(
            "chat_message",
            extra={"poc_id": "POC-07", "phase": 4, "session_id": session_id, "message_preview": message[:60]},
        )
        result = executor.invoke({"input": message})
        output = result.get("output", "") if isinstance(result, dict) else str(result)
        session.add_message("assistant", output)
        return {
            "output": output,
            "session_id": session_id,
            "intermediate_steps": result.get("intermediate_steps", []) if isinstance(result, dict) else [],
        }
    except Exception as exc:
        logger.error("chat_error", extra={"poc_id": "POC-07", "phase": 4, "session_id": session_id, "error": str(exc)})
        error_output = f"Error: {exc}"
        session.add_message("assistant", error_output)
        return {"output": error_output, "session_id": session_id, "error": str(exc)}
