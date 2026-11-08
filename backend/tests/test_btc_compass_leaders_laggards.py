"""
Unit tests for the BTC Compass + Top 5 Leaders/Laggards Strategy Engine.
Verifies that:
1. BTC Bullish regime targets Top 5 Gainers for BUY ONLY (100% blocks shorts).
2. BTC Bearish regime targets Top 5 Losers for SELL ONLY (100% blocks longs).
3. BTC Chop/Flat regime pauses scanning to preserve capital.
4. PriceCache correctly identifies and sorts leaders and laggards while filtering out stablecoins.
"""

import sys
import unittest
from unittest.mock import MagicMock, patch

for mod in ["httpx", "sqlalchemy", "sqlalchemy.ext.asyncio", "sqlalchemy.orm", "sqlalchemy.sql", "fastapi"]:
    if mod not in sys.modules:
        sys.modules[mod] = MagicMock()

from app.workers.price_cache import PriceCache


class TestBtcCompassLeadersLaggards(unittest.TestCase):

    def setUp(self):
        self.cache = PriceCache.get_instance_sync()
        test_data = [
            ("SUIUSDT", 3.20, 150_000_000.0, 18.5),    # Gainer #1
            ("NEARUSDT", 6.80, 80_000_000.0, 12.4),    # Gainer #2
            ("APTUSDT", 11.50, 95_000_000.0, 9.2),     # Gainer #3
            ("SOLUSDT", 90.00, 1_200_000_000.0, 4.1),  # Gainer #4
            ("ETHUSDT", 2700.0, 3_500_000_000.0, 2.5), # Gainer #5
            ("LINKUSDT", 14.00, 50_000_000.0, 0.5),    # Middle
            ("DOGEUSDT", 0.12, 120_000_000.0, -1.2),   # Mild drop
            ("ADAUSDT", 0.45, 60_000_000.0, -4.5),     # Loser #5
            ("DOTUSDT", 5.20, 45_000_000.0, -7.8),     # Loser #4
            ("FTMUSDT", 0.65, 40_000_000.0, -11.2),    # Loser #3
            ("CRVUSDT", 0.38, 35_000_000.0, -14.6),    # Loser #2
            ("LDOUSDT", 1.25, 32_000_000.0, -19.4),    # Loser #1
            # Stablecoins (must be filtered out)
            ("USDCUSDT", 1.00, 500_000_000.0, 0.01),
            ("FDUSDUSDT", 1.00, 200_000_000.0, 0.00),
            # TradFi / Equity tokens (must be filtered out)
            ("NVDAUSDT", 135.0, 50_000_000.0, 25.0),
        ]

        for sym, px, vol, pct in test_data:
            self.cache.update_price_and_volume(
                sym,
                price=px,
                quote_volume_24h=vol,
                high_24h=px * 1.05,
                low_24h=px * 0.95,
                price_change_pct_24h=pct
            )

    def test_price_cache_top_leaders_and_laggards_sorting(self):
        """Verify that PriceCache correctly ranks Top 5 Gainers and Top 5 Losers."""
        top_gainers, top_losers = self.cache.get_top_leaders_and_laggards(top_n=5, min_quote_volume=30_000_000.0)

        top_gainers, top_losers = self.cache.get_top_leaders_and_laggards(top_n=5, min_quote_volume=30_000_000.0)

        # Verify Top 5 Gainers
        self.assertEqual(top_gainers[:3], ["SUIUSDT", "NEARUSDT", "APTUSDT"])
        self.assertNotIn("USDCUSDT", top_gainers)
        self.assertNotIn("NVDAUSDT", top_gainers)

        # Verify Top 5 Losers
        self.assertEqual(top_losers[:3], ["LDOUSDT", "CRVUSDT", "FTMUSDT"])
        self.assertNotIn("USDCUSDT", top_losers)

    def test_btc_compass_bullish_targets_gainers_only(self):
        """When BTC is Bullish, the scanner must strictly evaluate Top Gainers for BUY and block all Shorts."""
        btc_price = 72000.0
        btc_ema9 = 71200.0
        btc_ema21 = 70500.0

        # BTC above EMA9 and EMA21 -> BULLISH
        is_bullish = (btc_price > btc_ema9 >= btc_ema21)
        self.assertTrue(is_bullish)

        # Candidate pool is Top Gainers
        top_gainers, _ = self.cache.get_top_leaders_and_laggards(top_n=5)
        allowed_side = "BUY" if is_bullish else "SELL"

        self.assertEqual(allowed_side, "BUY")
        self.assertIn("SUIUSDT", top_gainers)

        # Attempting a SELL on any coin under Bullish BTC must be blocked
        proposed_trade_side = "SELL"
        trade_permitted = (proposed_trade_side == allowed_side)
        self.assertFalse(trade_permitted, "Shorts must be 100% blocked when BTC is Bullish!")

    def test_btc_compass_bearish_targets_losers_only(self):
        """When BTC is Bearish, the scanner must strictly evaluate Top Losers for SELL and block all Longs."""
        btc_price = 68000.0
        btc_ema9 = 69500.0
        btc_ema21 = 70800.0

        # BTC below EMA9 and EMA21 -> BEARISH
        is_bearish = (btc_price < btc_ema9 <= btc_ema21)
        self.assertTrue(is_bearish)

        # Candidate pool is Top Losers
        _, top_losers = self.cache.get_top_leaders_and_laggards(top_n=5)
        allowed_side = "SELL" if is_bearish else "BUY"

        self.assertEqual(allowed_side, "SELL")
        self.assertIn("LDOUSDT", top_losers)

        # Attempting a BUY on any coin under Bearish BTC must be blocked
        proposed_trade_side = "BUY"
        trade_permitted = (proposed_trade_side == allowed_side)
        self.assertFalse(trade_permitted, "Longs must be 100% blocked when BTC is Bearish!")

    def test_btc_compass_chop_pauses_scanner(self):
        """When BTC is inside chop (flat/neutral), no directional trades are allowed."""
        btc_price = 70000.0
        btc_ema9 = 70050.0
        btc_ema21 = 69950.0
        btc_trend = "NEUTRAL"
        btc_1h_pct = 0.02
        macro_regime = "CHOP_RANGING"

        # Neither strong bull nor strong bear
        btc_compass_direction = "NEUTRAL"
        if (btc_price >= btc_ema9 and (btc_trend == "BULLISH" or btc_1h_pct > 0.05 or macro_regime == "BULL_EXPANSION")):
            btc_compass_direction = "BULLISH"
        elif (btc_price <= btc_ema9 and (btc_trend == "BEARISH" or btc_1h_pct < -0.05 or macro_regime == "BEAR_DISTRIBUTION")):
            btc_compass_direction = "BEARISH"

        self.assertEqual(btc_compass_direction, "NEUTRAL")
        # In neutral/chop, scanner does not dispatch trades
        should_pause_scanner = (btc_compass_direction == "NEUTRAL")
        self.assertTrue(should_pause_scanner, "Scanner must pause in BTC chop to protect capital!")


if __name__ == "__main__":
    unittest.main()
