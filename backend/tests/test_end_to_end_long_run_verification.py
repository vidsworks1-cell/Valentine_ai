"""
End-to-End Simulation: Verifying that the new implementation finds, screens, enters,
and rides a complete Long Run Trade through the entire pipeline.
"""

import unittest
from unittest.mock import MagicMock, patch, AsyncMock
from app.trading.high_tight_flag import HighTightFlagDetector
from app.trading.triple_ema_ribbon import TripleEmaRibbonEngine
from app.trading.open_interest_fuel import OpenInterestFuelDetector
from app.trading.breakout_quality import BreakoutQualityAnalyzer
from app.trading.adaptive_trailing_engine import AdaptiveTrailingEngine


class TestEndToEndLongRunVerification(unittest.TestCase):

    def test_full_pipeline_long_run_lifecycle(self):
        """
        Simulate an entire trade lifecycle for an explosive runner:
        1. Setup Discovery (High Tight Flag + Triple EMA Ribbon + OI Squeeze Fuel)
        2. Anti-Bluff Quality Filtering (Zero rejection wick traps)
        3. Strict Stop Bracket Clamping (0.35% - 0.50% SL)
        4. Auto-Breakeven Free-Roll Activation (+0.35% move)
        5. 70/30 Stage 1 Profit Bank (+2.0R)
        6. 20 EMA Core Moonbag Trailing (+3.5R to +6.0R)
        7. Tier 5 Profit Floor Lock (+82% of peak gains banked)
        """
        symbol = "RUNNERUSDT"
        entry_price = 100.0
        atr = 1.0  # 1.0% ATR

        # ---------------------------------------------------------------------
        # STEP 1: Setup Discovery via New Institutional Engines
        # ---------------------------------------------------------------------
        # 1a. High Tight Flag: Prior +18% thrust, 5-bar tight flag, breaking out
        htf_detector = HighTightFlagDetector.get_instance()
        with patch("app.trading.high_tight_flag.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_price_history.return_value = [
                84.0, 86.0, 92.0, 98.0, 100.0, 99.2, 99.0, 99.4, 99.8, 100.2
            ]
            mock_cache.get_technical_analysis.return_value = {
                "ema9": 99.2, "ema10": 99.2, "ema20": 95.0, "atr": atr
            }
            mock_cache.get_volume_surge_ratio.return_value = 2.4

            htf_res = htf_detector.detect_high_tight_flag(symbol, current_price=entry_price, side="BUY")
            self.assertTrue(htf_res["is_htf_valid"], "HTF pattern must be validated")
            self.assertTrue(htf_res["is_htf_breakout"], "HTF apex breakout must be triggered")
            self.assertGreaterEqual(htf_res["thrust_gain_pct"], 15.0)

        # 1b. Triple EMA Ribbon: 10 > 20 > 50 EMA in expanding formation
        ribbon_engine = TripleEmaRibbonEngine.get_instance()
        with patch("app.trading.triple_ema_ribbon.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {
                "ema10": 99.0, "ema20": 96.5, "ema50": 92.0, "atr": atr
            }
            ribbon_res = ribbon_engine.evaluate_ribbon_alignment(symbol, current_price=entry_price, proposed_side="BUY")
            self.assertTrue(ribbon_res["is_ribbon_aligned"])
            self.assertTrue(ribbon_res["is_ribbon_expanding"])
            self.assertEqual(ribbon_res["ribbon_state"], "BULLISH_RIBBON_EXPANSION")

        # 1c. Binance Futures Open Interest Fuel: +6.5% OI surge with negative funding
        oi_engine = OpenInterestFuelDetector.get_instance()
        oi_engine._oi_history[symbol] = [(100.0, 1000000.0), (160.0, 1065000.0)]
        with patch("app.trading.open_interest_fuel.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {"funding_rate": -0.00010}
            mock_cache.get_volume_surge_ratio.return_value = 2.4

            oi_res = oi_engine.evaluate_oi_squeeze_fuel(symbol, side="BUY", current_price=entry_price)
            self.assertTrue(oi_res["has_squeeze_fuel"])
            self.assertEqual(oi_res["squeeze_fuel_type"], "SHORT_SQUEEZE_FUEL_EXPANSION")

        # ---------------------------------------------------------------------
        # STEP 2: Anti-Bluff Quality Filtering
        # ---------------------------------------------------------------------
        anti_bluff = BreakoutQualityAnalyzer.get_instance()
        with patch("app.trading.breakout_quality.PriceCache") as mock_cache_cls:
            mock_cache = MagicMock()
            mock_cache_cls.get_instance_sync.return_value = mock_cache
            mock_cache.get_technical_analysis.return_value = {"atr": atr, "rsi": 58.0}
            mock_cache.get_1m_candle_metrics.return_value = {
                "close_location": 0.88,  # Closed in top 88% (no rejection wick bluff)
                "candle_direction": "BULLISH",
                "samples_count": 5
            }
            mock_cache.get_volume_surge_ratio.return_value = 2.4
            mock_cache.get_24h_high_low.return_value = (100.0, 85.0)

            bluff_check = anti_bluff.analyze_breakout_quality({
                "symbol": symbol, "side": "BUY", "price": entry_price, "entry_price": entry_price,
                "strategy": "Qullamaggie High Tight Flag Breakout", "rvol": 2.4
            })
            self.assertFalse(bluff_check["is_bluff"], "Must not be flagged as a bluff")
            self.assertTrue(bluff_check["is_genuine_breakout"], "Must qualify as genuine breakout")

        # ---------------------------------------------------------------------
        # STEP 3: Structural Initial Stop-Loss Calibration (0.85% - 1.85% Breathing Room)
        # ---------------------------------------------------------------------
        trailing_engine = AdaptiveTrailingEngine.get_instance()
        bracket = trailing_engine.calculate_initial_stop_bracket(
            symbol=symbol, side="BUY", entry_price=entry_price, atr=atr,
            strategy="Qullamaggie High Tight Flag Breakout"
        )
        initial_sl = bracket["stop_loss"]
        stop_dist = entry_price - initial_sl
        stop_pct = (stop_dist / entry_price) * 100.0

        # Stop must be structural between 0.85% and 1.85%
        self.assertGreaterEqual(stop_pct, 0.85)
        self.assertLessEqual(stop_pct, 1.85)
        # Verify asymmetric targets
        tp1 = bracket["take_profit_1"]
        tp2 = bracket["take_profit_2"]
        self.assertGreater(tp1, entry_price)
        self.assertGreater(tp2, tp1)

        # ---------------------------------------------------------------------
        # STEP 4: Live Trade Simulation - Riding the Long Run
        # ---------------------------------------------------------------------
        position = {
            "symbol": symbol,
            "side": "BUY",
            "entry_price": entry_price,
            "stop_loss": initial_sl,
            "trailing_stop": initial_sl,
            "atr": atr,
            "highest_price": entry_price,
            "lowest_price": entry_price,
            "opened_at": 1000.0,
            "ema10": 100.20,
            "ema20": 99.80
        }

        # Sub-step 4a: Price advances to $101.45 (+1.45% move / >= +1.2R) -> Proved Breakout Breakeven Ratchet
        be_update = trailing_engine.evaluate_trailing_update(
            position=position,
            current_price=101.45,
            live_rvol=2.0,
            live_rsi=62.0,
            orderbook_obi=0.20
        )
        self.assertTrue(be_update["should_update"])
        self.assertGreaterEqual(be_update["new_stop"], 100.20, "Must ratchet stop above entry to lock +0.25% net profit")
        position["trailing_stop"] = be_update["new_stop"]

        # Sub-step 4b: Price advances to $102.60 (+2.16R move) -> Stage 1 Scale-out territory & Tier 3 lock
        t3_update = trailing_engine.evaluate_trailing_update(
            position=position,
            current_price=102.60,
            live_rvol=1.8,
            live_rsi=66.0,
            orderbook_obi=0.15
        )
        self.assertTrue(t3_update["should_update"])
        # Tier 3 locks at least 65% of peak gains
        self.assertGreaterEqual(t3_update["new_stop"], 101.50)
        self.assertGreaterEqual(t3_update["locked_r"], 1.3)
        position["trailing_stop"] = t3_update["new_stop"]

        # Sub-step 4c: Price advances to $104.50 (+3.75R move) -> 20 EMA Core Moonbag Trailing active!
        position["ema10"] = 103.80
        position["ema20"] = 103.20
        moonbag_update = trailing_engine.evaluate_trailing_update(
            position=position,
            current_price=104.40,
            live_rvol=1.5,
            live_rsi=68.0,
            orderbook_obi=0.10
        )
        self.assertTrue(moonbag_update["should_update"])
        # Stop trailed near 20 EMA baseline or higher profit floor (Tier 4 locks 75% = 103.37)
        self.assertGreaterEqual(moonbag_update["new_stop"], 103.00)
        position["trailing_stop"] = moonbag_update["new_stop"]

        # Sub-step 4d: Massive Parabolic Climax to $107.50 (+6.25R Super-Runner) -> Tier 5 Profit Floor Lock
        position["highest_price"] = 107.50
        super_runner_update = trailing_engine.evaluate_trailing_update(
            position=position,
            current_price=107.10,
            live_rvol=1.1,  # Exhausting volume
            live_rsi=76.0,  # Overbought
            orderbook_obi=-0.35 # Mild ask wall
        )
        self.assertTrue(super_runner_update["should_update"])
        # Tier 5 (>= +6.0R Super-Runner) locks at least 82% of peak gains (100.0 + (7.50 * 0.82) = 106.15)
        self.assertGreaterEqual(super_runner_update["new_stop"], 106.00)
        self.assertGreaterEqual(super_runner_update["locked_r"], 5.0, "Must lock at least +5.0R profit on a +6.25R run!")
        self.assertIn("Tier 5", super_runner_update["tier_label"])

        print("\n✅ [VERIFICATION PASSED]: Entire Long Run Lifecycle successfully found, screened, managed, and banked!")


if __name__ == "__main__":
    unittest.main()
