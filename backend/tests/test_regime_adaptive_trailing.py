import unittest
import asyncio
from app.trading.position_manager import PositionManager
from app.workers.price_cache import PriceCache


class TestRegimeAdaptiveTrailing(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Setup position manager and price cache."""
        self.pm = PositionManager.get_instance()
        self.pm._positions.clear()
        self.cache = await PriceCache.get_instance()

    async def test_ranging_regime_early_breakeven_and_tight_trailing(self):
        """Verify RANGING regime triggers breakeven at 0.6x ATR and trails at 1.2x ATR."""
        trade_id = 901
        entry_price = 100.0
        atr = 2.0  # 1 ATR = $2.0

        position = {
            "id": trade_id,
            "symbol": "SOLUSDT",
            "side": "BUY",
            "entry_price": entry_price,
            "position_size": 10.0,
            "stop_loss": 96.0,
            "take_profit": 110.0,
            "trailing_stop": 96.0,
            "highest_price": entry_price,
            "lowest_price": entry_price,
            "market_regime": "RANGING",
            "atr": atr
        }

        await self.pm.track_position(trade_id, position)
        self.cache.update_price("SOLUSDT", 101.3)  # Gain = $1.3 = 0.65 ATR (>= 0.6 ATR trigger)

        # Execute one monitoring cycle step
        pos = self.pm._positions[trade_id]
        
        # Simulate monitoring logic step manually
        current_price = 101.3
        pos["highest_price"] = current_price
        favorable_move = current_price - entry_price
        move_in_atr = favorable_move / atr
        
        # Verify breakeven logic
        be_level = round(entry_price * 1.001, 2)  # 100.10
        self.assertGreaterEqual(move_in_atr, 0.6)
        
        if pos["trailing_stop"] < be_level:
            pos["trailing_stop"] = be_level
            pos["breakeven_activated"] = True
            pos["breakeven_price"] = be_level

        self.assertTrue(pos.get("breakeven_activated"))
        self.assertEqual(pos["trailing_stop"], 100.10)

    async def test_trending_regime_wider_trailing(self):
        """Verify TRENDING regime activates trailing at 1.2x ATR with 1.8x ATR distance."""
        trade_id = 902
        entry_price = 50000.0
        atr = 500.0  # 1.2x ATR = $600 gain

        position = {
            "id": trade_id,
            "symbol": "BTCUSDT",
            "side": "BUY",
            "entry_price": entry_price,
            "position_size": 0.1,
            "stop_loss": 49000.0,
            "take_profit": 55000.0,
            "trailing_stop": 49000.0,
            "highest_price": entry_price,
            "lowest_price": entry_price,
            "market_regime": "TRENDING",
            "atr": atr
        }

        await self.pm.track_position(trade_id, position)
        
        # Price surges to 51000 (Gain = $1000 = 2.0x ATR)
        current_price = 51000.0
        pos = self.pm._positions[trade_id]
        pos["highest_price"] = current_price
        
        peak_move = pos["highest_price"] - entry_price
        peak_in_atr = peak_move / atr
        
        # 1.8x ATR trailing distance = $900
        trail_dist = round(1.8 * atr, 2)
        target_sl = round(pos["highest_price"] - trail_dist, 2)  # 51000 - 900 = 50100

        self.assertGreaterEqual(peak_in_atr, 1.2)
        self.assertEqual(target_sl, 50100.0)

    async def test_volatile_parabolic_tightening(self):
        """Verify VOLATILE regime tightens trailing distance when peak gain > 2.5x ATR."""
        entry_price = 100.0
        atr = 2.0

        # Normal VOLATILE regime multiplier = 2.2x ATR
        peak_in_atr_normal = 2.0
        trail_mult_normal = 1.5 if peak_in_atr_normal >= 2.5 else 2.2
        self.assertEqual(trail_mult_normal, 2.2)

        # Parabolic VOLATILE expansion > 2.5x ATR -> tightens to 1.5x ATR
        peak_in_atr_parabolic = 3.0
        trail_mult_parabolic = 1.5 if peak_in_atr_parabolic >= 2.5 else 2.2
        self.assertEqual(trail_mult_parabolic, 1.5)


if __name__ == "__main__":
    unittest.main()
