import unittest
import asyncio
from app.trading.volatility_squeeze import VolatilitySqueezeDetector
from app.workers.price_cache import PriceCache


class TestVolatilitySqueezeDetection(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Setup price cache state."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.detector = VolatilitySqueezeDetector.get_instance()

    async def test_squeeze_detection_structure(self):
        """Verify detector returns complete metrics payload."""
        info = self.detector.detect_squeeze("BTCUSDT", 92500.0)

        self.assertEqual(info["symbol"], "BTCUSDT")
        self.assertIn(info["squeeze_state"], ["SQUEEZE_ACTIVE", "SQUEEZE_FIRING", "NO_SQUEEZE"])
        self.assertIn("squeeze_ratio", info)
        self.assertIn("compression_score", info)
        self.assertIn("bollinger_bands", info)
        self.assertIn("keltner_channels", info)
        self.assertIn("rvol", info)
        self.assertIn("directional_bias", info)

    async def test_bollinger_vs_keltner_width_calculation(self):
        """Verify Bollinger Bands and Keltner Channels widths are computed correctly."""
        info = self.detector.detect_squeeze("ETHUSDT", 3500.0)

        bb = info["bollinger_bands"]
        kc = info["keltner_channels"]

        self.assertGreater(bb["upper"], bb["lower"])
        self.assertGreater(kc["upper"], kc["lower"])
        self.assertAlmostEqual(bb["width"], round(bb["upper"] - bb["lower"], 2), places=1)
        self.assertAlmostEqual(kc["width"], round(kc["upper"] - kc["lower"], 2), places=1)

    async def test_squeeze_state_classification(self):
        """Verify Squeeze states match calculated ratio thresholds."""
        info = self.detector.detect_squeeze("SOLUSDT", 180.0)

        squeeze_ratio = info["squeeze_ratio"]
        rvol = info["rvol"]
        state = info["squeeze_state"]

        if squeeze_ratio < 0.92:
            self.assertEqual(state, "SQUEEZE_ACTIVE")
            self.assertEqual(info["directional_bias"], "CONSOLIDATING")
        elif squeeze_ratio >= 0.92 and squeeze_ratio <= 1.15 and rvol >= 1.25:
            self.assertEqual(state, "SQUEEZE_FIRING")
            self.assertIn(info["directional_bias"], ["BUY", "SELL"])


if __name__ == "__main__":
    unittest.main()
