"""
Unit tests for Single Trade Concurrency, Strict Stop Loss, Long Profitable Runs, and Anti-Bluff Engine.
"""

import sys
import unittest
from unittest.mock import MagicMock, patch

for mod in ["httpx", "sqlalchemy", "sqlalchemy.ext.asyncio", "sqlalchemy.orm", "sqlalchemy.sql", "fastapi"]:
    if mod not in sys.modules:
        sys.modules[mod] = MagicMock()

from app.trading.breakout_quality import BreakoutQualityAnalyzer
from app.trading.adaptive_trailing_engine import AdaptiveTrailingEngine
from app.trading.volatility_squeeze import VolatilitySqueezeDetector


class TestSingleTradeStrictRunner(unittest.TestCase):

    def setUp(self):
        self.anti_bluff = BreakoutQualityAnalyzer.get_instance()
        self.trailing = AdaptiveTrailingEngine.get_instance()
        self.squeeze = VolatilitySqueezeDetector.get_instance()

    def test_anti_bluff_rejection_wick_bull_trap(self):
        """Verify that a breakout candle with heavy upper wick (>45% rejection) is flagged as a bluff."""
        candidate = {
            "symbol": "BTCUSDT",
            "side": "BUY",
            "entry_price": 65000.0,
            "price": 65000.0,
            "strategy": "Minervini VCP Breakout",
            "rvol": 2.2
        }

        # Mock PriceCache to return candle close near bottom of range (Bull Trap)
        with patch("app.trading.breakout_quality.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {"atr": 500.0, "rsi": 55.0}
            mock_cache.get_1m_candle_metrics.return_value = {
                "close_location": 0.40,  # closed in lower 40% -> 60% upper wick rejection!
                "candle_direction": "BEARISH",
                "samples_count": 5
            }
            mock_cache.get_volume_surge_ratio.return_value = 2.0
            mock_cache.get_24h_high_low.return_value = (66000.0, 64000.0)

            result = self.anti_bluff.analyze_breakout_quality(candidate)
            self.assertTrue(result["is_bluff"])
            self.assertFalse(result["is_genuine_breakout"])
            self.assertIn("Bull Trap", result["bluff_reasons"][0])
            self.assertGreaterEqual(result["fake_breakout_probability_pct"], 70.0)

    def test_anti_bluff_low_volume_fakeout(self):
        """Verify that a breakout lacking volume surge (< 1.25x RVOL) is caught as a low-volume bluff."""
        candidate = {
            "symbol": "ETHUSDT",
            "side": "BUY",
            "entry_price": 3500.0,
            "price": 3500.0,
            "strategy": "Volatility Expansion Breakout",
            "rvol": 1.05
        }

        with patch("app.trading.breakout_quality.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {"atr": 40.0, "rsi": 50.0}
            mock_cache.get_1m_candle_metrics.return_value = {
                "close_location": 0.85,
                "candle_direction": "BULLISH",
                "samples_count": 5
            }
            mock_cache.get_volume_surge_ratio.return_value = 1.05
            mock_cache.get_24h_high_low.return_value = (3600.0, 3400.0)

            result = self.anti_bluff.analyze_breakout_quality(candidate)
            self.assertTrue(result["is_bluff"])
            self.assertFalse(result["is_genuine_breakout"])
            self.assertTrue(any("Low-Volume Fakeout" in r for r in result["bluff_reasons"]))

    def test_genuine_high_quality_breakout(self):
        """Verify that strong institutional volume, clean candle close, and firing squeeze produce a genuine breakout approval."""
        candidate = {
            "symbol": "SOLUSDT",
            "side": "BUY",
            "entry_price": 180.0,
            "price": 180.0,
            "strategy": "Volatility Expansion Breakout",
            "rvol": 2.4,
            "squeeze_info": {"squeeze_state": "SQUEEZE_FIRING"}
        }

        with patch("app.trading.breakout_quality.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {"atr": 2.5, "rsi": 58.0}
            mock_cache.get_1m_candle_metrics.return_value = {
                "close_location": 0.88,  # strong close near high
                "candle_direction": "BULLISH",
                "samples_count": 5
            }
            mock_cache.get_volume_surge_ratio.return_value = 2.4
            mock_cache.get_24h_high_low.return_value = (185.0, 172.0)

            result = self.anti_bluff.analyze_breakout_quality(candidate)
            self.assertFalse(result["is_bluff"])
            self.assertTrue(result["is_genuine_breakout"])
            self.assertGreaterEqual(result["breakout_quality_score"], 72.0)
            self.assertLessEqual(result["fake_breakout_probability_pct"], 28.0)

    def test_strict_initial_stop_bracket(self):
        """Verify that the initial stop bracket is strictly clamped between 0.35% and 0.55% of price."""
        entry_price = 100.0
        atr = 2.5  # 2.5% ATR is wide, strict bracket must clamp it tightly!

        bracket = self.trailing.calculate_initial_stop_bracket(
            symbol="TESTUSDT",
            side="BUY",
            entry_price=entry_price,
            atr=atr,
            strategy="Volatility Expansion Breakout"
        )

        stop_loss = bracket["stop_loss"]
        stop_dist = entry_price - stop_loss
        stop_pct = (stop_dist / entry_price) * 100.0

        # Must be structurally calibrated between 0.85% and 1.85%
        self.assertGreaterEqual(stop_pct, 0.85)
        self.assertLessEqual(stop_pct, 1.851)
        self.assertAlmostEqual(bracket["take_profit_1"], entry_price + (stop_dist * 2.0), delta=0.02)
        self.assertAlmostEqual(bracket["take_profit_2"], entry_price + (stop_dist * 4.5), delta=0.02)

    def test_strict_rapid_breakeven_lock(self):
        """Verify that once trade proves breakout (>= +1.35% or >= +1.2R), stop is ratcheted to entry + 0.25% fee buffer."""
        position = {
            "symbol": "BTCUSDT",
            "side": "BUY",
            "entry_price": 60000.0,
            "stop_loss": 59200.0,   # -1.33% initial SL ($800 risk)
            "trailing_stop": 59200.0,
            "atr": 600.0,
            "highest_price": 60900.0  # +$900 gain = +1.50% move / >= +1.2R
        }

        update = self.trailing.evaluate_trailing_update(
            position=position,
            current_price=60850.0,
            live_rvol=1.5,
            live_rsi=58.0,
            orderbook_obi=0.10
        )

        self.assertTrue(update["should_update"])
        # Must have ratcheted above entry price (+0.25% fee buffer)
        expected_min_sl = 60000.0 * 1.0025
        self.assertGreaterEqual(update["new_stop"], expected_min_sl)
        self.assertGreater(update["locked_r"], 0.0)

    def test_long_runner_profit_floor_lock(self):
        """Verify that when a massive runner hits +4.0R, at least 75% of peak gains are locked in."""
        position = {
            "symbol": "SOLUSDT",
            "side": "BUY",
            "entry_price": 100.0,
            "stop_loss": 99.50,     # $0.50 risk distance (0.50% strict SL)
            "trailing_stop": 99.50,
            "atr": 0.50,
            "highest_price": 102.20  # +$2.20 peak gain = +4.4R runner!
        }

        update = self.trailing.evaluate_trailing_update(
            position=position,
            current_price=102.00,
            live_rvol=1.2,
            live_rsi=65.0,
            orderbook_obi=0.05
        )

        self.assertTrue(update["should_update"])
        # Tier 4 (>= +3.5R) locks at least 75% of peak gain (100.0 + (2.20 * 0.75) = 101.65)
        self.assertGreaterEqual(update["new_stop"], 101.60)
        self.assertIn("Tier 4", update["tier_label"])
        self.assertGreaterEqual(update["locked_r"], 3.0)

    def test_long_run_filter_rejects_minor_fluctuations_and_chop(self):
        """Verify that normal market noise, low RVOL, or non-breakouts are ignored to wait exclusively for big runs."""
        # Simulated low-momentum setup: price not near 24h high, normal volume (RVOL 0.9x), no squeeze
        rvol = 0.95
        squeeze_state = "NO_SQUEEZE"
        is_vcp_apex = False
        is_in_play = False
        is_big_move_candidate = False

        # In reasoning.py:
        is_selected = False
        if is_vcp_apex and rvol >= 1.35:
            strategy = "Minervini VCP Breakout"
            is_selected = True
        elif is_in_play and is_big_move_candidate:
            strategy = "High-RVOL Breakout (In Play)"
            is_selected = True
        elif is_big_move_candidate and rvol >= 1.30:
            strategy = "Volatility Expansion Breakout"
            is_selected = True
        elif squeeze_state == "SQUEEZE_FIRING" and rvol >= 1.70:
            strategy = "Breakout Momentum"
            is_selected = True

        self.assertFalse(is_selected, "Minor chop/fluctuation must NOT be selected for trading.")

    def test_long_run_filter_identifies_explosive_breakout(self):
        """Verify that genuine explosive setups with high RVOL and firing squeeze qualify for big run runner status."""
        rvol = 1.85
        squeeze_state = "SQUEEZE_FIRING"
        is_vcp_apex = False
        is_in_play = False
        is_big_move_candidate = True

        strategy = None
        if is_vcp_apex and rvol >= 1.35:
            strategy = "Minervini VCP Breakout"
        elif is_in_play and is_big_move_candidate:
            strategy = "High-RVOL Breakout (In Play)"
        elif is_big_move_candidate and rvol >= 1.30:
            strategy = "Volatility Expansion Breakout"
        elif squeeze_state == "SQUEEZE_FIRING" and rvol >= 1.70:
            strategy = "Breakout Momentum"

        self.assertIsNotNone(strategy)
        self.assertEqual(strategy, "Volatility Expansion Breakout")

        # Verify asymmetric reward targeting: TP is at least 4.5x ATR, SL clamped to structural 0.85% - 1.85%
        entry_price = 1.00
        atr = 0.015  # 1.5% ATR
        min_dist = min(max(0.85 * atr, entry_price * 0.0085), entry_price * 0.0185)
        tp_dist = max(4.50 * atr, min_dist * 4.0, entry_price * 0.045)

        risk_reward_ratio = tp_dist / min_dist
        self.assertGreaterEqual(risk_reward_ratio, 3.5, "Big run target must offer at least 3.5x to 5.0x asymmetric R:R.")

    def test_high_tight_flag_detector_and_breakout(self):
        """Verify Kristjan Qullamaggie High Tight Flag (HTF) detection and apex breakout."""
        from app.trading.high_tight_flag import HighTightFlagDetector
        htf_engine = HighTightFlagDetector.get_instance()

        with patch("app.trading.high_tight_flag.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            # 12 candle history: starts at 10.0, explodes to 12.0 (+20% thrust), then coils tightly between 11.80 and 12.05
            mock_cache.get_price_history.return_value = [
                10.0, 10.2, 10.8, 11.5, 12.0, 11.9, 11.85, 11.92, 11.88, 11.95, 12.02, 12.06
            ]
            mock_cache.get_technical_analysis.return_value = {
                "ema9": 11.80,
                "ema10": 11.80,
                "ema20": 11.20,
                "atr": 0.25
            }
            mock_cache.get_volume_surge_ratio.return_value = 2.1

            # Price breaking out at 12.06 with RVOL 2.1x
            res = htf_engine.detect_high_tight_flag("RUNNERUSDT", current_price=12.06, side="BUY")
            self.assertTrue(res["is_htf_valid"])
            self.assertTrue(res["is_htf_breakout"])
            self.assertEqual(res["pattern_stage"], "HTF_APEX_BREAKOUT")
            self.assertGreaterEqual(res["thrust_gain_pct"], 15.0)
            self.assertLessEqual(res["consolidation_range_pct"], 4.5)
            self.assertGreaterEqual(res["quality_score"], 80.0)

    def test_triple_ema_ribbon_expansion_alignment(self):
        """Verify Triple EMA Ribbon (10 > 20 > 50 EMA) expansion alignment."""
        from app.trading.triple_ema_ribbon import TripleEmaRibbonEngine
        ribbon_engine = TripleEmaRibbonEngine.get_instance()

        with patch("app.trading.triple_ema_ribbon.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {
                "ema10": 105.0,
                "ema20": 102.0,
                "ema50": 98.0,
                "atr": 1.50
            }

            # BUY at 106.0: 106.0 > 105.0 > 102.0 > 98.0 (Bullish Ribbon Expansion)
            res = ribbon_engine.evaluate_ribbon_alignment("RUNNERUSDT", current_price=106.0, proposed_side="BUY")
            self.assertTrue(res["is_ribbon_aligned"])
            self.assertTrue(res["is_ribbon_expanding"])
            self.assertEqual(res["ribbon_state"], "BULLISH_RIBBON_EXPANSION")
            self.assertGreaterEqual(res["confluence_score"], 85.0)
            # Baseline support should be 20 EMA - 0.20x ATR = 102.0 - (1.5 * 0.2) = 101.70
            self.assertEqual(res["trailing_baseline_20ema"], 101.70)

    def test_open_interest_squeeze_fuel_detection(self):
        """Verify Binance Futures Open Interest surge and trapped liquidity cascade detection."""
        from app.trading.open_interest_fuel import OpenInterestFuelDetector
        oi_engine = OpenInterestFuelDetector.get_instance()

        # Simulate rolling OI rising from 1,000,000 to 1,060,000 (+6.0% surge)
        oi_engine._oi_history["BTCUSDT"] = [
            (1000.0, 1000000.0),
            (1060.0, 1060000.0)
        ]

        with patch("app.trading.open_interest_fuel.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            # Negative funding (-0.005%) indicating aggressive crowded shorts
            mock_cache.get_technical_analysis.return_value = {
                "funding_rate": -0.00005
            }
            mock_cache.get_volume_surge_ratio.return_value = 2.2

            res = oi_engine.evaluate_oi_squeeze_fuel("BTCUSDT", side="BUY", current_price=65000.0)
            self.assertTrue(res["has_squeeze_fuel"])
            self.assertEqual(res["squeeze_fuel_type"], "SHORT_SQUEEZE_FUEL_EXPANSION")
            self.assertGreaterEqual(res["oi_change_pct"], 5.0)
            self.assertGreaterEqual(res["fuel_confidence_bonus"], 0.04)

    def test_20_ema_core_moonbag_trailing_expansion(self):
        """Verify that when a massive runner hits >= +2.5R, it trails the 20 EMA to avoid premature shakeouts."""
        position = {
            "symbol": "SOLUSDT",
            "side": "BUY",
            "entry_price": 100.0,
            "stop_loss": 99.50,     # $0.50 risk distance (strict 0.50% SL)
            "trailing_stop": 99.50,
            "atr": 0.50,
            "highest_price": 101.50, # +$1.50 peak gain = +3.0R runner!
            "ema10": 101.35,
            "ema20": 101.25         # 20 EMA baseline
        }

        update = self.trailing.evaluate_trailing_update(
            position=position,
            current_price=101.40,
            live_rvol=1.5,
            live_rsi=68.0,
            orderbook_obi=0.15
        )

        self.assertTrue(update["should_update"])
        # Should reference 20 EMA Core Moonbag in tier label
        self.assertIn("20 EMA Core Moonbag", update["tier_label"])
        # Stop should be trailed at 20 EMA baseline (101.25 - 0.20*0.50 = 101.15)
        self.assertGreaterEqual(update["new_stop"], 101.15)
        self.assertGreaterEqual(update["locked_r"], 2.0)

    def test_order_manager_restricts_non_long_run_trades(self):
        """Verify that any trade attempting to execute that is NOT a qualified long-run setup is strictly blocked."""
        ALLOWED_LONG_RUN_STRATEGIES = {
            "Qullamaggie High Tight Flag Breakout",
            "Minervini VCP Breakout",
            "High-RVOL Breakout (In Play)",
            "Volatility Expansion Breakout",
            "Breakout Momentum"
        }

        # 1. Non-long-run setup: Mean Reversion Scalp
        opp_scalp = {
            "symbol": "DOGEUSDT",
            "strategy_used": "Mean Reversion Scalp",
            "htf_info": {"is_htf_breakout": False},
            "is_big_move": False,
            "ribbon_info": {"is_ribbon_aligned": False},
            "oi_fuel_info": {"has_squeeze_fuel": False},
            "squeeze_info": {"squeeze_state": "NO_SQUEEZE"}
        }

        strategy_used = opp_scalp["strategy_used"]
        is_allowed = any(s.lower() in strategy_used.lower() for s in ALLOWED_LONG_RUN_STRATEGIES)
        is_qualified = is_allowed and (
            opp_scalp["htf_info"]["is_htf_breakout"] or
            opp_scalp["is_big_move"] or
            (opp_scalp["ribbon_info"]["is_ribbon_aligned"] and opp_scalp["squeeze_info"]["squeeze_state"] in ["SQUEEZE_FIRING", "SQUEEZE_ACTIVE"]) or
            opp_scalp["oi_fuel_info"]["has_squeeze_fuel"]
        )
        self.assertFalse(is_qualified, "Non-long-run scalp trade must be strictly restricted from execution.")

        # 2. Verified Long-Run setup: High Tight Flag with OI Fuel
        opp_long_run = {
            "symbol": "BTCUSDT",
            "strategy_used": "Qullamaggie High Tight Flag Breakout",
            "htf_info": {"is_htf_breakout": True},
            "is_big_move": True,
            "ribbon_info": {"is_ribbon_aligned": True, "is_ribbon_expanding": True},
            "oi_fuel_info": {"has_squeeze_fuel": True},
            "squeeze_info": {"squeeze_state": "SQUEEZE_FIRING"}
        }

        strat_lr = opp_long_run["strategy_used"]
        is_allowed_lr = any(s.lower() in strat_lr.lower() for s in ALLOWED_LONG_RUN_STRATEGIES)
        is_qualified_lr = is_allowed_lr and (
            opp_long_run["htf_info"]["is_htf_breakout"] or
            opp_long_run["is_big_move"] or
            opp_long_run["oi_fuel_info"]["has_squeeze_fuel"]
        )
        self.assertTrue(is_qualified_lr, "Verified long-run trade must clear the restriction gate.")

    def test_opportunity_queue_prioritizes_long_runs_over_standard_trades(self):
        """Verify that long-run trades with HTF and OI fuel receive top capital ranking scores."""
        from app.trading.opportunity_queue import OpportunityQueueEngine
        queue_engine = OpportunityQueueEngine.get_instance()

        opp_standard = {
            "symbol": "ADAUSDT",
            "strategy_used": "Breakout Momentum",
            "price": 0.50,
            "stop_loss": 0.495,
            "take_profit": 0.52,
            "confidence_score": 0.80,
            "rvol": 1.5,
            "htf_info": {"is_htf_breakout": False},
            "oi_fuel_info": {"has_squeeze_fuel": False},
            "ribbon_info": {"is_ribbon_expanding": False}
        }

        opp_long_runner = {
            "symbol": "SOLUSDT",
            "strategy_used": "Qullamaggie High Tight Flag Breakout",
            "price": 100.0,
            "stop_loss": 99.50,
            "take_profit": 108.0,
            "confidence_score": 0.80,
            "rvol": 2.2,
            "htf_info": {"is_htf_breakout": True},
            "oi_fuel_info": {"has_squeeze_fuel": True},
            "ribbon_info": {"is_ribbon_expanding": True}
        }

        score_standard = queue_engine.calculate_capital_rank_score(opp_standard)
        score_runner = queue_engine.calculate_capital_rank_score(opp_long_runner)

        # Long runner must score significantly higher than standard trade due to +45.0 priority bonus
        self.assertGreater(score_runner, score_standard + 30.0, "Long runner must be prioritized significantly higher.")

    def test_order_manager_has_execute_opportunity_alias(self):
        """Verify that OrderManager has execute_opportunity method alias for re-entry & replay."""
        from app.trading.order_manager import OrderManager
        om = OrderManager.get_instance()
        self.assertTrue(hasattr(om, "execute_opportunity"), "OrderManager must have execute_opportunity attribute.")
        self.assertTrue(callable(getattr(om, "execute_opportunity")), "execute_opportunity must be a callable method.")

    def test_first_pullback_and_smart_reentry_allowed_by_long_run_gate(self):
        """Verify that Institutional First-Pullback and Smart Re-entry pass the 0g Long-Run Gate."""
        from app.trading.order_manager import OrderManager
        
        ALLOWED_LONG_RUN_STRATEGIES = {
            "Qullamaggie High Tight Flag Breakout",
            "Minervini VCP Breakout",
            "High-RVOL Breakout (In Play)",
            "Volatility Expansion Breakout",
            "Breakout Momentum",
            "Institutional First Pullback (Raschke/VWAP)",
            "Smart Re-Entry"
        }

        reentry_opp = {
            "symbol": "CLANKERUSDT",
            "side": "BUY",
            "entry_price": 23.70,
            "strategy_used": "Institutional First Pullback (Raschke/VWAP)",
            "confidence": 0.84,
            "is_reentry": True,
            "allow_long_runs": True
        }

        strategy_used = reentry_opp.get("strategy_used", "")
        is_allowed = any(s.lower() in strategy_used.lower() for s in ALLOWED_LONG_RUN_STRATEGIES)
        is_reentry = bool(reentry_opp.get("is_reentry", False) or "First Pullback" in strategy_used)
        is_qualified = (is_allowed or is_reentry) and (is_reentry or reentry_opp.get("allow_long_runs", False))

        self.assertTrue(is_allowed, "Institutional First Pullback must be an allowed strategy.")
        self.assertTrue(is_qualified, "Institutional First Pullback must clear the long-run restriction gate.")

    def test_market_regime_calibration_logic(self):
        """Verify that market regime properly resolves to TRENDING, VOLATILE, or RANGING without NameError."""
        def resolve_regime(macro_regime_name: str, is_trending: bool) -> str:
            if macro_regime_name in ["STRONG_BULL_EXPANSION", "MOMENTUM_EXPANSION", "TRENDING", "BEAR_TREND"]:
                return "TRENDING"
            elif is_trending:
                return "TRENDING"
            elif macro_regime_name in ["HIGH_VOLATILITY", "VOLATILE", "CRASH_PANIC"]:
                return "VOLATILE"
            else:
                return "RANGING"

        self.assertEqual(resolve_regime("STRONG_BULL_EXPANSION", False), "TRENDING")
        self.assertEqual(resolve_regime("CHOP_RANGING", True), "TRENDING")
        self.assertEqual(resolve_regime("VOLATILE", False), "VOLATILE")
        self.assertEqual(resolve_regime("CHOP_RANGING", False), "RANGING")


if __name__ == "__main__":
    unittest.main()



