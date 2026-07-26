import unittest
import asyncio
from app.trading.trading_persona import TradingPersonaPresetEngine
from app.trading.walk_forward_tuner import WalkForwardHyperparameterAutoTuner
from app.workers.price_cache import PriceCache


class TestTradingPersonaAndWalkForwardTuner(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize price cache and engine instances."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.persona_engine = TradingPersonaPresetEngine.get_instance()
        self.tuner = WalkForwardHyperparameterAutoTuner.get_instance()

    async def test_trading_persona_presets_selection(self):
        """Verify selecting presets updates active persona parameters."""
        presets = self.persona_engine.get_all_presets()
        self.assertIn("CONSERVATIVE_MACRO_SWING", presets["presets"])
        self.assertIn("AGGRESSIVE_MOMENTUM_SCALPER", presets["presets"])
        self.assertIn("DELTA_NEUTRAL_ARBITRAGE", presets["presets"])

        # Switch to Aggressive Momentum Scalper
        active_agg = self.persona_engine.set_active_persona("AGGRESSIVE_MOMENTUM_SCALPER")
        self.assertEqual(active_agg["key"], "AGGRESSIVE_MOMENTUM_SCALPER")
        self.assertEqual(active_agg["min_bayesian_confidence"], 0.72)
        self.assertEqual(active_agg["max_leverage"], 10)

        # Switch back to Conservative Macro Swing
        active_cons = self.persona_engine.set_active_persona("CONSERVATIVE_MACRO_SWING")
        self.assertEqual(active_cons["key"], "CONSERVATIVE_MACRO_SWING")
        self.assertEqual(active_cons["min_bayesian_confidence"], 0.82)

    async def test_walk_forward_hyperparameter_optimization(self):
        """Verify walk-forward tuner executes grid search optimization and evaluates out-of-sample metrics."""
        res = await self.tuner.run_walk_forward_optimization(
            symbol="BTCUSDT",
            num_candles=60,
            confidence_grid=[0.75, 0.82],
            sl_atr_grid=[1.5, 2.0],
            tp_atr_grid=[3.0, 4.0]
        )

        self.assertEqual(res["symbol"], "BTCUSDT")
        self.assertIn("optimal_hyperparameters", res)
        self.assertIn("best_candidate_metrics", res)
        self.assertGreaterEqual(res["total_candidates_evaluated"], 4)

        opt_params = res["optimal_hyperparameters"]
        self.assertIn("min_bayesian_confidence", opt_params)
        self.assertIn("sl_atr_multiplier", opt_params)
        self.assertIn("tp_atr_multiplier", opt_params)

        # Test Auto-apply to CUSTOM_QUANT persona
        applied_res = self.tuner.auto_apply_optimal_parameters()
        self.assertTrue(applied_res["auto_applied"])
        self.assertEqual(applied_res["active_persona"]["key"], "CUSTOM_QUANT")


if __name__ == "__main__":
    unittest.main()
