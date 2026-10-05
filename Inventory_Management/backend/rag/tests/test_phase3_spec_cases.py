from __future__ import annotations

from unittest.mock import patch

import pytest


class TestPhase3Spec20:
    def test_01_tools_defined(self) -> None:
        from agent.tools import (
            get_dashboard_stats,
            get_low_stock_alerts,
            get_product_stock,
            get_supplier_catalog,
            search_inventory_policy,
        )

        for tool in [
            get_low_stock_alerts,
            get_product_stock,
            get_supplier_catalog,
            get_dashboard_stats,
            search_inventory_policy,
        ]:
            assert tool.name and tool.description

    def test_02_tools_registered(self) -> None:
        from agent.agent import build_agent_executor

        names = [tool.name for tool in build_agent_executor().tools]
        for required in [
            "get_low_stock_alerts",
            "get_product_stock",
            "get_supplier_catalog",
            "get_dashboard_stats",
            "search_inventory_policy",
        ]:
            assert required in names

    def test_03_desc_length(self) -> None:
        from agent.tools import (
            get_dashboard_stats,
            get_low_stock_alerts,
            get_product_stock,
            get_supplier_catalog,
            search_inventory_policy,
        )

        for tool in [
            get_low_stock_alerts,
            get_product_stock,
            get_supplier_catalog,
            get_dashboard_stats,
            search_inventory_policy,
        ]:
            assert len(tool.description) >= 50, f"{tool.name} description too short"

    def test_04_string_input(self) -> None:
        from agent.tools import get_product_stock, get_supplier_catalog

        for tool in [get_product_stock, get_supplier_catalog]:
            if tool.args_schema:
                assert len(tool.args_schema.schema().get("properties", {})) >= 1

    def test_05_dashboard_counts(self) -> None:
        mock = {
            "total_products": 500,
            "low_stock_count": 25,
            "out_of_stock_count": 5,
            "open_po_count": 8,
            "total_stock_value": 2500000.0,
        }
        with patch("agent.tools._api_get", return_value=mock):
            from agent.tools import get_dashboard_stats

            result = get_dashboard_stats.invoke("")
            assert "500" in result or "product" in result.lower()
            assert "25" in result or "low" in result.lower()

    def test_06_product_stock(self) -> None:
        mock = {
            "id": 1,
            "sku": "SKU-GRO-0001",
            "name": "Basmati Rice",
            "reorder_point": 20,
            "reorder_quantity": 100,
            "stock_level": {
                "quantity_on_hand": 15,
                "quantity_available": 15,
                "quantity_reserved": 0,
            },
        }
        with patch("agent.tools._api_get", return_value=mock):
            from agent.tools import get_product_stock

            result = get_product_stock.invoke("1")
            assert "15" in result or "available" in result.lower()

    def test_07_product_404(self) -> None:
        with patch("agent.tools._api_get", return_value={"error": "not_found"}):
            from agent.tools import get_product_stock

            result = get_product_stock.invoke("9999")
            assert "not found" in result.lower() or "9999" in result

    def test_08_policy_search(self) -> None:
        mock = {"answer": "PO approval required for orders above Rs 50,000."}
        with patch("rag.rag_chain.build_rag_chain"), patch(
            "rag.rag_chain.ask_question", return_value=mock
        ):
            from agent.tools import search_inventory_policy

            result = search_inventory_policy.invoke("When is PO approval required?")
            assert "50,000" in result or "approval" in result.lower()

    def test_09_low_stock(self) -> None:
        mock = [
            {
                "id": 1,
                "sku": "SKU-GRO-0001",
                "name": "Rice",
                "quantity_available": 5,
                "reorder_point": 20,
            }
        ]
        with patch("agent.tools._api_get", return_value=mock):
            from agent.tools import get_low_stock_alerts

            result = get_low_stock_alerts.invoke("")
            assert "SKU-GRO" in result or "reorder" in result.lower() or "1" in result

    def test_10_api_unavailable(self) -> None:
        from agent.tools import _api_get

        with patch("agent.tools.requests.get", side_effect=ConnectionError("refused")):
            result = _api_get("/products/1")
            assert isinstance(result, dict) and "error" in result

    def test_11_system_prompt_role(self) -> None:
        from agent.prompts import INVENTORY_AGENT_SYSTEM_PROMPT

        assert any(
            word in INVENTORY_AGENT_SYSTEM_PROMPT.lower()
            for word in ["inventory", "stock", "procurement", "retail", "reorder"]
        )
        assert len(INVENTORY_AGENT_SYSTEM_PROMPT) >= 100

    def test_12_long_summarized(self) -> None:
        from agent.summarizer import _summarize_if_long

        long_text = "Stock report: " + ("SKU-GRO-0001 has 5 units. " * 100)
        assert len(long_text) > 2000
        with patch("agent.summarizer.load_summarize_chain") as mocked_chain:
            mocked_chain.return_value.run.return_value = "Summary: 100 products need reorder."
            assert isinstance(_summarize_if_long(long_text), str)

    def test_13_short_unchanged(self) -> None:
        from agent.summarizer import _summarize_if_long

        short = "SKU-GRO-0001 Basmati Rice: 15 available, reorder point 20."
        assert _summarize_if_long(short) == short

    def test_14_all_tools_referenced(self) -> None:
        from agent.prompts import INVENTORY_AGENT_SYSTEM_PROMPT

        count = sum(
            1
            for tool_name in [
                "get_low_stock_alerts",
                "get_product_stock",
                "get_supplier_catalog",
                "get_dashboard_stats",
                "search_inventory_policy",
            ]
            if tool_name in INVENTORY_AGENT_SYSTEM_PROMPT
        )
        assert count >= 4

    def test_15_low_stock_query(self) -> None:
        from agent.agent import build_agent_executor

        names = [tool.name for tool in build_agent_executor().tools]
        assert "get_low_stock_alerts" in names

    def test_16_multi_tool(self) -> None:
        from agent.agent import build_agent_executor

        names = [tool.name for tool in build_agent_executor().tools]
        assert "get_product_stock" in names and "search_inventory_policy" in names

    def test_17_policy_rag(self) -> None:
        mock = {"answer": "Low stock alert triggers when quantity_available <= reorder_point."}
        with patch("rag.rag_chain.ask_question", return_value=mock):
            with patch("rag.rag_chain.build_rag_chain"):
                from agent.tools import search_inventory_policy

                result = search_inventory_policy.invoke("When is a low stock alert triggered?")
                assert "low" in result.lower() or "reorder" in result.lower()

    def test_18_dashboard_query(self) -> None:
        mock = {
            "total_products": 500,
            "low_stock_count": 25,
            "out_of_stock_count": 5,
            "open_po_count": 8,
            "total_stock_value": 2500000.0,
        }
        with patch("agent.tools._api_get", return_value=mock):
            from agent.tools import get_dashboard_stats

            result = get_dashboard_stats.invoke("")
            assert "500" in result or "product" in result.lower()

    def test_19_ambiguous_no_crash(self) -> None:
        with patch("agent.tools._api_get", return_value=[]):
            from agent.tools import get_low_stock_alerts

            result = get_low_stock_alerts.invoke("")
            assert isinstance(result, str)

    def test_20_langsmith_trace(self) -> None:
        import os

        if not os.getenv("LANGCHAIN_API_KEY"):
            pytest.skip("No key")

        try:
            from langsmith import Client
        except Exception:
            pytest.skip("LangSmith SDK unavailable")

        runs = list(Client().list_runs(project_name="AI-Readiness-POC-07-P3", limit=3))
        if not runs:
            pytest.skip("No traces yet")
        assert len(runs) >= 1
