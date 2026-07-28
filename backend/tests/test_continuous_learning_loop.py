import unittest
import asyncio
from unittest.mock import patch, MagicMock

from app.trading.champion_challenger import ChampionChallengerEngine
from app.trading.walk_forward_tuner import WalkForwardHyperparameterAutoTuner
from app.ai.market_memory import MarketMemoryEngine
from app.trading.execution_analytics import ExecutionAnalyticsEngine
from app.ai.weekly_review import AutonomousWeeklyReviewEngine

class TestContinuousLearningLoop(unittest.TestCase):

    def test_strategy_tournament_2_scoring_and_matrix(self):
        engine = ChampionChallengerEngine.get_instance()
        matrix = engine.get_strategy_matrix_breakdown("Volatility Breakout")
        
        self.assertEqual(matrix["strategy"], "Volatility Breakout")
        self.assertIn("excellent on", matrix["insight"])
        self.assertIn("best_performance_combination", matrix)
        self.assertIn("weakest_performance_combination", matrix)

        context_mult = engine.get_contextual_capital_multiplier("Volatility Breakout", "BTCUSDT", "VOLATILE", "US")
        self.assertGreaterEqual(context_mult, 0.5)
        self.assertLessEqual(context_mult, 1.6)

    def test_continuous_walk_forward_pipeline(self):
        tuner = WalkForwardHyperparameterAutoTuner.get_instance()
        status = tuner.get_pipeline_status()
        self.assertIn("total_validations_conducted", status)

    def test_market_memory_engine(self):
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

    def test_autonomous_weekly_review(self):
        review_engine = AutonomousWeeklyReviewEngine.get_instance()
        
        async def _run():
            return await review_engine.generate_weekly_review(
                executed_trades_count=214,
                rejected_trades_count=418,
                exposure_guard_saved_usd=1820.0,
                confidence_filter_opportunity_cost_usd=430.0
            )

        report = asyncio.run(_run())
        self.assertIn("This week I executed 214 trades", report["executive_summary"])
        self.assertIn("Exposure guard saved approximately $1,820.00", report["executive_summary"])
        self.assertEqual(report["headline_metrics"]["trades_executed"], 214)

if __name__ == "__main__":
    unittest.main()
