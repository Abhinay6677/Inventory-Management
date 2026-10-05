from __future__ import annotations

from unittest.mock import MagicMock, patch

from multi_agent.agents import _safe_json
from multi_agent.state import initial_state


class TestPhase5Additional25:
    def test_01_safe_json_object(self):
        assert _safe_json('{"a": 1}') == {"a": 1}

    def test_02_safe_json_markdown(self):
        assert _safe_json("```json\n{\"risk\":\"high\"}\n```")["risk"] == "high"

    def test_03_safe_json_prose(self):
        assert _safe_json("Result: {\"ok\": true}")["ok"] is True

    def test_04_safe_json_empty(self):
        assert _safe_json("") == {}

    def test_05_safe_json_invalid(self):
        assert _safe_json("{not json}") == {}

    def test_06_safe_json_array(self):
        assert _safe_json("[1, 2]") == {}

    def test_07_initial_state_isolated_lists(self):
        first, second = initial_state(1), initial_state(2)
        first["messages"].append("x")
        assert second["messages"] == []

    def test_08_initial_state_isolated_dicts(self):
        first, second = initial_state(1), initial_state(2)
        first["product_data"]["sku"] = "X"
        assert second["product_data"] == {}

    def test_09_product_id_preserved(self):
        assert initial_state(99)["product_id"] == 99

    def test_10_demand_keeps_existing_errors(self):
        state = {**initial_state(1), "errors": ["prior"]}
        with patch("multi_agent.agents.requests.get", side_effect=Exception("down")), patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="{}")
            from multi_agent.agents import demand_forecaster
            assert "prior" in demand_forecaster(state)["errors"]

    def test_11_demand_keeps_existing_messages(self):
        state = {**initial_state(1), "messages": ["started"]}
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = {}
            llm.invoke.return_value = MagicMock(content="{}")
            from multi_agent.agents import demand_forecaster
            assert demand_forecaster(state)["messages"][0] == "started"

    def test_12_reorder_keeps_product(self):
        state = {**initial_state(1), "product_data": {"sku": "X"}, "demand_forecast": {}}
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="{}")
            from multi_agent.agents import reorder_agent
            assert reorder_agent(state)["product_data"]["sku"] == "X"

    def test_13_reorder_false_preserves_analyzing(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content='{"reorder_required":false}')
            from multi_agent.agents import reorder_agent
            assert reorder_agent(initial_state(1))["analysis_status"] == "analyzing"

    def test_14_reorder_true_sets_status(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content='{"reorder_required":true}')
            from multi_agent.agents import reorder_agent
            assert reorder_agent(initial_state(1))["analysis_status"] == "reorder_required"

    def test_15_supplier_without_supplier_id_skips_fetch(self):
        state = {**initial_state(1), "product_data": {}, "reorder_recommendation": {}}
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="{}")
            from multi_agent.agents import supplier_coordinator
            supplier_coordinator(state)
        get.assert_not_called()

    def test_16_supplier_preserves_errors(self):
        state = {**initial_state(1), "product_data": {"supplier_id": 3}, "errors": ["prior"]}
        with patch("multi_agent.agents.requests.get", side_effect=Exception("down")), patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="{}")
            from multi_agent.agents import supplier_coordinator
            assert set(["prior"]).issubset(supplier_coordinator(state)["errors"])

    def test_17_supplier_quote_has_fallback(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="not json")
            from multi_agent.agents import supplier_coordinator
            result = supplier_coordinator({**initial_state(1), "product_data": {"supplier_id": 4}})
        assert result["supplier_quote"]["supplier_id"] == 4

    def test_18_auditor_sets_complete(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="ok")
            from multi_agent.agents import inventory_auditor
            assert inventory_auditor(initial_state(1))["analysis_status"] == "complete"

    def test_19_auditor_fallback_report(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="")
            from multi_agent.agents import inventory_auditor
            assert inventory_auditor(initial_state(1))["audit_report"]

    def test_20_auditor_llm_error_surfaces_in_report(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.side_effect = RuntimeError("model down")
            from multi_agent.agents import inventory_auditor
            assert "Audit error" in inventory_auditor(initial_state(1))["audit_report"]

    def test_21_auditor_appends_message(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = MagicMock(content="audit")
            from multi_agent.agents import inventory_auditor
            assert "Inventory Auditor" in inventory_auditor(initial_state(1))["messages"][-1]

    def test_22_router_errors_below_threshold(self):
        from multi_agent.graph import should_skip_to_audit
        assert should_skip_to_audit({**initial_state(1), "errors": ["one", "two"]}) == "reorder_agent"

    def test_23_router_error_status_wins(self):
        from multi_agent.graph import should_skip_to_audit
        assert should_skip_to_audit({**initial_state(1), "errors": [], "analysis_status": "error"}) == "inventory_auditor"

    def test_24_graph_is_invokable(self):
        from multi_agent.graph import build_inventory_graph
        assert callable(build_inventory_graph().invoke)

    def test_25_pipeline_handles_model_failures(self):
        with patch("multi_agent.agents.requests.get", side_effect=Exception("api")), patch("multi_agent.agents._llm") as llm:
            llm.invoke.side_effect = RuntimeError("model")
            from multi_agent.graph import analyze_product
            result = analyze_product(1)
        assert result["analysis_status"] == "complete"
        assert result["errors"]
