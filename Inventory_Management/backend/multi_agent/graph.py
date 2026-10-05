from __future__ import annotations

from typing import Callable

from .agents import demand_forecaster, inventory_auditor, reorder_agent, supplier_coordinator
from .state import InventoryAnalysisState, initial_state

try:
    from langgraph.graph import END, StateGraph
except ImportError:  # pragma: no cover - optional runtime dependency
    StateGraph = None
    END = "__end__"


def should_skip_to_audit(state: InventoryAnalysisState) -> str:
    if len(state.get("errors", [])) >= 3 or state.get("analysis_status") == "error":
        return "inventory_auditor"
    return "reorder_agent"


class _CompiledInventoryGraph:
    def __init__(self) -> None:
        self.nodes: dict[str, Callable[[InventoryAnalysisState], InventoryAnalysisState]] = {
            "demand_forecaster": demand_forecaster,
            "reorder_agent": reorder_agent,
            "supplier_coordinator": supplier_coordinator,
            "inventory_auditor": inventory_auditor,
        }

    def invoke(self, state: InventoryAnalysisState) -> InventoryAnalysisState:
        state = self.nodes["demand_forecaster"](state)
        next_node = should_skip_to_audit(state)
        if next_node == "reorder_agent":
            state = self.nodes["reorder_agent"](state)
            state = self.nodes["supplier_coordinator"](state)
        return self.nodes["inventory_auditor"](state)


def build_inventory_graph() -> _CompiledInventoryGraph:
    """Build the StateGraph-compatible workflow.

    The small compiled adapter keeps the workflow usable when optional LangGraph
    dependencies are unavailable, while preserving the same node and edge contract.
    """
    if StateGraph is None:
        return _CompiledInventoryGraph()
    graph = StateGraph(InventoryAnalysisState)
    graph.add_node("demand_forecaster", demand_forecaster)
    graph.add_node("reorder_agent", reorder_agent)
    graph.add_node("supplier_coordinator", supplier_coordinator)
    graph.add_node("inventory_auditor", inventory_auditor)
    graph.set_entry_point("demand_forecaster")
    graph.add_conditional_edges(
        "demand_forecaster",
        should_skip_to_audit,
        {"reorder_agent": "reorder_agent", "inventory_auditor": "inventory_auditor"},
    )
    graph.add_edge("reorder_agent", "supplier_coordinator")
    graph.add_edge("supplier_coordinator", "inventory_auditor")
    graph.add_edge("inventory_auditor", END)
    return graph.compile()


def analyze_product(product_id: int) -> InventoryAnalysisState:
    return build_inventory_graph().invoke(initial_state(product_id))
