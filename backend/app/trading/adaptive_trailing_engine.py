"""
Adaptive Volatility-Clustered Trailing & Chandelier Engine (Wish 2)

Replaces rigid, static ATR multiples and static step trailing with:
1. Rolling Volatility Clustering (Quantile / Percentile Distribution):
   Distinguishes between COMPRESSION, NORMAL, EXPANSION, and HYPER_EXPANSION regimes.
2. Anti-Shakeout Elastic Buffer:
   Provides breathing room during the first 3-5 bars of an explosive breakout, preventing
   premature stop-outs during noise sweeps and retests.
3. Chandelier Parabolic Ratchet Matrix:
   Trails from the highest high (BUY) or lowest low (SELL) with an adaptive multiplier that
   contracts as profit expands or as momentum/volume exhausts.
4. Momentum & Orderflow Decay Acceleration:
   Rapidly ratchets stop closer when RVOL exhausts (< 0.8x) or order flow turns hostile (negative OBI),
   locking in 75%-85% of peak gains before the mean reversion cascade.
5. Invariant Non-Retrograde Ratchet:
   Mathematically guarantees stops only move in the favorable direction (never widens).
"""

import time
import math
import logging
from typing import Dict, Any, Tuple, Optional, List

logger = logging.getLogger("lumina.trading.adaptive_trailing_engine")

def _round_price(price: float) -> float:
    """Rounds price to appropriate significant precision to avoid truncation."""
    if price <= 0.0:
        return 0.0
    if price < 0.00001:
        return round(price, 10)
    elif price < 0.001:
        return round(price, 8)
    elif price < 1.0:
        return round(price, 6)
    elif price < 10.0:
        return round(price, 4)
    else:
        return round(price, 2)


