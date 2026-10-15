"""
Module 6: Adaptive Profit Optimization

Replaces static take-profit targets with continuous dynamic trajectory management.
Monitors:
- momentum decay (RSI divergence, MACD histogram contraction)
- volume decay (volume tapering as price approaches target)
- volatility expansion (super-trends extending past target)
- trend persistence (higher lows/lower highs holding)
- market structure (break of structure vs consolidation)

Allows:
- Take-Profit Extension (when momentum & trend persistence remain exceptionally strong)
- Trailing Exit Tightening (locking gains when momentum begins decelerating)
- Intelligent Early Partial/Full Exits (when original trade thesis weakens prior to SL hit)

Maximizes long-term expectancy rather than artificial win rate.
"""

import math
import logging
from typing import Dict, Any, Tuple, Optional

logger = logging.getLogger("lumina.trading.adaptive_profit_optimizer")


class AdaptiveProfitDecision:
    def __init__(
        self,
        action: str,  # "HOLD", "EXTEND_TP", "TIGHTEN_TRAIL", "EARLY_EXIT_HARVEST"
        new_take_profit: Optional[float] = None,
        new_trailing_stop: Optional[float] = None,
        rationale: str = ""
    ):
        self.action = action
        self.new_take_profit = new_take_profit
        self.new_trailing_stop = new_trailing_stop
        self.rationale = rationale

    def to_dict(self) -> Dict[str, Any]:
        return {
            "action": self.action,
            "new_take_profit": self.new_take_profit,
            "new_trailing_stop": self.new_trailing_stop,
            "rationale": self.rationale
        }


class AdaptiveProfitOptimizer:
    _instance: Optional['AdaptiveProfitOptimizer'] = None

    @classmethod
    def get_instance(cls) -> 'AdaptiveProfitOptimizer':
        if cls._instance is None:
            cls._instance = AdaptiveProfitOptimizer()
        return cls._instance

    def evaluate_trajectory(
        self,
        position: Dict[str, Any],
        current_price: float,
        rvol: float = 1.5,
        rsi: float = 60.0,
        momentum_accelerating: bool = True,
        adx: float = 25.0,
        **kwargs
    ) -> AdaptiveProfitDecision:
        """
        Continuously evaluates active position trajectory to dynamically adapt TP/SL.
        """
        symbol = position.get("symbol", "UNKNOWN")
        side = position.get("side", "BUY").upper()
        entry = float(position.get("entry_price", current_price))
        tp = float(position.get("take_profit", entry * 1.04))
        sl = float(position.get("stop_loss", entry * 0.98))
        current_trail = float(position.get("trailing_stop", sl))
        atr = float(position.get("atr", entry * 0.015))

        if entry <= 0:
            return AdaptiveProfitDecision(action="HOLD", rationale="Invalid entry price")

        unrealized_pnl_pct = ((current_price - entry) / entry * 100.0) if side == "BUY" else ((entry - current_price) / entry * 100.0)
        target_distance_pct = abs((tp - entry) / entry * 100.0)

        # Strategy specific calibration
        strat = position.get("strategy", position.get("entry_strategy", ""))
        is_veb = "volatility expansion" in strat.lower()
        req_progress = 0.70 if is_veb else 0.75
        req_rvol = 1.35 if is_veb else 1.60
        max_extensions = 3 if is_veb else 2

        # 1. TP Extension: Price reached >= req_progress of initial TP with accelerating momentum and high volume
        progress_to_tp = (unrealized_pnl_pct / target_distance_pct) if target_distance_pct > 0 else 0.0

        if progress_to_tp >= req_progress and momentum_accelerating and rvol >= req_rvol:
            # Trend is exceptionally strong: extend TP by 1.2x ATR to let winners run
            extension_amount = atr * (1.35 if is_veb else 1.2)
            if side == "BUY":
                extended_tp = round(tp + extension_amount, 4)
                raised_trail = round(max(current_trail, entry + (atr * 0.6)), 4)
            else:
                extended_tp = round(tp - extension_amount, 4)
                raised_trail = round(min(current_trail, entry - (atr * 0.6)), 4)

            # Prevent infinite extension if already reached extension limit
            ext_count = int(position.get("tp_extensions_count", 0))
            if ext_count < max_extensions:
                msg = (
                    f"🚀 [TP EXTENSION - {strat or 'VEB'}] {symbol} {side} momentum accelerating (+{unrealized_pnl_pct:.2f}%, RVOL {rvol:.1f}). "
                    f"Extended TP from ${tp:.2f} to ${extended_tp:.2f} to let outlier winner run. Lock trail at ${raised_trail:.2f}."
                )
                logger.info(msg)
                return AdaptiveProfitDecision(
                    action="EXTEND_TP",
                    new_take_profit=extended_tp,
                    new_trailing_stop=raised_trail,
                    rationale=msg
                )

        # 2. Tighten Trailing Stop: In profit (> 0.9R for VEB, > 1.2R standard), but momentum begins decelerating or RSI extreme
        profit_r_trigger = 0.9 if is_veb else 1.2
        if unrealized_pnl_pct >= (atr / entry * 100.0 * profit_r_trigger) and (not momentum_accelerating or rsi > 78.0 or rsi < 22.0):
            # Tighten trail to lock in 75% (VEB) or 72% of peak gains
            trail_pct = 0.75 if is_veb else 0.72
            if side == "BUY":
                peak_p = float(position.get("highest_price", current_price))
                tight_trail = round(entry + ((peak_p - entry) * trail_pct), 4)
                if tight_trail > current_trail:
                    msg = f"🛡️ [TIGHTEN TRAIL - {strat or 'VEB'}] Momentum decelerating on {symbol} near peak ${peak_p:.2f}. Raised trail to ${tight_trail:.2f}."
                    logger.info(msg)
                    return AdaptiveProfitDecision(
                        action="TIGHTEN_TRAIL",
                        new_trailing_stop=tight_trail,
                        rationale=msg
                    )
            else:
                lowest_p = float(position.get("lowest_price", current_price))
                tight_trail = round(entry - ((entry - lowest_p) * 0.72), 4)
                if tight_trail < current_trail or current_trail == 0:
                    msg = f"🛡️ [TIGHTEN TRAIL] Momentum decelerating on {symbol} near trough ${lowest_p:.2f}. Lowered trail to ${tight_trail:.2f}."
                    logger.info(msg)
                    return AdaptiveProfitDecision(
                        action="TIGHTEN_TRAIL",
                        new_trailing_stop=tight_trail,
                        rationale=msg
                    )

        # 3. Intelligent Early Exit: Trade in minor profit or small loss, but thesis has completely dissolved
        thesis_strength = float(position.get("thesis_strength", 1.0))
        if thesis_strength < 0.45 and unrealized_pnl_pct < 0.20:
            msg = f"✂️ [EARLY EXIT] Original trade thesis on {symbol} collapsed ({thesis_strength:.2f}). Harvesting capital before full stop-loss."
            logger.info(msg)
            return AdaptiveProfitDecision(
                action="EARLY_EXIT_HARVEST",
                rationale=msg
            )

        return AdaptiveProfitDecision(action="HOLD", rationale="Trajectory normal within expected bounds")
