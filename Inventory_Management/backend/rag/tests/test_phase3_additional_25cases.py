from __future__ import annotations

from unittest.mock import Mock, patch


class TestPhase3Additional25:
    def test_01_api_get_connection_error_shape(self) -> None:
        from agent.tools import _api_get

        with patch("agent.tools.requests.get", side_effect=ConnectionError("down")):
            result = _api_get("/dashboard")
            assert isinstance(result, dict)
            assert result.get("error") == "API unavailable"

    def test_02_low_stock_empty_message(self) -> None:
        from agent.tools import get_low_stock_alerts

        with patch("agent.tools._api_get", return_value=[]):
            result = get_low_stock_alerts.invoke("")
            assert "No low stock alerts" in result

    def test_03_low_stock_caps_output_at_20(self) -> None:
        from agent.tools import get_low_stock_alerts

        data = [
            {
                "sku": f"SKU-GRO-{i:04d}",
                "name": f"Product {i}",
                "quantity_available": i,
                "reorder_point": i + 1,
            }
            for i in range(25)
        ]
        with patch("agent.tools._api_get", return_value=data):
            result = get_low_stock_alerts.invoke("")
            assert "25 products need reorder" in result
            assert result.count("SKU-GRO-") == 20

    def test_04_supplier_catalog_unavailable(self) -> None:
        from agent.tools import get_supplier_catalog

        with patch("agent.tools._api_get", return_value={"error": "API unavailable"}):
            result = get_supplier_catalog.invoke("42")
            assert "unavailable" in result.lower()

    def test_05_supplier_catalog_empty(self) -> None:
        from agent.tools import get_supplier_catalog

        with patch("agent.tools._api_get", return_value=[]):
            result = get_supplier_catalog.invoke("42")
            assert "has no products" in result.lower()

    def test_06_supplier_catalog_formats_cost(self) -> None:
        from agent.tools import get_supplier_catalog

        with patch(
            "agent.tools._api_get",
            return_value=[
                {
                    "sku": "SKU-GRO-0001",
                    "name": "Rice",
                    "cost_price": 19,
                    "unit_of_measure": "kg",
                }
            ],
        ):
            result = get_supplier_catalog.invoke("42")
            assert "Rs 19.00/kg" in result

    def test_07_dashboard_error(self) -> None:
        from agent.tools import get_dashboard_stats

        with patch("agent.tools._api_get", return_value={"error": "API unavailable"}):
            result = get_dashboard_stats.invoke("")
            assert "Dashboard unavailable" in result

    def test_08_dashboard_happy_path(self) -> None:
        from agent.tools import get_dashboard_stats

        with patch(
            "agent.tools._api_get",
            return_value={
                "total_products": 10,
                "low_stock_count": 2,
                "out_of_stock_count": 1,
                "open_po_count": 3,
                "total_stock_value": 1000,
            },
        ):
            result = get_dashboard_stats.invoke("")
            assert "Total products: 10" in result
            assert "Low stock: 2" in result

    def test_09_product_stock_not_found(self) -> None:
        from agent.tools import get_product_stock

        with patch("agent.tools._api_get", return_value={"error": "not_found"}):
            result = get_product_stock.invoke("9")
            assert "not found" in result.lower()

    def test_10_product_stock_generic_error(self) -> None:
        from agent.tools import get_product_stock

        with patch("agent.tools._api_get", return_value={"error": "boom"}):
            result = get_product_stock.invoke("9")
            assert "Error:" in result

    def test_11_product_stock_fields_rendered(self) -> None:
        from agent.tools import get_product_stock

        with patch(
            "agent.tools._api_get",
            return_value={
                "sku": "SKU-GRO-0001",
                "name": "Rice",
                "reorder_point": 20,
                "reorder_quantity": 100,
                "stock_level": {
                    "quantity_on_hand": 30,
                    "quantity_available": 25,
                    "quantity_reserved": 5,
                },
            },
        ):
            result = get_product_stock.invoke("1")
            assert "On hand: 30" in result
            assert "Reserved: 5" in result

    def test_12_policy_search_success(self) -> None:
        from agent.tools import search_inventory_policy

        with patch("rag.rag_chain.build_rag_chain"), patch(
            "rag.rag_chain.ask_question", return_value={"answer": "Policy answer"}
        ):
            result = search_inventory_policy.invoke("What is FIFO?")
            assert result == "Policy answer"

    def test_13_policy_search_error_path(self) -> None:
        from agent.tools import search_inventory_policy

        with patch("rag.rag_chain.build_rag_chain", side_effect=RuntimeError("fail")):
            result = search_inventory_policy.invoke("What is FIFO?")
            assert "Policy search error" in result

    def test_14_summarizer_short_unchanged(self) -> None:
        from agent.summarizer import _summarize_if_long

        text = "Small text"
        assert _summarize_if_long(text) == text

    def test_15_summarizer_long_calls_chain(self) -> None:
        from agent.summarizer import _summarize_if_long

        long_text = "x" * 2200
        with patch("agent.summarizer.load_summarize_chain") as mocked:
            mocked.return_value.run.return_value = "summary"
            assert _summarize_if_long(long_text) == "summary"

    def test_16_summarizer_fallback_on_chain_failure(self) -> None:
        from agent.summarizer import _summarize_if_long

        long_text = "x" * 2300
        with patch("agent.summarizer.load_summarize_chain", side_effect=RuntimeError("bad")):
            result = _summarize_if_long(long_text)
            assert result.endswith("...")

    def test_17_executor_has_five_tools(self) -> None:
        from agent.agent import build_agent_executor

        assert len(build_agent_executor().tools) == 5

    def test_18_executor_names_unique(self) -> None:
        from agent.agent import build_agent_executor

        names = [tool.name for tool in build_agent_executor().tools]
        assert len(set(names)) == len(names)

    def test_19_prompt_mentions_tool_names(self) -> None:
        from agent.prompts import INVENTORY_AGENT_SYSTEM_PROMPT

        assert "get_low_stock_alerts" in INVENTORY_AGENT_SYSTEM_PROMPT
        assert "search_inventory_policy" in INVENTORY_AGENT_SYSTEM_PROMPT

    def test_20_prompt_mentions_domain_rules(self) -> None:
        from agent.prompts import INVENTORY_AGENT_SYSTEM_PROMPT

        lowered = INVENTORY_AGENT_SYSTEM_PROMPT.lower()
        assert "sku format" in lowered
        assert "po lifecycle" in lowered

    def test_21_tool_args_schema_product(self) -> None:
        from agent.tools import get_product_stock

        props = get_product_stock.args_schema.schema().get("properties", {})
        assert "product_id" in props

    def test_22_tool_args_schema_supplier(self) -> None:
        from agent.tools import get_supplier_catalog

        props = get_supplier_catalog.args_schema.schema().get("properties", {})
        assert "supplier_id" in props

    def test_23_tool_invoke_with_dict(self) -> None:
        from agent.tools import get_product_stock

        with patch("agent.tools._api_get", return_value={"error": "not_found"}):
            result = get_product_stock.invoke({"product_id": "77"})
            assert "77" in result

    def test_24_api_get_calls_requests_with_params(self) -> None:
        from agent.tools import _api_get

        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {"ok": True}

        with patch("agent.tools.requests.get", return_value=response) as mocked_get:
            _api_get("/products", params={"low_stock": "true"})
            args, kwargs = mocked_get.call_args
            assert "/products" in args[0]
            assert kwargs.get("params", {}).get("low_stock") == "true"

    def test_25_prompt_length_non_trivial(self) -> None:
        from agent.prompts import INVENTORY_AGENT_SYSTEM_PROMPT

        assert len(INVENTORY_AGENT_SYSTEM_PROMPT) >= 400
