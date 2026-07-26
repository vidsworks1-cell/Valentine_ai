import unittest
import asyncio
from app.trading.cross_asset_correlation import CrossAssetCorrelationEngine
from app.workers.price_cache import PriceCache


class TestCrossAssetCorrelationEngine(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize price cache and correlation engine instance."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.cache.update_price("ETHUSDT", 3500.0)
        self.cache.update_price("SOLUSDT", 180.0)
        self.engine = CrossAssetCorrelationEngine.get_instance()

    async def test_correlation_matrix_structure_and_bounds(self):
        """Verify correlation matrix output structure and bounds [-1.0, 1.0]."""
        res = self.engine.calculate_correlation_matrix(["BTCUSDT", "ETHUSDT", "SOLUSDT"])

        self.assertIn("matrix", res)
        self.assertIn("contagion_spillover_index", res)
        self.assertIn("contagion_status", res)
        self.assertIn("decoupled_assets", res)

        matrix = res["matrix"]
        self.assertEqual(matrix["BTCUSDT"]["BTCUSDT"], 1.0)
        
        btc_eth_corr = matrix["BTCUSDT"]["ETHUSDT"]
        self.assertGreaterEqual(btc_eth_corr, -1.0)
        self.assertLessEqual(btc_eth_corr, 1.0)

    async def test_trade_correlation_risk_evaluation_no_active_positions(self):
        """Verify trade risk evaluation when portfolio is empty."""
        eval_res = self.engine.evaluate_trade_correlation_risk("SOLUSDT", [])

        self.assertEqual(eval_res["risk_flag"], "LOW")
        self.assertEqual(eval_res["position_scaler"], 1.0)
        self.assertEqual(eval_res["max_correlation"], 0.0)

    async def test_trade_correlation_risk_evaluation_with_correlated_positions(self):
        """Verify position scaling when proposed asset is correlated with active positions."""
        eval_res = self.engine.evaluate_trade_correlation_risk("ETHUSDT", ["BTCUSDT", "SOLUSDT"])

        self.assertIn(eval_res["risk_flag"], ["LOW_CORRELATION", "MODERATE_CORRELATION", "HIGH_CONCENTRATION_RISK"])
        self.assertGreater(eval_res["position_scaler"], 0.0)
        self.assertLessEqual(eval_res["position_scaler"], 1.0)


if __name__ == "__main__":
    unittest.main()
