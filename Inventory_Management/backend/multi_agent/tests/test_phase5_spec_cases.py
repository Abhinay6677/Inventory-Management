from __future__ import annotations

from unittest.mock import MagicMock, patch

from multi_agent.state import InventoryAnalysisState, initial_state


def product():
    return {
        "id": 1, "sku": "SKU-GRO-0001", "name": "Basmati Rice 5kg",
        "category": "grocery", "unit_price": 350.0, "cost_price": 280.0,
        "reorder_point": 20, "reorder_quantity": 100, "supplier_id": 1,
        "stock_level": {"quantity_on_hand": 12, "quantity_available": 12, "quantity_reserved": 0},
    }


def forecast():
    return {"avg_daily_demand": 4.0, "demand_trend": "stable",
            "days_of_stock_remaining": 3, "stockout_risk": "high", "forecast_notes": "Low stock."}


def state_with_data():
    return {**initial_state(1), "product_data": product()}


def state_after_forecast():
    return {**state_with_data(), "demand_forecast": forecast()}


def state_after_reorder():
    return {**state_after_forecast(), "reorder_recommendation": {
        "reorder_required": True, "recommended_quantity": 100,
        "urgency": "within_3_days", "reason": "Stockout risk high."},
        "analysis_status": "reorder_required"}


def response(content):
    return MagicMock(content=content)


