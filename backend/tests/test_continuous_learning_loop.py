import unittest
import asyncio

from app.ai.experiment_framework import ExperimentFramework
from app.ai.adaptive_validation import AdaptiveChangeValidationEngine
from app.trading.system_harmony import SystemHarmonyEngine
from app.ai.market_memory import MarketMemoryEngine
from app.trading.execution_analytics import ExecutionAnalyticsEngine

class TestContinuousLearningAndHarmony(unittest.TestCase):

    def test_experiment_framework_ab_paper_testing(self):
        ef = ExperimentFramework.get_instance()
        summary = ef.get_all_experiments_summary()
        self.assertGreaterEqual(summary["active_experiments_count"], 1)

        exp_details = ef.get_experiment_details("EXP-CONFIDENCE-001")
        self.assertIn("evaluation_summary", exp_details)
        self.assertIn("recommendation", exp_details["evaluation_summary"])

    def test_adaptive_change_validation_impact_proof(self):
        engine = AdaptiveChangeValidationEngine.get_instance()
        summary = engine.get_validation_summary()
        self.assertGreaterEqual(summary["validated_proofs_count"], 2)

        proofs = summary["proof_history"]
        self.assertEqual(proofs[0]["verdict"], "VALIDATED_IMPROVEMENT")
        self.assertIn("delta_sharpe_pct", proofs[0]["impact_deltas"])

    def test_system_harmony_lineage_and_conflict_simulation(self):
        harmony = SystemHarmonyEngine.get_instance()
        
        # Test Decision Lineage
        app, risk, trace = harmony.harmonize_trade_decision(
            trade_id="TRD-TEST-001",
            opportunity={"symbol": "BTCUSDT", "side": "BUY", "strategy_used": "Breakout Momentum", "entry_price": 90000.0},
            base_risk_pct=0.02,
            meta_score=88.0,
            meta_approved=True,
            meta_pillar_breakdown={"technical": 90.0, "sentiment": 86.0},
            pg_approved=True,
            pg_action="APPROVE",
            pg_reason="Portfolio risk healthy",
            pg_adjusted_risk_pct=0.02,
            memory_modifier=1.20,
            memory_key="HIGH_POSITIVE|NORMAL_VOLATILITY|RISING|TRENDING",
            personality_name="Short Squeeze Rally & Alt Season Impulse",
            tournament_elo=1680,
            matrix_multiplier=1.15,
            execution_routing="MARKET"
        )

        self.assertTrue(app)
        self.assertGreater(risk, 0.0)
        self.assertEqual(trace.trade_id, "TRD-TEST-001")

        # Test Lineage Query
        retrieved_trace = harmony.get_lineage_trace("TRD-TEST-001")
        self.assertIsNotNone(retrieved_trace)
        self.assertEqual(retrieved_trace["trade_id"], "TRD-TEST-001")

        # Test Conflict Simulation Suite (100 Scenarios)
        sim_res = harmony.run_conflict_simulation_suite(n_scenarios=100)
        self.assertEqual(sim_res["total_scenarios_simulated"], 100)
        self.assertEqual(sim_res["deadlocks_or_unhandled_errors"], 0)
        self.assertEqual(sim_res["system_harmony_health"], "EXCELLENT")

    def test_market_memory_suitability(self):
        memory = MarketMemoryEngine.get_instance()
        suitability = memory.get_environment_suitability_modifier(
            strategy="Momentum",
            funding_rate=0.0005,
            volatility_atr=0.006,
            btc_dom_change_pct=0.005,
            market_regime="RANGING"
        )
        self.assertIn("environment_key", suitability)
        self.assertIn("capital_modifier", suitability)
        self.assertGreater(suitability["capital_modifier"], 0)

    def test_execution_quality_score_diagnosis(self):
        analytics = ExecutionAnalyticsEngine.get_instance()
        quality = analytics.calculate_trade_execution_quality(
            trade_id="101",
            symbol="BTCUSDT",
            side="BUY",
            strategy="Breakout Momentum",
            ordered_price=90000.0,
            fill_price=90005.0,
            exit_price=92000.0,
            stop_loss=89000.0,
            take_profit=92500.0,
            pnl=2000.0,
            max_favorable_excursion=2200.0,
            max_adverse_excursion=100.0,
            holding_time_seconds=900.0
        )
        self.assertEqual(quality["trade_id"], "101")
        self.assertGreaterEqual(quality["overall_execution_quality_score"], 0)
        self.assertLessEqual(quality["overall_execution_quality_score"], 100)
        self.assertIn(quality["root_cause_diagnosis"], ["EXCELLENT_EXECUTION", "BAD_ANALYSIS", "POOR_EXECUTION", "BAD_RISK_MANAGEMENT", "NEUTRAL"])

    def test_autonomous_research_agent(self):
        from app.ai.autonomous_research_agent import AutonomousResearchAgent
        agent = AutonomousResearchAgent.get_instance()
        
        # Test summary
        summary = agent.get_research_agent_summary()
        self.assertGreaterEqual(summary["total_hypotheses_generated"], 3)

        # Test hypothesis discovery
        discovered = agent.generate_new_hypotheses()
        self.assertEqual(len(discovered), 2)

        # Test paper experiment design from hypothesis
        exp_design = agent.design_paper_experiment_from_hypothesis("HYP-2026-001")
        self.assertIn("experiment_registered", exp_design)

        # Test statistical evaluation
        eval_res = agent.evaluate_hypothesis_statistical_significance("HYP-2026-001")
        self.assertIn("statistical_test_results", eval_res)
        self.assertIn("recommendation", eval_res)

        # Test production adoption
        adoption = agent.adopt_hypothesis_into_production("HYP-2026-001")
        self.assertIn("status", adoption)
        self.assertEqual(adoption["status"], "PROMOTED_TO_PRODUCTION")


if __name__ == "__main__":
    unittest.main()