class AdaptiveTrailingEngine:
    """Institutional-grade dynamic volatility trailing & chandelier exit engine."""
    _instance: Optional['AdaptiveTrailingEngine'] = None

    def __init__(self):
        # Rolling cache of recent ATR observations per symbol for empirical quantile calibration
        self._atr_history: Dict[str, List[float]] = {}
        self._max_history = 100

    @classmethod
    def get_instance(cls) -> 'AdaptiveTrailingEngine':
        if cls._instance is None:
            cls._instance = AdaptiveTrailingEngine()
        return cls._instance

    def record_atr(self, symbol: str, atr: float):
        """Records an ATR observation into rolling memory to maintain quantile distribution."""
        if atr <= 0.0:
            return
        if symbol not in self._atr_history:
            self._atr_history[symbol] = []
        history = self._atr_history[symbol]
        history.append(atr)
        if len(history) > self._max_history:
            history.pop(0)

    def calculate_volatility_quantile(self, symbol: str, current_atr: float, current_price: float) -> Tuple[float, str]:
        """
        Determines the empirical volatility quantile and assigned regime:
        - COMPRESSION (Q < 0.25): Squeezed volatility, low volatility band
        - NORMAL (0.25 <= Q <= 0.70): Stable market condition
        - EXPANSION (0.70 < Q <= 0.90): Active breakout / trending move
        - HYPER_EXPANSION (Q > 0.90): Parabolic blow-off, high risk of exhaustion
        """
        self.record_atr(symbol, current_atr)
        history = self._atr_history.get(symbol, [])

        if len(history) < 10:
            # Baseline estimation from ATR as % of price if history is short
            atr_pct = (current_atr / current_price * 100.0) if current_price > 0 else 1.5
            if atr_pct < 0.8:
                return 0.20, "COMPRESSION"
            elif atr_pct < 2.0:
                return 0.50, "NORMAL"
            elif atr_pct < 4.0:
                return 0.80, "EXPANSION"
            else:
                return 0.95, "HYPER_EXPANSION"

        sorted_hist = sorted(history)
        # Empirical percentile rank
        rank = sum(1 for x in sorted_hist if x <= current_atr)
        quantile = rank / len(sorted_hist)

        if quantile < 0.25:
            regime = "COMPRESSION"
        elif quantile <= 0.70:
            regime = "NORMAL"
        elif quantile <= 0.90:
            regime = "EXPANSION"
        else:
            regime = "HYPER_EXPANSION"

        return quantile, regime

    def calculate_initial_stop_bracket(
        self,
        symbol: str,
        side: str,
        entry_price: float,
        atr: float,
        strategy: str = "VEB"
    ) -> Dict[str, Any]:
        """
        Calculates an institutional volatility-clustered initial stop loss.
        Replaces arbitrary static multiples with quantile-calibrated stops.
        """
        quantile, regime = self.calculate_volatility_quantile(symbol, atr, entry_price)

        is_veb = ("volatility" in strategy.lower()) or ("breakout" in strategy.lower())
        
        # In compression, breakout stops must be placed outside the coiling boundary (1.65 - 1.85x ATR)
        # In hyper-expansion, stop must not be excessively wide to cap dollar loss (1.35x ATR)
        if regime == "COMPRESSION":
            mult = 1.75 if is_veb else 1.60
        elif regime == "NORMAL":
            mult = 1.50
        elif regime == "EXPANSION":
            mult = 1.45 if is_veb else 1.50
        else: # HYPER_EXPANSION
            mult = 1.35

        stop_dist = max(atr * mult, entry_price * 0.006)
        side_upper = side.upper()

        if side_upper == "BUY":
            stop_loss = _round_price(entry_price - stop_dist)
            tp1 = _round_price(entry_price + (stop_dist * 1.5))
            tp2 = _round_price(entry_price + (stop_dist * 2.5))
        else:
            stop_loss = _round_price(entry_price + stop_dist)
            tp1 = _round_price(entry_price - (stop_dist * 1.5))
            tp2 = _round_price(entry_price - (stop_dist * 2.5))

        return {
            "stop_loss": stop_loss,
            "take_profit_1": tp1,
            "take_profit_2": tp2,
            "stop_distance": stop_dist,
            "multiplier": mult,
            "volatility_quantile": round(quantile, 3),
            "volatility_regime": regime
        }

    def evaluate_trailing_update(
        self,
        position: Dict[str, Any],
        current_price: float,
        live_rvol: float = 1.0,
        live_rsi: float = 50.0,
        orderbook_obi: float = 0.0
    ) -> Dict[str, Any]:
        """
        Continuously evaluates position progress and calculates optimal Chandelier ratchet.
        Returns whether an update should be committed and the detailed analytics.
        """
        symbol = position.get("symbol", "UNKNOWN")
        side = position.get("side", "BUY").upper()
        entry = float(position.get("entry_price", current_price))
        atr = float(position.get("atr", entry * 0.015))
        current_sl = float(position.get("trailing_stop", position.get("stop_loss", 0.0)))
        opened_at = float(position.get("opened_at", time.time()))
        hold_seconds = max(1.0, time.time() - opened_at)
        strategy = position.get("strategy", position.get("entry_strategy", ""))

        if entry <= 0.0 or atr <= 0.0:
            return {"should_update": False, "reason": "Invalid entry or ATR"}

        # 1. Update High-Water Mark (Peak price for BUY, Trough price for SELL)
        highest_p = float(position.get("highest_price", entry))
        lowest_p = float(position.get("lowest_price", entry))
        if current_price > highest_p:
            highest_p = current_price
            position["highest_price"] = highest_p
        if lowest_p == 0.0 or current_price < lowest_p:
            lowest_p = current_price
            position["lowest_price"] = lowest_p

        # 2. Risk metric calculations (R-multiple)
        initial_sl = float(position.get("stop_loss", current_sl))
        risk_dist = abs(entry - initial_sl) if abs(entry - initial_sl) > 0 else (atr * 1.5)
        
        favorable_gain = (current_price - entry) if side == "BUY" else (entry - current_price)
        peak_gain = (highest_p - entry) if side == "BUY" else (entry - lowest_p)
        
        current_r = favorable_gain / risk_dist if risk_dist > 0 else 0.0
        peak_r = peak_gain / risk_dist if risk_dist > 0 else 0.0

        # 3. Volatility Quantile & Regime Calibration
        quantile, regime = self.calculate_volatility_quantile(symbol, atr, current_price)

        # 4. Multi-Regime Base Multiplier
        # In expansion, give trend room initially (1.2x), but tighten down to 0.85x once > 2R
        if regime == "EXPANSION":
            base_mult = 1.15 if peak_r < 1.8 else (0.95 if peak_r < 3.0 else 0.75)
        elif regime == "HYPER_EXPANSION":
            # Parabolic move: aggressively lock gains to prevent giving back vertical spike
            base_mult = 0.80 if peak_r < 2.0 else 0.60
        elif regime == "COMPRESSION":
            base_mult = 1.25
        else: # NORMAL
            base_mult = 1.10 if peak_r < 1.8 else 0.90

        # 5. Anti-Shakeout Elastic Factor (First 180 seconds or before 0.75R)
        # Prevents initial retest noise and wide spread wicks from prematurely triggering exit
        elastic_factor = 1.0
        if hold_seconds < 180 and peak_r < 0.80:
            elastic_factor = 1.15

        # 6. Momentum & Order Flow Decay Factor
        # Accelerates stop closer when volume dries up or order book exhibits heavy resistance
        decay_factor = 1.0
        decay_reasons = []

        # A. RVOL Exhaustion: If price is at or near peak (peak_r >= 1.5R) but volume dropped sharply
        if peak_r >= 1.5 and live_rvol < 0.80:
            decay_factor *= 0.85
            decay_reasons.append(f"RVOL exhausted ({live_rvol:.2f}x)")

        # B. RSI Divergence / Overbought Exhaustion
        if side == "BUY" and live_rsi >= 76.0 and current_price < highest_p:
            decay_factor *= 0.88
            decay_reasons.append(f"RSI extreme ({live_rsi:.1f})")
        elif side == "SELL" and live_rsi <= 24.0 and current_price > lowest_p:
            decay_factor *= 0.88
            decay_reasons.append(f"RSI extreme ({live_rsi:.1f})")

        # C. L2 Order Book Influx Resistance (hostile wall pressing against trade)
        if side == "BUY" and orderbook_obi < -0.40 and peak_r >= 1.2:
            decay_factor *= 0.88
            decay_reasons.append(f"Hostile ask wall (OBI {orderbook_obi:.2f})")
        elif side == "SELL" and orderbook_obi > 0.40 and peak_r >= 1.2:
            decay_factor *= 0.88
            decay_reasons.append(f"Hostile bid wall (OBI {orderbook_obi:.2f})")

        # 7. Final Effective Chandelier Trailing Distance
        effective_mult = base_mult * elastic_factor * decay_factor
        effective_dist = max(atr * effective_mult, entry * 0.0035)

        # 8. Chandelier Level Computation
        if side == "BUY":
            raw_target_sl = _round_price(highest_p - effective_dist)
        else:
            raw_target_sl = _round_price(lowest_p + effective_dist)

        # 9. Guaranteed Profit-Floor Ratchet (Locks minimum R-multiples)
        floor_sl = current_sl
        tier_label = "None"
        
        if peak_r >= 4.0:
            # Lock at least 75% of peak gains or +3.0R
            tier_label = "Tier 4 (>= +4.0R Runner Lock)"
            min_lock_gain = peak_gain * 0.78
            floor_sl = _round_price(entry + min_lock_gain if side == "BUY" else entry - min_lock_gain)
        elif peak_r >= 2.5:
            # Lock at least 65% of peak gains or +1.7R
            tier_label = "Tier 3 (>= +2.5R Trajectory Lock)"
            min_lock_gain = peak_gain * 0.65
            floor_sl = _round_price(entry + min_lock_gain if side == "BUY" else entry - min_lock_gain)
        elif peak_r >= 1.5:
            # Lock at least +0.75R
            tier_label = "Tier 2 (>= +1.5R Half-Runner Lock)"
            locked_amount = risk_dist * 0.75
            floor_sl = _round_price(entry + locked_amount if side == "BUY" else entry - locked_amount)
        elif peak_r >= 0.50:
            # Lock Breakeven + fee buffer (+0.25% net profit) as soon as trade reaches +0.50R (+4% to +6% ROI)
            tier_label = "Tier 1 (Breakeven + Fees)"
            fee_buffer = entry * 0.0025
            floor_sl = _round_price(entry + fee_buffer if side == "BUY" else entry - fee_buffer)

        # 10. Kristjan Qullamaggie 10/20 EMA Dynamic Baseline Trailing Layer
        # Once a runner clears >= +1.8R, trail the dynamic 10 EMA (or 20 EMA) baseline with a 0.20x ATR buffer
        q_ema_active = False
        ema_fast = float(position.get("ema10", position.get("ema9", 0.0)))
        ema_slow = float(position.get("ema20", position.get("ema21", 0.0)))

        # Fallback to PriceCache TA if not cached in position dictionary
        if ema_fast <= 0.0:
            try:
                from app.workers.price_cache import PriceCache
                cache_ta = PriceCache.get_instance_sync().get_technical_analysis(symbol)
                ema_fast = float(cache_ta.get("ema9", cache_ta.get("ema10", 0.0)))
                ema_slow = float(cache_ta.get("ema21", cache_ta.get("ema20", 0.0)))
            except Exception:
                pass

        if peak_r >= 1.80 and ema_fast > 0.0:
            if side == "BUY":
                # For BUY, trail 0.20x ATR beneath the 10 EMA baseline (or 20 EMA if wide swing)
                q_target = _round_price(ema_fast - (atr * 0.20))
                if q_target > floor_sl and q_target < current_price:
                    floor_sl = q_target
                    tier_label = f"Qullamaggie 10 EMA Trail (${ema_fast:.4f})"
                    q_ema_active = True
            else:
                # For SELL, trail 0.20x ATR above the 10 EMA baseline
                q_target = _round_price(ema_fast + (atr * 0.20))
                if (floor_sl == 0.0 or q_target < floor_sl) and q_target > current_price:
                    floor_sl = q_target
                    tier_label = f"Qullamaggie 10 EMA Trail (${ema_fast:.4f})"
                    q_ema_active = True

        # Merge Chandelier stop with profit-floor
        if side == "BUY":
            candidate_sl = max(raw_target_sl, floor_sl)
            # Guarantee non-retrograde and below current market price
            candidate_sl = max(candidate_sl, current_sl)
            if candidate_sl >= current_price:
                # Keep safe buffer beneath market price
                candidate_sl = _round_price(current_price - (atr * 0.25))
            should_update = candidate_sl > (current_sl + (entry * 0.0008))
        else:
            candidate_sl = min(raw_target_sl, floor_sl) if floor_sl > 0 else raw_target_sl
            if current_sl > 0:
                candidate_sl = min(candidate_sl, current_sl)
            if candidate_sl <= current_price:
                candidate_sl = _round_price(current_price + (atr * 0.25))
            should_update = (candidate_sl < (current_sl - (entry * 0.0008))) if current_sl > 0 else True

        locked_r = (candidate_sl - entry) / risk_dist if side == "BUY" else (entry - candidate_sl) / risk_dist

        reason = (
            f"Chandelier Ratchet: Regime={regime} (Q={quantile:.2f}, Mult={effective_mult:.2f}x), "
            f"Peak={peak_r:.2f}R, Current={current_r:.2f}R, Floor={tier_label}"
        )
        if decay_reasons:
            reason += f" [Accelerators: {', '.join(decay_reasons)}]"

        return {
            "should_update": should_update,
            "new_stop": candidate_sl,
            "old_stop": current_sl,
            "locked_r": round(locked_r, 2),
            "peak_r": round(peak_r, 2),
            "current_r": round(current_r, 2),
            "volatility_regime": regime,
            "volatility_quantile": round(quantile, 3),
            "effective_multiplier": round(effective_mult, 2),
            "tier_label": tier_label,
            "reason": reason
        }
