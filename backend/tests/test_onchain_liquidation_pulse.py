import unittest
import asyncio
from app.trading.onchain_liquidation_pulse import OnChainLiquidationPulseEngine
from app.workers.price_cache import PriceCache


class TestOnChainLiquidationPulseEngine(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize price cache and engine instance."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.engine = OnChainLiquidationPulseEngine.get_instance()

    async def test_liquidation_heatmap_payload(self):
        """Verify liquidation heatmap returns long and short clusters with magnet pull targets."""
        res = await self.engine.get_liquidation_heatmap("BTCUSDT")

        self.assertEqual(res["symbol"], "BTCUSDT")
        self.assertIn("long_liquidation_clusters", res)
        self.assertIn("short_liquidation_clusters", res)
        self.assertIn("magnet_direction", res)
        self.assertIn("primary_liquidity_target", res)
        self.assertIn("magnet_pull_score", res)

        long_clusters = res["long_liquidation_clusters"]
        short_clusters = res["short_liquidation_clusters"]
        self.assertEqual(len(long_clusters), 3)
        self.assertEqual(len(short_clusters), 3)

        # Check pricing logic: Long trigger < current price < Short trigger
        curr_price = res["current_price"]
        self.assertLess(long_clusters[0]["trigger_price"], curr_price)
        self.assertGreater(short_clusters[0]["trigger_price"], curr_price)

    async def test_exchange_netflow_pulse_payload(self):
        """Verify exchange netflow pulse metrics and whale alerts."""
        res = await self.engine.get_exchange_netflow_pulse("BTC")

        self.assertEqual(res["asset"], "BTC")
        self.assertIn("netflow_regime", res)
        self.assertIn("market_bias", res)
        self.assertIn("netflow_24h_amount", res)
        self.assertIn("stablecoin_netflow_24h_usd_m", res)
        self.assertIn("whale_alerts", res)

        whale_alerts = res["whale_alerts"]
        self.assertGreaterEqual(len(whale_alerts), 1)
        self.assertIn("amount_usd_m", whale_alerts[0])


if __name__ == "__main__":
    unittest.main()
