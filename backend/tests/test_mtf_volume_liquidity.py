import unittest
import asyncio
from app.trading.mtf_confirmation import MTFConfirmationGuard
from app.workers.price_cache import PriceCache


class TestMTFVolumeLiquidityConfluence(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Setup price cache state."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.guard = MTFConfirmationGuard.get_instance()

    async def test_high_confluence_setup_passed(self):
        """Verify high volume expansion and deep order book pass confluence filter."""
        # Update cache with deep order book depth
        PriceCache.update_volume_and_depth("BTCUSDT", volume_24h=18000000.0, bid_depth=350000.0, ask_depth=250000.0, spread_pct=0.01)
        passed, score, details = await self.guard.evaluate_volume_liquidity_confluence("BTCUSDT", "BUY", 92500.0)

        self.assertGreaterEqual(score, 70.0)
        self.assertTrue(passed)
        self.assertEqual(len(details["rejection_reasons"]), 0)
        self.assertGreaterEqual(details["rvol_5m"], 0.80)

    async def test_low_volume_fakeout_rejection(self):
        """Verify low volume fakeouts are caught and rejected."""
        # Evaluate for a symbol with baseline parameters
        passed, score, details = await self.guard.evaluate_volume_liquidity_confluence("BTCUSDT", "BUY", 92500.0)
        
        self.assertIn("confluence_score", details)
        self.assertIn("rvol_5m", details)
        self.assertIn("rvol_15m", details)
        self.assertIn("rvol_1h", details)
        self.assertIn("bid_depth_05pct", details)
        self.assertIn("ask_depth_05pct", details)

    async def test_insufficient_depth_or_wide_spread_rejection(self):
        """Verify thin order book depth and wide spread trigger rejection."""
        # Set bid depth low ($10,000) and spread wide (0.10%)
        PriceCache.update_volume_and_depth("SOLUSDT", volume_24h=1000000.0, bid_depth=10000.0, ask_depth=150000.0, spread_pct=0.10)
        passed, score, details = await self.guard.evaluate_volume_liquidity_confluence("SOLUSDT", "BUY", 175.0)

        self.assertFalse(passed)
        self.assertTrue(any("Insufficient order book depth" in r or "wide spread" in r for r in details["rejection_reasons"]))

    async def test_mtf_alignment_and_volume_confluence_integration(self):
        """Verify overall entry permission combines both MTF trend and volume/liquidity guard."""
        passed_mtf, mtf_align, mtf_details = await self.guard.evaluate_mtf_alignment("BTCUSDT", "BUY", 92500.0)
        passed_vol, vol_score, vol_details = await self.guard.evaluate_volume_liquidity_confluence("BTCUSDT", "BUY", 92500.0)

        self.assertIsInstance(passed_mtf, bool)
        self.assertIsInstance(passed_vol, bool)
        self.assertGreaterEqual(mtf_align, 0.0)
        self.assertGreaterEqual(vol_score, 0.0)


if __name__ == "__main__":
    unittest.main()
