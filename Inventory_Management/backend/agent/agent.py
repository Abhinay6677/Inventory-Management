from __future__ import annotations

from dataclasses import dataclass

from agent.tools import (
    get_dashboard_stats,
    get_low_stock_alerts,
    get_product_stock,
    get_supplier_catalog,
    search_inventory_policy,
)


@dataclass
class InventoryAgentExecutor:
    tools: list


def build_agent_executor() -> InventoryAgentExecutor:
    return InventoryAgentExecutor(
        tools=[
            get_low_stock_alerts,
            get_product_stock,
            get_supplier_catalog,
            get_dashboard_stats,
            search_inventory_policy,
        ]
    )