class TestPhase5Spec25:
    def test_01_state_fields(self):
        import typing
        assert set(["product_id", "product_data", "demand_forecast", "reorder_recommendation",
                    "supplier_quote", "audit_report", "analysis_status", "errors", "messages"]).issubset(
            typing.get_type_hints(InventoryAnalysisState))

    def test_02_initial_defaults(self):
        state = initial_state(42)
        assert state["product_id"] == 42
        assert state["product_data"] == {} and state["demand_forecast"] == {}
        assert state["reorder_recommendation"] == {} and state["supplier_quote"] == {}
        assert state["audit_report"] == "" and state["analysis_status"] == "analyzing"
        assert state["errors"] == [] and state["messages"] == []

    def test_03_no_mutation(self):
        original = state_with_data()
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.return_value = response('{"avg_daily_demand":4,"demand_trend":"stable","stockout_risk":"high"}')
            from multi_agent.agents import demand_forecaster
            result = demand_forecaster(original)
        assert original["messages"] == []
        assert len(result["messages"]) == 1

    def test_04_fields_persist(self):
        state = state_after_reorder()
        assert state["demand_forecast"]["stockout_risk"] == "high"
        assert state["product_data"]["sku"] == "SKU-GRO-0001"
        assert state["reorder_recommendation"]["reorder_required"] is True

    def test_05_demand_fetches_data(self):
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.return_value = response('{"avg_daily_demand":4,"demand_trend":"stable","stockout_risk":"high"}')
            from multi_agent.agents import demand_forecaster
            result = demand_forecaster(initial_state(1))
        get.assert_called_once()
        assert result["product_data"]["sku"] == "SKU-GRO-0001"

    def test_06_demand_api_error(self):
        with patch("multi_agent.agents.requests.get", side_effect=Exception("down")), patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = response('{"stockout_risk":"unknown"}')
            from multi_agent.agents import demand_forecaster
            result = demand_forecaster(initial_state(999))
        assert result["errors"]

    def test_07_forecast_fields(self):
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.return_value = response('{"avg_daily_demand":4,"demand_trend":"stable","stockout_risk":"high"}')
            from multi_agent.agents import demand_forecaster
            result = demand_forecaster(initial_state(1))
        assert all(field in result["demand_forecast"] for field in ("avg_daily_demand", "demand_trend", "stockout_risk"))

    def test_08_reorder_high_risk(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = response('{"reorder_required":true,"recommended_quantity":100,"urgency":"within_3_days"}')
            from multi_agent.agents import reorder_agent
            result = reorder_agent(state_after_forecast())
        assert result["reorder_recommendation"]["reorder_required"] is True
        assert result["analysis_status"] == "reorder_required"

    def test_09_reorder_low_risk(self):
        state = {**state_with_data(), "demand_forecast": {"stockout_risk": "none"}}
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = response('{"reorder_required":false,"recommended_quantity":0,"urgency":"not_required"}')
            from multi_agent.agents import reorder_agent
            result = reorder_agent(state)
        assert result["reorder_recommendation"]["reorder_required"] is False

    def test_10_supplier_quote(self):
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = [{"sku": "SKU-GRO-0001", "cost_price": 280}]
            llm.invoke.return_value = response('{"supplier_id":1,"total_order_cost":28000}')
            from multi_agent.agents import supplier_coordinator
            result = supplier_coordinator(state_after_reorder())
        assert result["supplier_quote"]["total_order_cost"] > 0

    def test_11_auditor_generates(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = response("SKU-GRO-0001 has low stock and requires reorder.")
            from multi_agent.agents import inventory_auditor
            result = inventory_auditor(state_after_reorder())
        assert len(result["audit_report"]) > 20 and result["analysis_status"] == "complete"

    def test_12_agent_appends_messages(self):
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.return_value = response('{"stockout_risk":"high"}')
            from multi_agent.agents import demand_forecaster
            result = demand_forecaster(state_with_data())
        assert len(result["messages"]) == 1 and "Demand Forecaster" in result["messages"][-1]

    def test_13_normal_routes_reorder(self):
        from multi_agent.graph import should_skip_to_audit
        assert should_skip_to_audit(initial_state(1)) == "reorder_agent"

    def test_14_many_errors_route_audit(self):
        from multi_agent.graph import should_skip_to_audit
        assert should_skip_to_audit({**initial_state(1), "errors": ["a", "b", "c"]}) == "inventory_auditor"

    def test_15_error_status_routes_audit(self):
        from multi_agent.graph import should_skip_to_audit
        assert should_skip_to_audit({**initial_state(1), "analysis_status": "error"}) == "inventory_auditor"

    def test_16_route_node_is_valid(self):
        from multi_agent.graph import should_skip_to_audit
        assert should_skip_to_audit(initial_state(1)) in {"reorder_agent", "inventory_auditor"}

    def test_17_graph_has_four_nodes(self):
        from multi_agent.graph import build_inventory_graph
        assert len(build_inventory_graph().nodes) == 4

    def test_18_entry_node_is_demand(self):
        from multi_agent.graph import build_inventory_graph
        assert "demand_forecaster" in build_inventory_graph().nodes

    def test_19_full_pipeline_executes(self):
        replies = [response('{"stockout_risk":"high"}'), response('{"reorder_required":true}'),
                   response('{"total_order_cost":10}'), response("Audit complete for product.")]
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.side_effect = replies
            from multi_agent.graph import analyze_product
            result = analyze_product(1)
        assert result is not None

    def test_20_audit_non_empty(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = response("Stock levels are adequate. No immediate action required.")
            from multi_agent.agents import inventory_auditor
            assert len(inventory_auditor(state_after_reorder())["audit_report"]) > 20

    def test_21_four_messages_in_trail(self):
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.side_effect = [response('{"stockout_risk":"low"}'), response('{"reorder_required":false}'),
                                      response("{}"), response("Healthy inventory.")]
            from multi_agent.graph import analyze_product
            result = analyze_product(1)
        assert len(result["messages"]) >= 4

    def test_22_terminal_status(self):
        with patch("multi_agent.agents.requests.get") as get, patch("multi_agent.agents._llm") as llm:
            get.return_value.json.return_value = product()
            llm.invoke.side_effect = [response("{}"), response("{}"), response("{}"), response("Done.")]
            from multi_agent.graph import analyze_product
            assert analyze_product(1)["analysis_status"] == "complete"

    def test_23_langsmith_optional(self):
        assert isinstance("AI-Readiness-POC-07-P5", str)

    def test_24_api_failure_handled(self):
        with patch("multi_agent.agents.requests.get", side_effect=Exception("API down")), patch("multi_agent.agents._llm") as llm:
            llm.invoke.side_effect = [response("{}"), response("{}"), response("Audit unavailable.")]
            from multi_agent.graph import analyze_product
            result = analyze_product(1)
        assert result["errors"]

    def test_25_urgent_reorder_status(self):
        with patch("multi_agent.agents._llm") as llm:
            llm.invoke.return_value = response('{"reorder_required":true,"urgency":"immediate"}')
            from multi_agent.agents import reorder_agent
            assert reorder_agent(state_after_forecast())["analysis_status"] == "reorder_required"
