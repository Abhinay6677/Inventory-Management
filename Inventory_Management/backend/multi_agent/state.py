from __future__ import annotations

from typing import Any, TypedDict


class InventoryAnalysisState(TypedDict):
    product_id: int
    product_data: dict[str, Any]
    demand_forecast: dict[str, Any]
    reorder_recommendation: dict[str, Any]
    supplier_quote: dict[str, Any]
    audit_report: str
    analysis_status: str
    errors: list[str]
    messages: list[str]


def initial_state(product_id: int) -> InventoryAnalysisState:
    """Create an isolated state for one product analysis."""
    return {
        "product_id": product_id,
        "product_data": {},
        "demand_forecast": {},
        "reorder_recommendation": {},
        "supplier_quote": {},
        "audit_report": "",
        "analysis_status": "analyzing",
        "errors": [],
        "messages": [],
    }
