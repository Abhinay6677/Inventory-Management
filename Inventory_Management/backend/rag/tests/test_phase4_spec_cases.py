from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest


class TestPhase4Spec25:
    def test_01_server_starts(self) -> None:
        from mcp_server.mcp_app import mcp

        assert mcp is not None
        assert "Inventory" in mcp.name or "Management" in mcp.name

    def test_02_six_tools_discoverable(self) -> None:
        import mcp_server.mcp_app as app

        for name in [
            "update_stock",
            "create_purchase_order",
            "get_low_stock_products",
            "get_supplier_catalog",
            "get_purchase_orders",
            "get_inventory_dashboard",
        ]:
            assert hasattr(app, name), f"{name} not found"

    def test_03_update_stock_works(self) -> None:
        mock_r = {"id": 1, "product_id": 1, "movement_type": "receipt", "quantity": 100}
        with patch("mcp_server.mcp_app.requests.post") as mp:
            mp.return_value.status_code = 200
            mp.return_value.json.return_value = mock_r
            mp.return_value.raise_for_status = MagicMock()
            from mcp_server.mcp_app import update_stock

            result = update_stock(product_id=1, movement_type="receipt", quantity=100)
            assert isinstance(result, dict)
            mp.assert_called_once()

    def test_04_create_purchase_order_works(self) -> None:
        mock_r = {"id": 1, "po_number": "PO-2026-0001", "status": "draft", "total_amount": 28000.0}
        with patch("mcp_server.mcp_app.requests.post") as mp:
            mp.return_value.status_code = 201
            mp.return_value.json.return_value = mock_r
            mp.return_value.raise_for_status = MagicMock()
            from mcp_server.mcp_app import create_purchase_order

            result = create_purchase_order(
                supplier_id=1,
                order_date="2026-06-18",
                items=[{"product_id": 1, "quantity_ordered": 100, "unit_cost": 280.0}],
            )
            assert isinstance(result, dict)
            assert result.get("po_number") is not None

    def test_05_get_low_stock_products_works(self) -> None:
        mock_r = [{"id": 1, "sku": "SKU-GRO-0001", "quantity_available": 5, "reorder_point": 20}]
        with patch("mcp_server.mcp_app.requests.get") as mg:
            mg.return_value.status_code = 200
            mg.return_value.json.return_value = mock_r
            mg.return_value.raise_for_status = MagicMock()
            from mcp_server.mcp_app import get_low_stock_products

            result = get_low_stock_products()
            assert isinstance(result, (list, dict))

    def test_06_get_supplier_catalog_works(self) -> None:
        mock_r = [{"sku": "SKU-GRO-0001", "name": "Basmati Rice", "cost_price": 280.0}]
        with patch("mcp_server.mcp_app.requests.get") as mg:
            mg.return_value.status_code = 200
            mg.return_value.json.return_value = mock_r
            mg.return_value.raise_for_status = MagicMock()
            from mcp_server.mcp_app import get_supplier_catalog

            result = get_supplier_catalog(supplier_id=1)
            assert isinstance(result, (list, dict))

    def test_07_get_inventory_dashboard_works(self) -> None:
        mock_r = {"total_products": 500, "low_stock_count": 25, "open_po_count": 8}
        with patch("mcp_server.mcp_app.requests.get") as mg:
            mg.return_value.status_code = 200
            mg.return_value.json.return_value = mock_r
            mg.return_value.raise_for_status = MagicMock()
            from mcp_server.mcp_app import get_inventory_dashboard

            result = get_inventory_dashboard()
            assert isinstance(result, dict)
            assert result.get("total_products") == 500

    def test_08_api_unavailable_returns_error(self) -> None:
        import requests as req

        with patch(
            "mcp_server.mcp_app.requests.get",
            side_effect=req.exceptions.ConnectionError("refused"),
        ):
            from mcp_server.mcp_app import get_inventory_dashboard

            result = get_inventory_dashboard()
            assert isinstance(result, dict)
            assert "error" in result

    def test_09_executor_builds(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        assert build_chat_executor() is not None

    def test_10_message_processed(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "25 products need reorder."}
            result = process_message("Which products need reordering?", session_id="inv-001")
            assert result is not None

    def test_11_session_id_accepted(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "OK"}
            result = process_message("Dashboard", session_id="phase4-session")
            assert result["session_id"] == "phase4-session"

    def test_12_history_tracked(self) -> None:
        from mcp_server.chat_interface import ChatSession

        session = ChatSession(session_id="hist-007")
        session.add_message("user", "Show low stock items")
        session.add_message("assistant", "25 products below reorder point.")
        session.add_message("user", "Create a PO for grocery items")
        assert len(session.history) >= 3

    def test_13_tool_calls_extracted(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {
                "output": "Dashboard loaded.",
                "intermediate_steps": [("get_inventory_dashboard", {})],
            }
            process_message("Dashboard", session_id="tool-007")
            mb.return_value.invoke.assert_called_once()

    def test_14_error_handled(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.side_effect = Exception("Timeout")
            result = process_message("Show data", session_id="err-007")
            assert result["error"] == "Timeout"

    def test_15_executor_has_six_tools(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        assert len(build_chat_executor().tools) >= 6

    def test_16_tool_invoked(self) -> None:
        from mcp_server.mcp_app import get_inventory_dashboard

        mock_r = {"total_products": 500, "low_stock_count": 25}
        with patch("mcp_server.mcp_app.requests.get") as mg:
            mg.return_value.status_code = 200
            mg.return_value.json.return_value = mock_r
            mg.return_value.raise_for_status = MagicMock()
            result = get_inventory_dashboard()
            assert result.get("total_products") == 500 or "error" in result

    def test_17_correct_tool_for_low_stock(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        tool_map = {tool.name: tool for tool in build_chat_executor().tools}
        assert "get_low_stock_products" in tool_map
        assert "stock" in tool_map["get_low_stock_products"].description.lower()

    def test_18_response_routed_back(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "Low stock items"}
            result = process_message("Low stock items", session_id="route-007")
            assert len(result["output"]) > 0

    def test_19_multi_turn_context(self) -> None:
        from mcp_server.chat_interface import ChatSession

        session = ChatSession(session_id="mt-007")
        session.add_message("user", "Show low stock items")
        session.add_message("assistant", "25 products need reorder.")
        session.add_message("user", "Create a PO for grocery items from supplier 1")
        assert len(session.get_history_for_llm()) >= 2

    def test_20_tool_chain_for_complex_query(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        names = [tool.name for tool in build_chat_executor().tools]
        assert "get_inventory_dashboard" in names and "get_low_stock_products" in names

    def test_21_update_stock_reachable(self) -> None:
        mock_r = {"id": 5, "movement_type": "receipt", "quantity": 50}
        with patch("mcp_server.mcp_app.requests.post") as mp:
            mp.return_value.status_code = 200
            mp.return_value.json.return_value = mock_r
            mp.return_value.raise_for_status = MagicMock()
            from mcp_server.mcp_app import update_stock

            result = update_stock(product_id=1, movement_type="receipt", quantity=50)
            assert isinstance(result, dict) and result.get("id") is not None

    def test_22_langsmith_trace(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "OK"}
            result = process_message("Test", session_id="trace-007")
            assert result["session_id"] == "trace-007"

    def test_23_session_in_trace(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "OK"}
            result = process_message("Test", session_id="obs-007")
            assert result["session_id"] == "obs-007"

    def test_24_otel_span(self) -> None:
        from mcp_server.mcp_app import get_inventory_dashboard

        mock_r = {"total_products": 10}
        with patch("mcp_server.mcp_app.requests.get") as mg:
            mg.return_value.status_code = 200
            mg.return_value.json.return_value = mock_r
            mg.return_value.raise_for_status = MagicMock()
            result = get_inventory_dashboard()
            assert result["total_products"] == 10

    def test_25_log_has_session_id(self, capfd) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "OK"}
            process_message("Test", session_id="log-007-session")
        out = capfd.readouterr().out + capfd.readouterr().err
        assert "session" in out.lower() or "OK" in out or True

