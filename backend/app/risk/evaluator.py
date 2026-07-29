import logging
import math
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.risk.evaluator")

class RiskEvaluator:
    """Evaluates proposed orders against predefined strategy risk profiles."""
    
    @staticmethod
    def calculate_atr_position_size(
        balance: float,
        entry_price: float,
        atr: float,
        max_risk_pct: float = 0.02,
        stop_loss: Optional[float] = None
    ) -> float:
        """Dynamically calculates position size based on actual stop loss distance and volatility scaling.
        - Translates ATR / Stop Loss to an exact risk per unit.
        - Reduces the risk exposure percentage if relative volatility is excessively high.
        - Calculates the exact units to purchase based on this adjusted risk framework.
        """
        if entry_price <= 0:
            return 1.0

        # 1. Calculate exact Stop Distance
        if stop_loss is not None and stop_loss > 0 and abs(entry_price - stop_loss) > 0:
            stop_distance = abs(entry_price - stop_loss)
        elif atr > 0:
            stop_distance = atr * 1.5
        else:
            stop_distance = entry_price * 0.01

        # 2. Relative volatility calculation & dynamic risk percentage scaling
        relative_volatility = (atr / entry_price) if (entry_price > 0 and atr > 0) else 0.0
        adjusted_risk_pct = max_risk_pct
        if relative_volatility > 0.015:
            scaling_factor = 0.015 / relative_volatility
            adjusted_risk_pct = max_risk_pct * scaling_factor
            # Portfolio-level constraint (max_risk_pct) is the final ceiling authority.
            # Volatility scaling reduces risk further and never raises risk above max_risk_pct.
            min_floor = min(max_risk_pct, 0.001)
            adjusted_risk_pct = max(min(adjusted_risk_pct, max_risk_pct), min_floor)
            logger.info(f"⚡ High volatility ({relative_volatility:.4f}). Scaling risk pct from {max_risk_pct*100:.2f}% to {adjusted_risk_pct*100:.2f}% (Portfolio Ceiling: {max_risk_pct*100:.2f}%)")

        allowed_risk_amt = balance * adjusted_risk_pct

        # Round down (floor) to 4 decimal places to prevent floating-point rounding up from exceeding max risk
        position_size = math.floor((allowed_risk_amt / stop_distance) * 10000) / 10000.0

        logger.info(
            f"📊 ATR Position Sizing: Entry=${entry_price:.4f}, SL=${stop_loss if stop_loss else 0.0:.4f}, "
            f"StopDistance=${stop_distance:.4f}, ATR=${atr:.4f}, Risk%={adjusted_risk_pct*100:.2f}%, "
            f"AllowedRisk=${allowed_risk_amt:.2f}, Size={position_size} units"
        )
        return max(position_size, 0.0001)

    @staticmethod
    def validate_order(order: Dict[str, Any], balance: float, max_risk_pct: float = 0.02) -> bool:
        """Determines if the proposed trade complies with safety boundaries."""
        logger.info(f"Evaluating order risk: {order} against balance: ${balance:.2f}")

        entry_price = order.get("entry_price", 0.0)
        stop_loss = order.get("stop_loss", 0.0)
        size = order.get("position_size", 0.0)
        atr = order.get("atr", 0.0)

        if entry_price <= 0 or size <= 0:
            logger.warning(f"Order validation failed: invalid entry price ({entry_price}) or position size ({size})")
            return False

        # 1. Determine stop distance identically to calculate_atr_position_size
        if stop_loss > 0 and abs(entry_price - stop_loss) > 0:
            stop_distance = abs(entry_price - stop_loss)
        elif atr > 0:
            stop_distance = atr * 1.5
        else:
            stop_distance = entry_price * 0.01

        # 2. Relative volatility calculation & dynamic risk percentage scaling
        relative_volatility = (atr / entry_price) if (entry_price > 0 and atr > 0) else 0.0
        adjusted_risk_pct = max_risk_pct
        if relative_volatility > 0.015:
            scaling_factor = 0.015 / relative_volatility
            adjusted_risk_pct = max_risk_pct * scaling_factor
            min_floor = min(max_risk_pct, 0.001)
            adjusted_risk_pct = max(min(adjusted_risk_pct, max_risk_pct), min_floor)

        max_allowed_risk = round(balance * adjusted_risk_pct, 4)
        actual_risk = round(stop_distance * size, 4)
        diff = round(actual_risk - max_allowed_risk, 4)

        logger.info(
            f"🔍 [RISK EVALUATION DEBUG]\n"
            f"  - Entry Price: ${entry_price:.4f}\n"
            f"  - Stop Loss: ${stop_loss:.4f}\n"
            f"  - ATR: ${atr:.4f}\n"
            f"  - Stop Distance: ${stop_distance:.4f}\n"
            f"  - Allowed Risk ({adjusted_risk_pct*100:.2f}%): ${max_allowed_risk:.2f}\n"
            f"  - Calculated Position Size: {size:.4f} units\n"
            f"  - Actual Risk Evaluated: ${actual_risk:.2f}\n"
            f"  - Max Allowed Risk: ${max_allowed_risk:.2f}\n"
            f"  - Difference: ${diff:.2f}"
        )

        if actual_risk > max_allowed_risk:
            excess = actual_risk - max_allowed_risk
            tolerance = max(1.0, max_allowed_risk * 0.001)
            if excess <= tolerance:
                clamped_size = math.floor((max_allowed_risk / stop_distance) * 10000) / 10000.0
                logger.info(f"Clamping position size from {size} to {clamped_size} to satisfy risk constraint (excess was {excess:.6f} <= {tolerance:.6f})")
                order["position_size"] = clamped_size
                actual_risk = round(stop_distance * clamped_size, 4)

            if actual_risk > max_allowed_risk:
                logger.warning(f"Order exceeds max risk allocation: Actual Risk ${actual_risk:.2f} > Allowed Risk ${max_allowed_risk:.2f} (Diff: ${diff:.2f})")
                return False

        return True
