from __future__ import annotations

import json
import os
from typing import Any

try:
    import requests
except ImportError:  # pragma: no cover - dependencies are supplied in deployment
    requests = None  # type: ignore[assignment]

try:
    import structlog
    logger = structlog.get_logger()
except ImportError:  # pragma: no cover - keeps unit tests dependency-light
    import logging
    logger = logging.getLogger(__name__)

try:
    from langchain.schema import HumanMessage
except ImportError:  # pragma: no cover
    class HumanMessage:
        def __init__(self, content: str) -> None:
            self.content = content

try:
    from langchain_google_genai import ChatGoogleGenerativeAI
except ImportError:  # pragma: no cover
    ChatGoogleGenerativeAI = None

try:
    from langsmith import traceable
except ImportError:  # pragma: no cover
    def traceable(**_kwargs: Any):
        def decorate(function: Any) -> Any:
            return function
        return decorate


BASE_URL = os.getenv("PHASE1_API_BASE_URL", "http://localhost:8000/api/v1")
PROJECT_NAME = "AI-Readiness-POC-07-P5"


class _FallbackLLM:
    def invoke(self, _messages: list[Any]) -> Any:
        return type("Response", (), {"content": '{"stockout_risk":"unknown","demand_trend":"unknown","avg_daily_demand":0,"days_of_stock_remaining":0,"forecast_notes":"No model configured."}'})()


def _create_llm() -> Any:
    if ChatGoogleGenerativeAI is None or not os.getenv("GOOGLE_API_KEY"):
        return _FallbackLLM()
    return ChatGoogleGenerativeAI(model="gemini-2.0-flash", temperature=0.2)


_llm = _create_llm()


def _safe_json(text: str) -> dict[str, Any]:
    """Parse JSON even when a model surrounds it with markdown or prose."""
    if not isinstance(text, str):
        return {}
    start, end = text.find("{"), text.rfind("}") + 1
    if start < 0 or end <= start:
        return {}
    try:
        value = json.loads(text[start:end])
    except (TypeError, ValueError):
        return {}
    return value if isinstance(value, dict) else {}


def _invoke_json(prompt: str, fallback: dict[str, Any], errors: list[str], label: str) -> dict[str, Any]:
    try:
        response = _llm.invoke([HumanMessage(content=prompt)])
        return _safe_json(getattr(response, "content", "")) or fallback
    except Exception as exc:
        errors.append(f"{label} LLM error: {exc}")
        return fallback


def _fetch(path: str, errors: list[str]) -> Any:
    if requests is None:
        errors.append("Product fetch error: requests package is unavailable")
        return {}
    try:
        response = requests.get(f"{BASE_URL}{path}", timeout=10)
        response.raise_for_status()
        return response.json()
    except Exception as exc:
        errors.append(f"API fetch error: {exc}")
        return {}


@traceable(project_name=PROJECT_NAME)
def demand_forecaster(state: InventoryAnalysisState) -> InventoryAnalysisState:
    errors = list(state["errors"])
    messages = list(state["messages"])
    product_data = _fetch(f"/products/{state['product_id']}", errors)
    if not isinstance(product_data, dict):
        product_data = {}
    forecast = _invoke_json(
        f"Analyze product inventory and forecast demand. Respond with JSON only.\nProduct data: {json.dumps(product_data)}",
        {"avg_daily_demand": 0, "demand_trend": "unknown", "days_of_stock_remaining": 0,
         "stockout_risk": "unknown", "forecast_notes": "Insufficient data."},
        errors,
        "Demand",
    )
    messages.append(
        f"Demand Forecaster: risk={forecast.get('stockout_risk')}, trend={forecast.get('demand_trend')}"
    )
    logger.info("agent_complete", poc_id="POC-07", phase="P5", agent="demand_forecaster")
    return {**state, "product_data": product_data, "demand_forecast": forecast,
            "errors": errors, "messages": messages}


@traceable(project_name=PROJECT_NAME)
def reorder_agent(state: InventoryAnalysisState) -> InventoryAnalysisState:
    errors = list(state["errors"])
    messages = list(state["messages"])
    recommendation = _invoke_json(
        f"Determine if reorder is needed. Respond with JSON only.\nProduct: {json.dumps(state['product_data'])}\nForecast: {json.dumps(state['demand_forecast'])}",
        {"reorder_required": False, "recommended_quantity": 0, "urgency": "not_required",
         "reason": "Analysis unavailable."},
        errors,
        "Reorder",
    )
    status = "reorder_required" if recommendation.get("reorder_required") else state["analysis_status"]
    messages.append(
        f"Reorder Agent: required={recommendation.get('reorder_required')}, urgency={recommendation.get('urgency')}"
    )
    logger.info("agent_complete", poc_id="POC-07", phase="P5", agent="reorder_agent")
    return {**state, "reorder_recommendation": recommendation, "analysis_status": status,
            "errors": errors, "messages": messages}


@traceable(project_name=PROJECT_NAME)
def supplier_coordinator(state: InventoryAnalysisState) -> InventoryAnalysisState:
    errors = list(state["errors"])
    messages = list(state["messages"])
    supplier_id = state["product_data"].get("supplier_id")
    catalog = _fetch(f"/suppliers/{supplier_id}/catalog", errors) if supplier_id else []
    if not isinstance(catalog, list):
        catalog = []
    quote = _invoke_json(
        f"Generate a supplier quote. Respond with JSON only.\nProduct: {json.dumps(state['product_data'])}\n"
        f"Reorder recommendation: {json.dumps(state['reorder_recommendation'])}\nCatalog: {json.dumps(catalog[:5])}",
        {"supplier_id": supplier_id, "quoted_unit_cost": 0, "total_order_cost": 0,
         "estimated_lead_time_days": 7, "quote_notes": "Quote unavailable."},
        errors,
        "Supplier",
    )
    messages.append(
        f"Supplier Coordinator: supplier={quote.get('supplier_id')}, cost=₹{quote.get('total_order_cost', 0):.0f}"
    )
    logger.info("agent_complete", poc_id="POC-07", phase="P5", agent="supplier_coordinator")
    return {**state, "supplier_quote": quote, "errors": errors, "messages": messages}


@traceable(project_name=PROJECT_NAME)
def inventory_auditor(state: InventoryAnalysisState) -> InventoryAnalysisState:
    product = state["product_data"]
    stock = product.get("stock_level", {})
    prompt = (
        "Generate a concise inventory audit report in 3-4 sentences. Be specific.\n"
        f"Product: {product.get('sku', 'Unknown')} — {product.get('name', '')}\n"
        f"Stock: {stock.get('quantity_available', 0)} available, reorder point: {product.get('reorder_point', 0)}\n"
        f"Forecast: {state['demand_forecast'].get('days_of_stock_remaining', 0)} days, "
        f"risk: {state['demand_forecast'].get('stockout_risk', 'unknown')}\n"
        f"Reorder: {json.dumps(state['reorder_recommendation'])}\n"
        f"Supplier quote: {json.dumps(state['supplier_quote'])}"
    )
    try:
        report = str(getattr(_llm.invoke([HumanMessage(content=prompt)]), "content", "")).strip()
    except Exception as exc:
        report = f"Audit error: {exc}"
    if not report:
        report = "Inventory audit complete."
    messages = list(state["messages"])
    messages.append(f"Inventory Auditor: report generated ({len(report)} chars)")
    logger.info("agent_complete", poc_id="POC-07", phase="P5", agent="inventory_auditor")
    return {**state, "audit_report": report, "analysis_status": "complete", "messages": messages}
