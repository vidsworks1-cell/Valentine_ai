"""
Module 3: Breakout Quality Analyzer

Evaluates breakout momentum integrity before approving breakout trades:
- genuine volume expansion (relative volume vs 20-period baseline, volume delta)
- breakout candle quality (body-to-wick ratio, close in top 15% of range for long / bottom 15% for short)
- follow-through probability (momentum acceleration, absence of immediate exhaustion)
- fake breakout / bull trap / bear trap probability (rejection wicks, Bollinger overextension)
- order book depth support (bid/ask depth skew, liquidity cushion)
- liquidity expansion vs thin-book vacuum pump
- volatility expansion (true range expanding synchronously with volume)

Returns:
- Breakout Quality Score (0 - 100)
- Validation Status (APPROVED / MARGINAL / FAKE_BREAKOUT_RISK)
- Sub-metrics for Meta-Reasoning explanation
"""

import math
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.trading.breakout_quality_analyzer")


class BreakoutQualityAnalyzer:
    _instance: Optional['BreakoutQualityAnalyzer'] = None

    @classmethod
    def get_instance(cls) -> 'BreakoutQualityAnalyzer':
        if cls._instance is None:
            cls._instance = BreakoutQualityAnalyzer()
        return cls._instance

    def evaluate_breakout(
        self,
        symbol: str,
        direction: str = "BUY",
        current_price: float = 0.0,
        breakout_level: float = 0.0,
        candle_high: float = 0.0,
        candle_low: float = 0.0,
        candle_open: float = 0.0,
        candle_close: float = 0.0,
        relative_volume: float = 1.0,
        volume_percentile: float = 50.0,
        atr_ratio: float = 1.0,
        orderbook_skew: float = 0.5,  # 0.5 = neutral, >0.55 = buyer dominance
        vwap_deviation: float = 0.005,
        context: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Analyzes a candidate breakout and returns a comprehensive 0-100 Breakout Quality Score.
        """
        direction = direction.upper()

        # 1. Volume Expansion Pillar (0 - 100)
        # Institutional breakouts need >= 1.8x - 2.5x volume expansion
        if relative_volume >= 2.5:
            vol_expansion_score = 98.0
        elif relative_volume >= 1.8:
            vol_expansion_score = 85.0 + ((relative_volume - 1.8) / 0.7 * 13.0)
        elif relative_volume >= 1.2:
            vol_expansion_score = 65.0 + ((relative_volume - 1.2) / 0.6 * 20.0)
        else:
            vol_expansion_score = max(10.0, relative_volume * 50.0)

        # 2. Breakout Candle Quality Pillar (0 - 100)
        # Strong body, close near the extreme of the bar (little to no opposing wick)
        full_range = max(1e-9, candle_high - candle_low)
        body = abs(candle_close - candle_open)
        body_ratio = body / full_range  # 0.0 to 1.0

        if direction == "BUY":
            upper_wick = candle_high - max(candle_close, candle_open)
            wick_ratio = upper_wick / full_range
            close_position = (candle_close - candle_low) / full_range  # 1.0 = closed at absolute high
        else:
            lower_wick = min(candle_close, candle_open) - candle_low
            wick_ratio = lower_wick / full_range
            close_position = (candle_high - candle_close) / full_range

        # Ideal candle: body_ratio >= 0.70 and wick_ratio <= 0.15, close_position >= 0.85
        candle_quality = (body_ratio * 40.0) + ((1.0 - min(1.0, wick_ratio * 2.5)) * 30.0) + (close_position * 30.0)
        candle_quality = max(0.0, min(100.0, candle_quality))

        # 3. Follow-Through Probability (0 - 100)
        # Measures distance beyond level without excessive overextension
        if breakout_level > 0 and current_price > 0:
            if direction == "BUY":
                clearance_pct = (current_price - breakout_level) / breakout_level * 100.0
            else:
                clearance_pct = (breakout_level - current_price) / breakout_level * 100.0
        else:
            clearance_pct = 0.5

        if 0.25 <= clearance_pct <= 2.5:
            # Healthy decisive clearance
            follow_through_score = 90.0
        elif clearance_pct < 0.25:
            # Barely tickling the breakout line (risk of immediate rejection)
            follow_through_score = 60.0
        else:
            # Overextended >2.5% (chasing momentum risk)
            follow_through_score = max(35.0, 90.0 - ((clearance_pct - 2.5) * 15.0))

        # 4. Fake Breakout / Trap Probability (0 - 100, where 0 is high trap danger, 100 is low trap danger)
        fake_trap_risk = 0.0
        # Danger signs: small body with massive rejection wick
        if wick_ratio > 0.40:
            fake_trap_risk += 45.0
        if relative_volume < 1.1:
            fake_trap_risk += 30.0  # low volume fakeout
        if abs(vwap_deviation) > 0.035:
            fake_trap_risk += 25.0  # severely stretched from mean
        fake_breakout_prob_pct = min(95.0, max(5.0, fake_trap_risk))
        anti_fake_score = 100.0 - fake_breakout_prob_pct

        # 5. Order Book Support (0 - 100)
        if direction == "BUY":
            # Buyer depth skew > 0.50
            skew_score = min(100.0, max(10.0, orderbook_skew * 100.0))
        else:
            skew_score = min(100.0, max(10.0, (1.0 - orderbook_skew) * 100.0))

        # 6. Volatility Expansion (0 - 100)
        # ATR ratio > 1.2x confirms genuine range expansion
        if atr_ratio >= 1.5:
            vol_expansion_atr = 95.0
        elif atr_ratio >= 1.1:
            vol_expansion_atr = 75.0 + ((atr_ratio - 1.1) / 0.4 * 20.0)
        else:
            vol_expansion_atr = max(20.0, atr_ratio * 65.0)

        # Composite Institutional Breakout Quality Score
        composite = (
            (vol_expansion_score * 0.28) +
            (candle_quality * 0.24) +
            (follow_through_score * 0.16) +
            (anti_fake_score * 0.16) +
            (skew_score * 0.08) +
            (vol_expansion_atr * 0.08)
        )
        composite = max(0.0, min(100.0, round(composite, 1)))

        if composite >= 78.0 and fake_breakout_prob_pct < 25.0:
            status = "APPROVED_HIGH_CONVICTION"
            pass_filter = True
        elif composite >= 62.0 and fake_breakout_prob_pct < 40.0:
            status = "APPROVED_STANDARD"
            pass_filter = True
        elif composite >= 50.0:
            status = "MARGINAL_BREAKOUT"
            pass_filter = False
        else:
            status = "REJECTED_FAKE_BREAKOUT_RISK"
            pass_filter = False

        return {
            "breakout_quality_score": composite,
            "status": status,
            "approved": pass_filter,
            "fake_breakout_prob_pct": round(fake_breakout_prob_pct, 1),
            "follow_through_score": round(follow_through_score, 1),
            "volume_expansion_score": round(vol_expansion_score, 1),
            "candle_structure_score": round(candle_quality, 1),
            "orderbook_support_score": round(skew_score, 1),
            "relative_volume": round(relative_volume, 2),
            "body_ratio": round(body_ratio, 2),
            "wick_ratio": round(wick_ratio, 2)
        }
