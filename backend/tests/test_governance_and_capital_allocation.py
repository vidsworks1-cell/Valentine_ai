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

    def test_mtf_guard_and_confidence_experiments(self):
        # 1. Test MTF Replay Experiment
        mtf_exp = self.replay.run_mtf_guard_replay_experiment(num_candles=40)
        self.assertIn("policy_results", mtf_exp)
        self.assertIn("DYNAMIC_STRATEGY_WEIGHTED", mtf_exp["policy_results"])
        self.assertIn("recommended_policy", mtf_exp["experiment_summary"])

        # 2. Test Confidence Threshold Experiment
        conf_exp = self.replay.run_confidence_threshold_replay_experiment(thresholds=[0.72, 0.71, 0.70], num_candles=40)
        self.assertIn("threshold_evaluations", conf_exp)
        self.assertIn("threshold_0.71", conf_exp["threshold_evaluations"])
        self.assertIn("optimal_threshold", conf_exp["experiment_summary"])

        # 3. Test Interaction Stack Analytics & Granular Subsystem Breakdown
        self.attribution.record_interaction_stack_evaluation(applied_scalers_count=3, cumulative_scale_factor=0.35)
        summary = self.attribution.get_attribution_summary()
        self.assertIn("interaction_stack_stats", summary)
        self.assertIn("mtf_guard", summary["subsystem_breakdown"])
        self.assertIn("winners_prevented", summary["subsystem_breakdown"]["mtf_guard"])
        self.assertIn("net_pnl_impact_usd", summary["subsystem_breakdown"]["mtf_guard"])

        # 4. Test Replay Self-Calibration Shadow Governance & Stabilization
        from app.ai.self_adaptation import SelfAdaptationEngine
        engine = SelfAdaptationEngine.get_instance()
        calib_res = engine.auto_calibrate_from_replay(force_promote=True)
        self.assertIn("governance_stage", calib_res)
        self.assertIn("shadow_policy", calib_res)
        self.assertEqual(calib_res["shadow_policy"]["status"], "PROMOTED_LIVE")


    def test_async_mtf_strategy_weighted_guard(self):
        import asyncio
        from app.trading.mtf_confirmation import MTFConfirmationGuard
        async def _run():
            guard = MTFConfirmationGuard.get_instance()
            passed, score, details = await guard.evaluate_mtf_alignment(
                symbol="BTCUSDT",
                side="BUY",
                price=65000.0,
                strategy="Mean Reversion",
                conviction_score=82.0
            )
            self.assertIn("weighted_alignment_pct", details)
            self.assertEqual(details["weight_type"], "MEAN_REVERSION_WEIGHTED")
        asyncio.run(_run())

if __name__ == "__main__":
    unittest.main()
