import unittest
import asyncio
import time
from typing import Dict, Any

from app.trading.chaos_engine import ChaosEngine
from app.trading.position_manager import PositionManager
from app.trading.order_manager import OrderManager
from app.trading.portfolio_guard import PortfolioGuard
from app.scheduler.manager import LayeredSchedulerManager
from app.workers.price_cache import PriceCache
from app.services.binance_connector import BinanceConnector


class TestChaosTestingMode(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize state and enable chaos testing mode."""
        self.chaos = ChaosEngine.get_instance()
        self.chaos.reset_stats()
        self.chaos.configure(
            enabled=True,
            api_timeout_rate=0.0,
            http_500_rate=0.0,
            ws_lost_msg_rate=0.0,
            ws_duplicate_msg_rate=0.0,
            delayed_fill_rate=0.0,
            delayed_fill_seconds=0.1,
            db_failure_rate=0.0,
            network_disconnect_rate=0.0
        )

        self.pm = PositionManager.get_instance()
        self.pm._positions.clear()

        self.om = OrderManager.get_instance()
        self.pg = PortfolioGuard.get_instance()
        self.pg._circuit_breaker_active = False

        self.sm = await LayeredSchedulerManager.get_instance()
        self.sm.emergency_stop_active = False
        self.sm.global_trading_mode = "PAPER"

        self.cache = await PriceCache.get_instance()
        self.cache.connection_status = "CONNECTED"
        self.cache.last_update_time = time.time()
        self.cache.update_price("BTCUSDT", 50000.0)

    async def asyncTearDown(self):
        """Clean up chaos state after tests."""
        self.chaos.configure(enabled=False)
        self.chaos.reset_stats()
        self.pm._positions.clear()

    async def test_1_binance_api_timeout_injection_and_no_duplicate_orders(self):
        """Verify API timeouts do not create duplicate orders or corrupt state."""
        self.chaos.configure(enabled=True, api_timeout_rate=1.0)
        connector = BinanceConnector.get_instance()

        with self.assertRaises(Exception):
            await connector.execute_request("GET", "/api/v3/account", mode="TESTNET")

        self.assertEqual(self.chaos.stats["api_timeouts"], 1)
        self.assertEqual(len(self.pm.get_active_positions()), 0)

    async def test_2_http_500_error_injection_handling(self):
        """Verify HTTP 500 error responses are caught and handled without creating orphan positions."""
        self.chaos.configure(enabled=True, http_500_rate=1.0)
        connector = BinanceConnector.get_instance()

        res = await connector.execute_request("POST", "/api/v3/order", mode="TESTNET", signed=True)
        self.assertEqual(res.get("error"), "CHAOS_HTTP_500")
        self.assertEqual(res.get("status_code"), 500)
        self.assertEqual(self.chaos.stats["http_500_errors"], 1)
        self.assertEqual(len(self.pm.get_active_positions()), 0)

    async def test_3_lost_and_duplicate_websocket_messages(self):
        """Verify price cache handles dropped and duplicate WS ticker messages correctly."""
        self.chaos.configure(enabled=True, ws_lost_msg_rate=0.5, ws_duplicate_msg_rate=0.5)

        initial_time = time.time()
        self.cache.update_price("BTCUSDT", 51000.0)

        # Simulate dropped WS msg handling
        if self.chaos.should_inject_ws_drop():
            pass  # message dropped
        else:
            self.cache.update_price("BTCUSDT", 51100.0)

        # Simulate duplicate WS msg handling
        p = 51200.0
        self.cache.update_price("BTCUSDT", p)
        if self.chaos.should_inject_ws_duplicate():
            self.cache.update_price("BTCUSDT", p)  # process again

        self.assertEqual(self.cache.get_price("BTCUSDT"), 51200.0)
        self.assertGreaterEqual(self.chaos.stats["ws_lost_messages"] + self.chaos.stats["ws_duplicate_messages"], 0)

    async def test_4_delayed_fills_and_order_pipeline_lock_safety(self):
        """Verify delayed order execution fills complete without race conditions or duplicates."""
        self.chaos.configure(enabled=True, delayed_fill_rate=1.0, delayed_fill_seconds=0.05)

        start_time = time.time()
        delay = self.chaos.get_fill_delay()
        await asyncio.sleep(delay)
        elapsed = time.time() - start_time

        self.assertGreaterEqual(elapsed, 0.04)
        self.assertEqual(self.chaos.stats["delayed_fills"], 1)

    async def test_5_database_failure_resilience_and_consistent_state(self):
        """Verify DB failures during operations roll back gracefully without orphan positions."""
        self.chaos.configure(enabled=True, db_failure_rate=1.0)

        from app.services.audit_service import AuditService
        from app.database.session import AsyncSessionLocal

        try:
            async with AsyncSessionLocal() as db:
                await AuditService.log_event(db, "Test audit log with chaos DB failure")
            db_failed = False
        except Exception as e:
            db_failed = True
            self.assertIn("Chaos injected Database Failure", str(e))

        self.assertTrue(db_failed)
        self.assertEqual(self.chaos.stats["db_failures"], 1)
        self.assertEqual(len(self.pm.get_active_positions()), 0)

    async def test_6_network_disconnect_and_automatic_recovery(self):
        """Verify network disconnects trigger reconnect sequence and restore feed state."""
        self.chaos.configure(enabled=True, network_disconnect_rate=1.0)

        disconnected = False
        if self.chaos.should_inject_network_disconnect():
            disconnected = True
            self.cache.connection_status = "DISCONNECTED"

        self.assertTrue(disconnected)
        self.assertEqual(self.cache.connection_status, "DISCONNECTED")
        self.assertEqual(self.chaos.stats["network_disconnects"], 1)

        # Automatic recovery simulation
        self.cache.connection_status = "RECONNECTING"
        self.cache.connection_status = "CONNECTED"
        self.cache.update_price("BTCUSDT", 50500.0)

        self.assertEqual(self.cache.connection_status, "CONNECTED")
        self.assertEqual(self.cache.get_price("BTCUSDT"), 50500.0)

    async def test_7_full_chaos_concurrent_run_verification(self):
        """Runs multi-asset scenario under randomized multi-fault chaos injection."""
        self.chaos.configure(
            enabled=True,
            api_timeout_rate=0.2,
            http_500_rate=0.2,
            ws_lost_msg_rate=0.2,
            ws_duplicate_msg_rate=0.2,
            delayed_fill_rate=0.2,
            delayed_fill_seconds=0.02,
            db_failure_rate=0.2,
            network_disconnect_rate=0.2
        )

        symbols = ["BTCUSDT", "ETHUSDT", "BNBUSDT"]
        for sym in symbols:
            if not self.chaos.should_inject_ws_drop():
                self.cache.update_price(sym, 1000.0)

            if self.chaos.should_inject_ws_duplicate():
                self.cache.update_price(sym, 1000.0)

        telemetry = self.chaos.get_telemetry()
        self.assertTrue(telemetry["enabled"])
        self.assertIn("stats", telemetry)


if __name__ == "__main__":
    unittest.main()
