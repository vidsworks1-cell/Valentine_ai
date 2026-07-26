import unittest
import asyncio
from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
from app.workers.price_cache import PriceCache


class TestMacroRiskOverlayEngine(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Setup price cache and macro risk engine instance."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.engine = MacroRiskOverlayEngine.get_instance()
        self.engine.set_manual_event_lockdown(False)

    async def test_macro_risk_evaluation_payload(self):
        """Verify macro risk evaluation returns complete metric structure."""
        result = await self.engine.evaluate_macro_risk("BTCUSDT", "BUY")

        self.assertEqual(result["symbol"], "BTCUSDT")
        self.assertEqual(result["proposed_side"], "BUY")
        self.assertIn("macro_risk_score", result)
        self.assertIn("risk_level", result)
        self.assertIn("position_size_scaler", result)
        self.assertIn("recommended_leverage_cap", result)
        self.assertIn("metrics", result)

        metrics = result["metrics"]
        self.assertIn("btc_dominance_pct", metrics)
        self.assertIn("funding_annualized_pct", metrics)
        self.assertIn("oi_cascade_risk_score", metrics)

    async def test_manual_lockdown_activation(self):
        """Verify manual event lockdown forces CRITICAL_EVENT_LOCKDOWN and halts trading."""
        self.engine.set_manual_event_lockdown(True, "FOMC Announcement Lockout")
        result = await self.engine.evaluate_macro_risk("ETHUSDT", "BUY")

        self.assertFalse(result["trade_permitted"])
        self.assertEqual(result["risk_level"], "CRITICAL_EVENT_LOCKDOWN")
        self.assertEqual(result["position_size_scaler"], 0.0)
        self.assertGreaterEqual(result["macro_risk_score"], 85.0)
        self.assertTrue(result["metrics"]["manual_lockdown_engaged"])

        # Reset lockdown
        self.engine.set_manual_event_lockdown(False)

    async def test_position_scaler_modulation(self):
        """Verify position size scalers align with risk level thresholds."""
        res = await self.engine.evaluate_macro_risk("SOLUSDT", "BUY")
        
        scaler = res["position_size_scaler"]
        level = res["risk_level"]

        if level == "LOW_RISK":
            self.assertEqual(scaler, 1.0)
            self.assertEqual(res["recommended_leverage_cap"], 10)
        elif level == "MODERATE_RISK":
            self.assertEqual(scaler, 0.70)
            self.assertEqual(res["recommended_leverage_cap"], 5)
        elif level == "HIGH_MACRO_RISK":
            self.assertEqual(scaler, 0.35)
            self.assertEqual(res["recommended_leverage_cap"], 2)
        elif level == "CRITICAL_EVENT_LOCKDOWN":
            self.assertEqual(scaler, 0.0)
            self.assertEqual(res["recommended_leverage_cap"], 1)


if __name__ == "__main__":
    unittest.main()
