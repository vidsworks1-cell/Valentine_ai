"""
Module 3: Breakout Quality Analyzer & Anti-Bluff Engine

Detects and eliminates "bluffs" before order execution:
1. Rejection Wick Traps (Bull/Bear Traps with >35% opposing wick)
2. Low-Volume Fakeouts (Breakouts lacking institutional RVOL surge)
3. Spoofed Order Book Imbalances (Negative OBI pressing directly against breakout)
4. Liquidity Sweep Fakeouts (Poking previous 24h high/low and falling back inside)
5. Overextension Climax Traps (Stretched >3.0x ATR from mean or extreme RSI)

Returns:
- breakout_quality_score: 0 - 100
- is_genuine_breakout: bool
- is_bluff: bool
- bluff_reasons: List[str]
- fake_breakout_probability_pct: float
- follow_through_probability_pct: float
"""

import math
import logging
from typing import Dict, Any, List, Optional
from app.workers.price_cache import PriceCache

logger = logging.getLogger("lumina.trading.breakout_quality")


class BreakoutQualityAnalyzer:
    _instance: Optional['BreakoutQualityAnalyzer'] = None

    @classmethod
    def get_instance(cls) -> 'BreakoutQualityAnalyzer':
        if cls._instance is None:
            cls._instance = BreakoutQualityAnalyzer()
        return cls._instance

    def analyze_breakout_quality(
        self,
        candidate_setup: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Deeply inspects candidate breakout against real live microstructure to stop bluffs.
        """
        symbol = str(candidate_setup.get("symbol", "UNKNOWN")).upper()
        strategy = str(candidate_setup.get("strategy_used", candidate_setup.get("strategy", "")))
        is_breakout_strategy = any(k in strategy.lower() for k in ["breakout", "squeeze", "momentum", "vcp", "expansion"])

        price = float(candidate_setup.get("price", candidate_setup.get("entry_price", 100.0)))
        side = candidate_setup.get("side", "BUY").upper()

        cache = PriceCache.get_instance_sync()
        ta = cache.get_technical_analysis(symbol)
        m1_data = cache.get_1m_candle_metrics(symbol)
        rvol_surge = cache.get_volume_surge_ratio(symbol)
        rvol = float(candidate_setup.get("rvol", rvol_surge or 1.5))
        high_24h, low_24h = cache.get_24h_high_low(symbol)

        atr = float(candidate_setup.get("atr", ta.get("atr", price * 0.015)))
        rsi_val = float(ta.get("rsi", 50.0))

        bluff_reasons: List[str] = []
        is_bluff = False

        # --- BLUFF CHECK 1: Rejection Wick & Candle Close Location (Bull/Bear Trap) ---
        close_loc = m1_data.get("close_location", 0.70)
        candle_dir = m1_data.get("candle_direction", "NEUTRAL")

        if m1_data.get("samples_count", 0) >= 2:
            if side == "BUY":
                # For BUY: If candle closes in the lower half (<0.55), top wick rejected = Bull Trap Bluff
                if close_loc < 0.55:
                    is_bluff = True
                    bluff_reasons.append(f"Bull Trap: 1m candle closed in bottom {close_loc*100:.0f}% of range (heavy upper rejection wick).")
                elif candle_dir == "BEARISH" and rvol < 1.60:
                    is_bluff = True
                    bluff_reasons.append(f"Opposing Bearish 1m candle on weak volume delta (RVOL {rvol:.2f}x).")
            else: # SELL
                # For SELL: If candle closes in upper half (>0.45), bottom wick rejected = Bear Trap Bluff
                if close_loc > 0.45:
                    is_bluff = True
                    bluff_reasons.append(f"Bear Trap: 1m candle closed in top {close_loc*100:.0f}% of range (heavy lower rejection wick).")
                elif candle_dir == "BULLISH" and rvol < 1.60:
                    is_bluff = True
                    bluff_reasons.append(f"Opposing Bullish 1m candle on weak volume delta (RVOL {rvol:.2f}x).")

        # --- BLUFF CHECK 2: Low-Volume Fakeout (Volume Vacuum) ---
        if rvol < 1.25 and rvol_surge < 1.25:
            is_bluff = True
            bluff_reasons.append(f"Low-Volume Fakeout: Volume surge ({max(rvol, rvol_surge):.2f}x < 1.25x threshold) indicates lack of institutional commitment.")

        # --- BLUFF CHECK 3: Order Book Microstructure & Spoof Resistance ---
        try:
            from app.trading.orderbook_l2_engine import OrderBookL2Engine
            ob_metrics = OrderBookL2Engine.get_instance().get_orderbook_metrics(symbol)
            obi = float(ob_metrics.get("obi_top5", 0.0))
            if side == "BUY" and obi <= -0.20:
                is_bluff = True
                bluff_reasons.append(f"Hostile Order Book: Heavy ask wall overhead (OBI {obi:.2f} < -0.20 threshold).")
            elif side == "SELL" and obi >= 0.20:
                is_bluff = True
                bluff_reasons.append(f"Hostile Order Book: Heavy bid wall beneath (OBI {obi:.2f} > +0.20 threshold).")
        except Exception:
            pass

        # --- BLUFF CHECK 4: Liquidity Sweep / Stop Hunt Bluff ---
        # If price briefly crossed the 24h high/low but has already slipped back below/above it
        if side == "BUY" and high_24h > 0:
            if price < (high_24h * 0.9985) and candidate_setup.get("is_big_move", False):
                is_bluff = True
                bluff_reasons.append(f"Failed Liquidity Sweep: Price pierced 24h high (${high_24h:.4f}) but collapsed back inside range.")
        elif side == "SELL" and low_24h > 0:
            if price > (low_24h * 1.0015) and candidate_setup.get("is_big_move", False):
                is_bluff = True
                bluff_reasons.append(f"Failed Liquidity Sweep: Price pierced 24h low (${low_24h:.4f}) but bounced back inside range.")

        # --- BLUFF CHECK 5: Overextension Exhaustion Climax ---
        if side == "BUY" and rsi_val >= 76.0 and close_loc < 0.70:
            is_bluff = True
            bluff_reasons.append(f"Exhaustion Climax: RSI overbought ({rsi_val:.1f}) with stalled candle close ({close_loc*100:.0f}%).")
        elif side == "SELL" and rsi_val <= 24.0 and close_loc > 0.30:
            is_bluff = True
            bluff_reasons.append(f"Exhaustion Climax: RSI oversold ({rsi_val:.1f}) with stalled candle close ({close_loc*100:.0f}%).")

        # --- SCORING & COMPOSITE CONFIRMATION ---
        # Volume expansion score
        vol_score = min(100.0, max(20.0, rvol * 45.0))
        # Structure score
        structure_score = 90.0 if not is_bluff else 30.0
        # Volatility squeeze score
        squeeze_info = candidate_setup.get("squeeze_info", {})
        squeeze_score = 95.0 if squeeze_info.get("squeeze_state") == "SQUEEZE_FIRING" else 75.0

        breakout_quality = (vol_score * 0.35) + (structure_score * 0.45) + (squeeze_score * 0.20)
        breakout_quality = round(max(10.0, min(99.0, breakout_quality)), 1)

        fakeout_prob = 75.0 if is_bluff else round(max(5.0, min(80.0, 100.0 - (breakout_quality * 0.95))), 1)
        follow_through_prob = round(100.0 - fakeout_prob, 1)

        is_genuine = (not is_bluff) and (breakout_quality >= 72.0) and (fakeout_prob <= 28.0)

        return {
            "breakout_quality_score": breakout_quality,
            "is_genuine_breakout": is_genuine,
            "is_bluff": is_bluff,
            "bluff_reasons": bluff_reasons,
            "fake_breakout_probability_pct": fakeout_prob,
            "follow_through_probability_pct": follow_through_prob,
            "diagnostic_breakdown": {
                "rvol": round(rvol, 2),
                "close_location": round(close_loc, 2),
                "candle_direction": candle_dir,
                "is_bluff": is_bluff,
                "reasons": bluff_reasons
            }
        }

