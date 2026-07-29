import unittest
from app.trading.model_governance import ModelGovernanceEngine
from app.trading.capital_allocation import CapitalAllocationEngine
from app.trading.portfolio_attribution import PortfolioAttributionEngine
from app.trading.historical_replay import HistoricalReplayEngine

class TestModelGovernanceAndCapitalAllocation(unittest.TestCase):
    
    def setUp(self):
        self.governance = ModelGovernanceEngine.get_instance()
        self.capital = CapitalAllocationEngine.get_instance()
        self.attribution = PortfolioAttributionEngine.get_instance()
        self.replay = HistoricalReplayEngine.get_instance()

    def test_model_governance_lineage_and_checkpoint(self):
        # 1. Test decision lineage payload generation
        lineage = self.governance.get_decision_lineage(decision_type="TRADE_SIGNAL", extra_meta={"symbol": "BTCUSDT"})
        self.assertIn("lineage_id", lineage)
        self.assertIn("subsystem_versions", lineage)
        self.assertEqual(lineage["subsystem_versions"]["strategy_engine"], "v2.4.0")

        # 2. Test version checkpoint creation
        checkpoint = self.governance.create_version_checkpoint(
            new_versions={"strategy_engine": "v2.5.0", "portfolio_guard": "v2.0.0"},
            notes="Promoted v2.5.0 strategy engine"
        )
        self.assertIn("checkpoint_id", checkpoint)
        self.assertEqual(self.governance.active_versions["strategy_engine"], "v2.5.0")

        # 3. Test rollback execution
        rollback_res = self.governance.trigger_rollback(reason="Test manual governance rollback")
        self.assertTrue(rollback_res["rollback_triggered"])
        self.assertEqual(self.governance.active_versions["strategy_engine"], "v2.3.0")

    def test_dynamic_capital_allocation(self):
        # 1. Test portfolio rebalancing under VOLATILE regime
        rebal_volatile = self.capital.recalculate_strategy_allocations(
            total_equity=100000.0,
            current_regime="VOLATILE"
        )
        self.assertIn("allocations_pct", rebal_volatile)
        self.assertIn("Breakout Momentum", rebal_volatile["allocations_pct"])

        # Sum of allocations should equal 1.0 (or close with rounding)
        total_pct = sum(rebal_volatile["allocations_pct"].values())
        self.assertAlmostEqual(total_pct, 1.0, delta=0.01)

        # 2. Test strategy capital limit lookup
        limit = self.capital.get_strategy_capital_limit("Breakout Momentum", total_equity=100000.0)
        self.assertGreater(limit["max_allocated_usd"], 0.0)

    def test_portfolio_performance_attribution(self):
        lineage = self.governance.get_decision_lineage("TRADE_SIGNAL")
        
        self.attribution.record_trade_attribution(
            symbol="ETHUSDT",
            strategy="Breakout Momentum",
            realized_pnl=450.0,
            decision_lineage=lineage,
            subsystem_flags={"meta_reasoning_boosted": True, "self_adaptation_calibrated": True, "kelly_sized": True}
        )

        summary = self.attribution.get_attribution_summary()
        self.assertGreater(summary["total_tracked_trades"], 0)
        self.assertIn("subsystem_breakdown", summary)
        self.assertIn("meta_reasoning", summary["subsystem_breakdown"])

    def test_deterministic_version_replay_comparison(self):
        control_versions = {"strategy_engine": "v2.4.0"}
        challenger_versions = {"strategy_engine": "v2.5.0-rc1"}

        result = self.replay.run_deterministic_version_comparison(
            control_versions=control_versions,
            challenger_versions=challenger_versions,
            num_candles=30
        )

        self.assertIn("control", result)
        self.assertIn("challenger", result)
        self.assertIn("comparison", result)
        self.assertIn("recommendation", result["comparison"])

if __name__ == "__main__":
    unittest.main()
