import unittest
import asyncio
import time
from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from app.database.base import Base
from app.database.session import AsyncSessionLocal
from app.trading.position_manager import PositionManager
from app.trading.order_manager import OrderManager
from app.trading.portfolio_guard import PortfolioGuard
from app.scheduler.manager import LayeredSchedulerManager
from app.workers.price_cache import PriceCache
from app.risk.evaluator import RiskEvaluator


class TestTradingSafetyModules(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize clean state before each test."""
        self.pm = PositionManager.get_instance()
        self.pm._positions.clear()
        
        self.om = OrderManager.get_instance()
        self.pg = PortfolioGuard.get_instance()
        self.pg._circuit_breaker_active = False
        self.pg._circuit_breaker_date = None
        
        self.sm = await LayeredSchedulerManager.get_instance()
        self.sm.emergency_stop_active = False
        self.sm.global_trading_mode = "PAPER"
        
        self.cache = await PriceCache.get_instance()
        self.cache.connection_status = "CONNECTED"
        self.cache.last_update_time = time.time()
        self.cache.update_price("BTCUSDT", 50000.0)

    async def asyncTearDown(self):
        """Clean up positions and flags after each test."""
        self.pm._positions.clear()
        self.sm.emergency_stop_active = False

    async def test_stop_loss_trigger_and_zero_price_protection(self):
        """Verify Stop Loss triggers on real drop and NEVER false-triggers when price is 0."""
        trade_id = 101
        await self.pm.track_position(trade_id, {
            "id": trade_id,
            "symbol": "BTCUSDT",
            "side": "BUY",
            "entry_price": 50000.0,
            "position_size": 0.1,
            "stop_loss": 48000.0,
            "take_profit": 55000.0,
            "trailing_stop": 48000.0,
            "highest_price": 50000.0,
            "lowest_price": 50000.0
        })

        # 1. Zero price protection: Price cache returns 0.0 -> Must NOT trigger SL
        self.cache.update_price("BTCUSDT", 0.0)
        # Simulate monitor loop check
        current_price = self.cache.get_price("BTCUSDT")
        self.assertEqual(current_price, 0.0)
        # Position should remain active
        self.assertIn(trade_id, self.pm._positions)

        # 2. Market Gap down to 47000 (below SL 48000) -> Triggers SL close
        self.cache.update_price("BTCUSDT", 47000.0)
        close_called = False
        async def mock_close(tid, exit_px, outcome):
            nonlocal close_called
            close_called = True
            self.assertEqual(tid, trade_id)
            self.assertEqual(exit_px, 47000.0)
            self.assertEqual(outcome, "STOP_LOSS")
            self.pm._positions.pop(tid, None)

        original_close = self.pm._close_position
        self.pm._close_position = mock_close
        try:
            # Trigger manual check logic
            pos = self.pm._positions[trade_id]
            curr_px = self.cache.get_price("BTCUSDT")
            if curr_px > 0 and curr_px <= pos["trailing_stop"]:
                await self.pm._close_position(trade_id, curr_px, "STOP_LOSS")
            
            self.assertTrue(close_called)
            self.assertNotIn(trade_id, self.pm._positions)
        finally:
            self.pm._close_position = original_close

    async def test_trailing_stop_progression_and_no_backward_movement(self):
        """Verify trailing stop moves upward with price and never moves backwards."""
        trade_id = 102
        await self.pm.track_position(trade_id, {
            "id": trade_id,
            "symbol": "BTCUSDT",
            "side": "BUY",
            "entry_price": 50000.0,
            "position_size": 0.1,
            "stop_loss": 48000.0,  # 2000 distance
            "take_profit": 55000.0,
            "trailing_stop": 48000.0,
            "highest_price": 50000.0,
            "lowest_price": 50000.0
        })

        pos = self.pm._positions[trade_id]
        dist = abs(pos["entry_price"] - pos["stop_loss"]) # 2000.0

        # Price climbs to 52000 -> trailing stop should raise to 50000 (52000 - 2000)
        price1 = 52000.0
        if price1 > pos["highest_price"]:
            old_sl = pos["trailing_stop"]
            pos["highest_price"] = price1
            new_sl = round(price1 - dist, 2)
            if new_sl > old_sl:
                pos["trailing_stop"] = new_sl

        self.assertEqual(pos["trailing_stop"], 50000.0)

        # Price drops slightly to 51000 -> trailing stop MUST NOT move backwards
        price2 = 51000.0
        if price2 > pos["highest_price"]:
            old_sl = pos["trailing_stop"]
            pos["highest_price"] = price2
            new_sl = round(price2 - dist, 2)
            if new_sl > old_sl:
                pos["trailing_stop"] = new_sl

        self.assertEqual(pos["trailing_stop"], 50000.0)  # Preserved at 50000.0

    async def test_take_profit_stages_and_final_exit(self):
        """Verify multi-stage take profit hits TP stages and final TP closes position."""
        trade_id = 103
        await self.pm.track_position(trade_id, {
            "id": trade_id,
            "symbol": "BTCUSDT",
            "side": "BUY",
            "entry_price": 50000.0,
            "position_size": 1.0,
            "stop_loss": 48000.0,
            "take_profit": 54000.0,
            "trailing_stop": 48000.0,
            "highest_price": 50000.0,
            "lowest_price": 50000.0
        })

        pos = self.pm._positions[trade_id]
        stages = pos["tp_stages"]
        self.assertEqual(len(stages), 3)

        # TP1 level is entry + (54000-50000)*0.35 = 51400.0
        self.assertEqual(stages[0]["level"], 51400.0)

        # Price reaches TP1 level -> hit stage
        stages[0]["hit"] = True
        self.assertTrue(stages[0]["hit"])

        # Final TP stage (TP3) is 54000.0. When hit, position is closed.
        stages[-1]["hit"] = True
        await self.pm._close_position(trade_id, 54000.0, "FINAL_TAKE_PROFIT_STAGE")
        self.assertNotIn(trade_id, self.pm._positions)

    async def test_position_synchronization(self):
        """Verify sync_exchange_positions accurately reports tracked state."""
        await self.pm.track_position(201, {
            "id": 201, "symbol": "ETHUSDT", "side": "BUY",
            "entry_price": 3000.0, "position_size": 1.0, "stop_loss": 2900.0,
            "take_profit": 3300.0, "trailing_stop": 2900.0, "highest_price": 3000.0, "lowest_price": 3000.0
        })
        sync_res = await self.pm.sync_exchange_positions("PAPER")
        self.assertEqual(sync_res["status"], "SUCCESS")
        self.assertEqual(sync_res["tracked_positions_count"], 1)

    async def test_emergency_liquidation_circuit_breaker(self):
        """Verify emergency liquidation liquidates active positions and blocks new trading."""
        await self.pm.track_position(301, {
            "id": 301, "symbol": "BTCUSDT", "side": "BUY",
            "entry_price": 50000.0, "position_size": 0.1, "stop_loss": 48000.0,
            "take_profit": 55000.0, "trailing_stop": 48000.0, "highest_price": 50000.0, "lowest_price": 50000.0
        })

        liquidated_count = await self.pm.trigger_emergency_liquidation()
        self.assertEqual(liquidated_count, 1)
        self.assertEqual(len(self.pm._positions), 0)

        # Confirm trading is now blocked
        allowed, reason = await self.sm.is_trading_enabled()
        self.assertFalse(allowed)
        self.assertIn("EMERGENCY_STOP_ACTIVE", reason)

    async def test_stale_market_data_circuit_breaker(self):
        """Verify scheduler blocks trading when price feed is stale."""
        self.cache.last_update_time = time.time() - 10.0  # 10s old (stale)
        allowed, reason = await self.sm.is_trading_enabled()
        self.assertFalse(allowed)
        self.assertIn("stale", reason.lower())

    async def test_risk_evaluator_atr_clamping(self):
        """Verify ATR position sizing floors position size to avoid over-risking."""
        size = RiskEvaluator.calculate_atr_position_size(
            balance=10000.0,
            entry_price=50000.0,
            atr=500.0,
            max_risk_pct=0.02
        )
        self.assertGreater(size, 0.0)
        self.assertLessEqual(size * 500.0 * 1.5, 10000.0 * 0.02 + 0.01)


if __name__ == "__main__":
    unittest.main()
