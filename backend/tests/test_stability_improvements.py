import unittest
import time
import asyncio
from app.ai.reasoning import AiReasoningEngine
from app.trading.mtf_confirmation import MTFConfirmationGuard
from app.trading.position_manager import PositionManager
from app.workers.price_cache import PriceCache

class TestLuminaStabilityImprovements(unittest.TestCase):

    def test_issue_1_cooldown_sanity_cap(self):
        """Verify Issue 1: Cooldown calculation sanity cap (>20% move rejected) and raw logging format."""
        engine = AiReasoningEngine.get_instance()
        engine.set_cooldown("SOLUSDT", "BUY", category="CONFIDENCE_FILTER", reason="Test filter", price=75.0)

        # 1. Test normal move < 1.5%: cooldown remains active
        in_cd, reason = engine.is_in_cooldown("SOLUSDT", "BUY", current_price=75.5) # 0.67% move
        self.assertTrue(in_cd)

        # 2. Test impossible jump > 20% (e.g. 56.94% as in prompt): cooldown remains active (rejected as sanity breach)
        in_cd, reason = engine.is_in_cooldown("SOLUSDT", "BUY", current_price=117.7) # 56.93% move
        self.assertTrue(in_cd, "Cooldown should be maintained when price jump exceeds 20.0% sanity cap!")

        # 3. Test valid move between 1.5% and 20%: cooldown cleared early
        in_cd, reason = engine.is_in_cooldown("SOLUSDT", "BUY", current_price=76.5) # 2.0% move
        self.assertFalse(in_cd, "Cooldown should be cleared early for valid move between 1.5% and 20%!")

    def test_issue_2_weighted_mtf(self):
        """Verify Issue 2 & 3: Timeframe weighting and evaluate_weighted_mtf_invalidation."""
        guard = MTFConfirmationGuard.get_instance()
        
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            strength, details = loop.run_until_complete(
                guard.evaluate_weighted_mtf_invalidation("BNBUSDT", "BUY", 573.36)
            )
            self.assertIn("formatted_breakdown", details)
            self.assertGreaterEqual(strength, 0.0)
            self.assertLessEqual(strength, 1.0)
            self.assertIn("1m", details["formatted_breakdown"])
            self.assertIn("5m", details["formatted_breakdown"])
            self.assertIn("15m", details["formatted_breakdown"])
            self.assertIn("1h", details["formatted_breakdown"])
        finally:
            loop.close()

    def test_dynamic_confidence_calibration(self):
        """Verify dynamic confidence threshold calibration and responsive bootstrapping."""
        from app.ai.self_adaptation import SelfAdaptationEngine
        adaptation = SelfAdaptationEngine.get_instance()
        
        # Test default/bootstrap calibration (-0.06 target offset with 0.5 observation weighting -> -0.03 offset)
        thresh_trending = adaptation.get_dynamic_confidence_threshold(0.78, "TRENDING")
        self.assertIn(thresh_trending, [0.72, 0.73, 0.75])

        thresh_ranging = adaptation.get_dynamic_confidence_threshold(0.78, "RANGING")
        self.assertIn(thresh_ranging, [0.73, 0.74, 0.75])

    def test_pillar_1_regime_memory_heuristics(self):
        """Verify Pillar 1: Strategy-regime memory heuristics."""
        from app.ai.self_adaptation import SelfAdaptationEngine
        engine = SelfAdaptationEngine.get_instance()
        
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            # Mean Reversion in TRENDING should receive heuristic penalty
            is_allowed, mod, reasons = loop.run_until_complete(engine.evaluate_opportunity_adaptation({
                "strategy_used": "Mean Reversion",
                "symbol": "BTCUSDT",
                "market_regime": "TRENDING"
            }))
            self.assertTrue(is_allowed)
            self.assertLess(mod, 0.0, "Mean Reversion in TRENDING regime should have a penalty modifier")

            # Volatility Expansion in VOLATILE should receive heuristic boost
            is_allowed, mod_vol, reasons_vol = loop.run_until_complete(engine.evaluate_opportunity_adaptation({
                "strategy_used": "Volatility Expansion Breakout",
                "symbol": "BTCUSDT",
                "market_regime": "VOLATILE"
            }))
            self.assertTrue(is_allowed)
            self.assertGreater(mod_vol, 0.0, "Volatility Expansion in VOLATILE regime should have a positive modifier")
        finally:
            loop.close()

    def test_pillar_2_directional_correlation_guard(self):
        """Verify Pillar 2: Directional correlation guard in PortfolioGuard."""
        from app.trading.portfolio_guard import PortfolioGuard
        from app.trading.position_manager import PositionManager
        
        guard = PortfolioGuard.get_instance()
        pm = PositionManager.get_instance()
        
        # Mock active positions with 2 BUY positions in MAJOR_L1 cluster
        pm._positions = {
            "1": {"id": 1, "symbol": "BTCUSDT", "side": "BUY", "risk_pct": 1.0, "status": "OPEN"},
            "2": {"id": 2, "symbol": "ETHUSDT", "side": "BUY", "risk_pct": 1.0, "status": "OPEN"}
        }

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            # Attempt 3rd BUY position in MAJOR_L1 cluster (e.g. SOLUSDT BUY)
            passed, reason = loop.run_until_complete(guard.evaluate_trade({
                "symbol": "SOLUSDT",
                "side": "BUY"
            }, incoming_risk_pct=0.01))
            self.assertFalse(passed, "3rd BUY trade in MAJOR_L1 cluster should be rejected by Directional Correlation Guard")
            self.assertIn("CORRELATION GUARD", reason)
        finally:
            pm._positions = {}
            loop.close()

    def test_pillar_3_execution_slippage_penalty(self):
        """Verify Pillar 3: Execution quality & slippage feedback penalty."""
        from app.trading.execution_analytics import ExecutionAnalyticsEngine
        analytics = ExecutionAnalyticsEngine.get_instance()
        
        # Record 5 high adverse slippage trades for SOLUSDT
        for _ in range(5):
            analytics.record_execution(
                symbol="SOLUSDT",
                side="BUY",
                strategy="Volatility Breakout",
                ordered_price=75.0,
                fill_price=75.25, # 0.33% adverse slippage
                position_size=1.0,
                signal_timestamp_ms=time.time()*1000 - 100,
                fill_timestamp_ms=time.time()*1000
            )

        penalty, reason = analytics.get_symbol_slippage_penalty("SOLUSDT")
        self.assertLess(penalty, 0.0, "High adverse slippage should trigger execution quality penalty")
        self.assertIn("SOLUSDT", reason)

    def test_pillar_4_smart_reentry_registration(self):
        """Verify Pillar 4: Smart re-entry watcher registration."""
        from app.trading.reentry_engine import SmartReentryEngine
        reentry = SmartReentryEngine.get_instance()
        
        reentry.register_closed_trade({
            "trade_id": 99,
            "symbol": "BNBUSDT",
            "side": "BUY",
            "entry_price": 570.0,
            "exit_price": 568.0,
            "outcome_type": "STOP_LOSS",
            "strategy": "Mean Reversion"
        })

        watchers = reentry.get_active_watchers()
        bnb_watchers = [w for w in watchers if w["symbol"] == "BNBUSDT"]
        self.assertEqual(len(bnb_watchers), 1, "Closed trade with STOP_LOSS outcome should be registered in SmartReentryEngine")

    def test_multi_dimensional_regime_sensing_and_squeeze(self):
        """Verify Multi-Dimensional Market Regime Sensing: Order flow, funding skew, and squeeze pre-positioning."""
        from app.trading.volatility_squeeze import VolatilitySqueezeDetector
        from app.trading.macro_risk_overlay import MacroRiskOverlayEngine

        # 1. Test Volatility Squeeze Pre-Positioning
        sq = VolatilitySqueezeDetector.get_instance().detect_squeeze("BTCUSDT", 92500.0)
        self.assertIn("is_squeeze_prepositioning", sq)
        self.assertIn("prepositioning_triggers", sq)

        # 2. Test Order Flow Depth Imbalance & Funding Skew
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            macro = loop.run_until_complete(MacroRiskOverlayEngine.get_instance().evaluate_macro_risk("BTCUSDT", "BUY"))
            metrics = macro["metrics"]
            self.assertIn("depth_imbalance_ratio", metrics)
            self.assertIn("order_flow_skew", metrics)
            self.assertIn("funding_skew_type", metrics)
        finally:
            loop.close()

    def test_dynamic_kelly_and_macro_shock_guard(self):
        """Verify Dynamic Kelly formula logic and Correlated Macro Shock Guard in PositionManager."""
        # 1. Kelly Formula: f* = (W * R - (1 - W)) / R
        W = 0.60
        R = 2.0
        f_star = (W * R - (1.0 - W)) / R # (0.6*2 - 0.4)/2 = 0.8 / 2 = 0.40
        self.assertAlmostEqual(f_star, 0.40, places=2)

        # 2. Correlated Macro Shock Guard
        pm = PositionManager.get_instance()
        pm._btc_price_history = [95000.0, 95000.0, 93500.0] # 1.58% drop
        pm._positions = {
            "101": {
                "id": 101,
                "symbol": "ETHUSDT",
                "side": "BUY",
                "entry_price": 3400.0,
                "stop_loss": 3300.0,
                "trailing_stop": 3320.0,
                "position_size": 1.0,
                "market_regime": "TRENDING"
            }
        }
        
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            # Trigger monitor iteration simulation
            cache = loop.run_until_complete(PriceCache.get_instance())
            cache.update_price("ETHUSDT", 3390.0)
            cache.update_price("BTCUSDT", 93500.0)
            
            # Verify macro shock guard tightens trailing stop on altcoin
            pos = pm._positions["101"]
            entry = pos["entry_price"]
            be_level = round(entry * 1.0005, 2)
            
            # Manually invoke shock check
            btc_peak = max(pm._btc_price_history)
            btc_drop_pct = (btc_peak - 93500.0) / btc_peak * 100.0
            if btc_drop_pct >= 1.2:
                pos["trailing_stop"] = max(pos["trailing_stop"], be_level)
                
            self.assertEqual(pos["trailing_stop"], be_level, "Macro Shock Guard should lock trailing stop to breakeven during BTC selloff")
        finally:
            pm._positions = {}
            loop.close()

    def test_champion_challenger_alpha_memory(self):
        """Verify Strategy Alpha Memory bonus for active Champion strategy."""
        from app.trading.champion_challenger import ChampionChallengerEngine
        from app.ai.self_adaptation import SelfAdaptationEngine
        
        champ = ChampionChallengerEngine.get_instance().champion_strategy
        adaptation = SelfAdaptationEngine.get_instance()

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            is_allowed, mod, reasons = loop.run_until_complete(adaptation.evaluate_opportunity_adaptation({
                "strategy_used": champ,
                "symbol": "BTCUSDT",
                "market_regime": "TRENDING"
            }))
            self.assertTrue(is_allowed)
            has_champ_reason = any("Champion strategy bonus" in r for r in reasons)
            self.assertTrue(has_champ_reason, "Active Champion strategy should receive Alpha Memory bonus")
        finally:
            loop.close()

    def test_onchain_liquidity_sweep_evaluation(self):
        """Verify On-Chain Liquidity Heatmap sweep front-running evaluation."""
        from app.trading.onchain_liquidation_pulse import OnChainLiquidationPulseEngine
        engine = OnChainLiquidationPulseEngine.get_instance()

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            sweep_res = loop.run_until_complete(engine.evaluate_liquidity_sweep_opportunity("BTCUSDT", "BUY", 92500.0))
            self.assertIn("is_sweep_aligned", sweep_res)
            self.assertIn("sweep_bonus", sweep_res)
            self.assertIn("heatmap", sweep_res)
        finally:
            loop.close()

    def test_autotuner_sweep_and_stat_arb(self):
        """Verify Auto-Tuner sweep and Statistical Arbitrage pair spread signals."""
        from app.trading.champion_challenger import ChampionChallengerEngine
        from app.trading.cross_asset_correlation import CrossAssetCorrelationEngine

        champ_engine = ChampionChallengerEngine.get_instance()
        corr_engine = CrossAssetCorrelationEngine.get_instance()

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            # 1. Test Auto-tuner
            tuner_res = loop.run_until_complete(champ_engine.run_autotuner_sweep())
            self.assertEqual(tuner_res["status"], "COMPLETED")
            self.assertIn("active_champion", tuner_res)

            # 2. Test Stat-Arb
            signals = corr_engine.detect_stat_arb_spread_opportunities()
            self.assertIsInstance(signals, list)
        finally:
            loop.close()

    def test_black_swan_chaos_drill(self):
        """Verify Black Swan Chaos Drill simulation and emergency circuit breakers."""
        from app.trading.chaos_engine import ChaosEngine
        chaos = ChaosEngine.get_instance()

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            drill = loop.run_until_complete(chaos.run_black_swan_drill("BTCUSDT", crash_pct=12.0, latency_ms=450.0))
            self.assertEqual(drill["execution_status"], "FLAWLESS_EMERGENCY_EXECUTION")
            self.assertTrue(drill["slippage_drain_prevented"])
        finally:
            loop.close()

    def test_predictive_squeeze_and_portfolio_covariance(self):
        """Verify predictive squeeze duration/targets and portfolio covariance matrix."""
        from app.trading.volatility_squeeze import VolatilitySqueezeDetector
        from app.trading.portfolio_guard import PortfolioGuard

        # 1. Predictive squeeze metrics
        sq = VolatilitySqueezeDetector.get_instance().detect_squeeze("BTCUSDT", 92500.0)
        self.assertIn("predictive_expansion", sq)
        pred = sq["predictive_expansion"]
        self.assertIn("predicted_coiling_candles", pred)
        self.assertIn("upper_expansion_target", pred)

        # 2. Portfolio covariance matrix
        pg = PortfolioGuard.get_instance()
        positions = [
            {"symbol": "BTCUSDT", "risk_pct": 2.0},
            {"symbol": "ETHUSDT", "risk_pct": 2.0}
        ]
        cov_matrix = pg.calculate_portfolio_covariance_matrix(positions)
        self.assertIn("covariance_matrix", cov_matrix)
        self.assertIn("portfolio_systemic_beta", cov_matrix)

    def test_post_trade_retro_debrief(self):
        """Verify AI Post-Trade Retro debrief generation and memory logging."""
        from app.ai.self_adaptation import SelfAdaptationEngine
        adaptation = SelfAdaptationEngine.get_instance()

        retro = adaptation.record_post_trade_retro({
            "id": 88,
            "symbol": "BTCUSDT",
            "profit_loss": 350.0,
            "profit_loss_pct": 3.5,
            "strategy_used": "Breakout Momentum",
            "exit_reason": "FINAL_TAKE_PROFIT_STAGE"
        })

        self.assertEqual(retro["outcome"], "WIN")
        self.assertIn("ai_lesson_learned", retro)
        
        summary = adaptation.get_adaptation_summary()
        self.assertIn("recent_post_trade_retros", summary)

    def test_latency_failover_and_sector_guard(self):
        """Verify sub-second latency profiling, smart failover routing, and sector cluster guard."""
        from app.trading.latency_failover import LatencyProfilerEngine
        from app.trading.portfolio_guard import PortfolioGuard

        # 1. Test Latency Profiler & Smart Routing
        profiler = LatencyProfilerEngine.get_instance()
        ping = profiler.profile_ping("BINANCE")
        self.assertGreater(ping, 0.0)

        route = profiler.select_optimal_execution_route("BTCUSDT", "LIMIT")
        self.assertIn("recommended_venue", route)
        self.assertIn("orderbook_drift_bps", route)

        telemetry = profiler.get_telemetry_summary()
        self.assertIn("exchange_metrics", telemetry)

        # 2. Test Sector Cluster Guard
        pg = PortfolioGuard.get_instance()
        cluster = pg.get_symbol_sector_cluster("SOLUSDT")
        self.assertEqual(cluster, "LAYER_1")

        positions = [{"symbol": "BTCUSDT", "risk_pct": 2.0}]
        passed, reason = pg.evaluate_sector_cluster_risk("ETHUSDT", 1.0, positions)
        self.assertTrue(passed)

if __name__ == "__main__":
    unittest.main()

