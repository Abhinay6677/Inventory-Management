from __future__ import annotations

from unittest.mock import MagicMock, patch


class TestPhase4Additional25:
    def test_01_get_helper_connection_error(self) -> None:
        import requests
        from mcp_server.mcp_app import _get

        with patch("mcp_server.mcp_app.requests.get", side_effect=requests.exceptions.ConnectionError("down")):
            result = _get("/dashboard")
            assert result.get("error") == "API unavailable"

    def test_02_post_helper_connection_error(self) -> None:
        import requests
        from mcp_server.mcp_app import _post

        with patch("mcp_server.mcp_app.requests.post", side_effect=requests.exceptions.ConnectionError("down")):
            result = _post("/orders", {"a": 1})
            assert result.get("error") == "API unavailable"

    def test_03_update_stock_payload(self) -> None:
        from mcp_server.mcp_app import update_stock

        with patch("mcp_server.mcp_app.requests.post") as mp:
            mp.return_value.status_code = 200
            mp.return_value.json.return_value = {"ok": True}
            mp.return_value.raise_for_status = MagicMock()
            update_stock(3, "sale", 4, reference_number="R-1", notes="adjustment")
            args, kwargs = mp.call_args
            assert "/products/3/stock" in args[0]
            assert kwargs["json"]["movement_type"] == "sale"
            assert kwargs["json"]["reference_number"] == "R-1"

    def test_04_create_purchase_order_expected_delivery(self) -> None:
        from mcp_server.mcp_app import create_purchase_order

        with patch("mcp_server.mcp_app.requests.post") as mp:
            mp.return_value.status_code = 201
            mp.return_value.json.return_value = {"po_number": "PO-1"}
            mp.return_value.raise_for_status = MagicMock()
            create_purchase_order(2, "2026-06-18", [{"product_id": 1, "quantity_ordered": 2, "unit_cost": 3.0}], "2026-06-20")
            assert mp.call_args.kwargs["json"]["expected_delivery"] == "2026-06-20"

    def test_05_get_purchase_orders_params(self) -> None:
        from mcp_server.mcp_app import get_purchase_orders

        with patch("mcp_server.mcp_app.requests.get") as mg:
            mg.return_value.status_code = 200
            mg.return_value.json.return_value = []
            mg.return_value.raise_for_status = MagicMock()
            get_purchase_orders(status="draft", supplier_id=7)
            assert mg.call_args.kwargs["params"]["status"] == "draft"
            assert mg.call_args.kwargs["params"]["supplier_id"] == 7

    def test_06_chat_session_init(self) -> None:
        from mcp_server.chat_interface import ChatSession

        session = ChatSession("s-1")
        assert session.session_id == "s-1"
        assert session.history == []

    def test_07_chat_session_order(self) -> None:
        from mcp_server.chat_interface import ChatSession

        session = ChatSession("s-2")
        session.add_message("user", "one")
        session.add_message("assistant", "two")
        assert session.history[0]["content"] == "one"
        assert session.history[1]["content"] == "two"

    def test_08_chat_history_limit(self) -> None:
        from mcp_server.chat_interface import ChatSession

        session = ChatSession("s-3")
        for idx in range(12):
            session.add_message("user", str(idx))
        assert len(session.get_history_for_llm()) == 10
        assert session.get_history_for_llm()[0]["content"] == "2"

    def test_09_tool_schema_properties(self) -> None:
        from mcp_server._compat import ToolSpec
        from mcp_server.mcp_app import get_supplier_catalog

        tool = ToolSpec(get_supplier_catalog)
        props = tool.args_schema.schema()["properties"]
        assert "supplier_id" in props
        assert props["supplier_id"]["type"] in {"integer", "string"}

    def test_10_tool_invoke_single_arg(self) -> None:
        from mcp_server._compat import ToolSpec

        def demo(value: str) -> str:
            return f"value={value}"

        assert ToolSpec(demo).invoke("x") == "value=x"

    def test_11_tool_invoke_multi_arg(self) -> None:
        from mcp_server._compat import ToolSpec

        def demo(a: str, b: str) -> str:
            return f"{a}-{b}"

        assert ToolSpec(demo).invoke({"a": "1", "b": "2"}) == "1-2"

    def test_12_executor_tools_unique(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        names = [tool.name for tool in build_chat_executor().tools]
        assert len(names) == len(set(names))

    def test_13_executor_descriptions_present(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        for tool in build_chat_executor().tools:
            assert len(tool.description) > 20

    def test_14_routing_dashboard(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        with patch("mcp_server.chat_interface.get_inventory_dashboard", return_value={"total_products": 1}) as mocked:
            result = build_chat_executor().invoke({"input": "Show dashboard metrics"})
            assert mocked.called
            assert "total_products" in result["output"]

    def test_15_routing_low_stock(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        with patch("mcp_server.chat_interface.get_low_stock_products", return_value=[{"sku": "SKU-1"}]) as mocked:
            result = build_chat_executor().invoke({"input": "Need low stock items"})
            assert mocked.called
            assert "SKU-1" in result["output"]

    def test_16_routing_supplier_catalog(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        with patch("mcp_server.chat_interface.get_supplier_catalog", return_value=[{"sku": "SKU-2"}]) as mocked:
            result = build_chat_executor().invoke({"input": "Show supplier 2 catalog"})
            assert mocked.called
            assert "SKU-2" in result["output"]

    def test_17_routing_create_po(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        with patch("mcp_server.chat_interface.create_purchase_order", return_value={"po_number": "PO-9"}) as mocked:
            result = build_chat_executor().invoke({"input": "Create a purchase order for supplier 9"})
            assert mocked.called
            assert "PO-9" in result["output"]

    def test_18_routing_list_po(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        with patch("mcp_server.chat_interface.get_purchase_orders", return_value=[{"status": "draft"}]) as mocked:
            result = build_chat_executor().invoke({"input": "List draft purchase orders"})
            assert mocked.called
            assert "draft" in result["output"]

    def test_19_routing_stock_update(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        with patch("mcp_server.chat_interface.update_stock", return_value={"id": 1}) as mocked:
            result = build_chat_executor().invoke({"input": "Update stock receipt 15"})
            assert mocked.called
            assert "1" in result["output"]

    def test_20_process_message_records_history(self) -> None:
        from mcp_server.chat_interface import _CHAT_SESSIONS, process_message

        _CHAT_SESSIONS.pop("history-1", None)
        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "OK"}
            process_message("dashboard", session_id="history-1")
        assert len(_CHAT_SESSIONS["history-1"].history) == 2

    def test_21_process_message_empty_input(self) -> None:
        from mcp_server.chat_interface import process_message

        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "dashboard"}
            result = process_message("", session_id="empty-1")
            assert result["session_id"] == "empty-1"

    def test_22_process_message_session_persists(self) -> None:
        from mcp_server.chat_interface import _CHAT_SESSIONS, process_message

        _CHAT_SESSIONS.pop("persist-1", None)
        with patch("mcp_server.chat_interface.build_chat_executor") as mb:
            mb.return_value.invoke.return_value = {"output": "first"}
            process_message("first", session_id="persist-1")
            process_message("second", session_id="persist-1")
        assert len(_CHAT_SESSIONS["persist-1"].history) == 4

    def test_23_mcp_registry_collects_tools(self) -> None:
        from mcp_server.mcp_app import mcp

        assert hasattr(mcp, "tools")
        assert len(mcp.tools) >= 6

    def test_24_mcp_run_callable(self) -> None:
        from mcp_server.mcp_app import mcp

        assert callable(mcp.run)
        assert mcp.run() is None

    def test_25_rendered_output_is_string(self) -> None:
        from mcp_server.chat_interface import build_chat_executor

        result = build_chat_executor().invoke({"input": "Show dashboard"})
        assert isinstance(result["output"], str)

