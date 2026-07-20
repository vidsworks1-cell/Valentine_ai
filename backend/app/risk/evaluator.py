import logging
from typing import Dict, Any

logger = logging.getLogger("lumina.risk.evaluator")

class RiskEvaluator:
    """Evaluates proposed orders against predefined strategy risk profiles."""
    
    @staticmethod
    def calculate_atr_position_size(balance: float, entry_price: float, atr: float, max_risk_pct: float = 0.02) -> float:
        """Dynamically calculates position size based on current ATR and volatility scaling.
        - Translates ATR to a dynamic volatility risk factor.
        - Reduces the risk exposure percentage if volatility is excessively high.
        - Calculates the exact units to purchase based on this adjusted risk framework.
        """
        if entry_price <= 0 or atr <= 0:
            return 1.0
            
        # 1. Relative volatility calculation
        relative_volatility = atr / entry_price
        
        # 2. Dynamic risk percentage scaling
        # Standard threshold is 1.5% relative volatility.
        # If volatility is high, we scale down the allowed risk percentage.
        adjusted_risk_pct = max_risk_pct
        if relative_volatility > 0.015:
            scaling_factor = 0.015 / relative_volatility
            adjusted_risk_pct = max_risk_pct * scaling_factor
            adjusted_risk_pct = max(adjusted_risk_pct, 0.005) # Keep a floor of 0.5% risk
            logger.info(f"⚡ Volatility high ({relative_volatility:.4f}). Scaling risk pct from {max_risk_pct*100:.1f}% to {adjusted_risk_pct*100:.2f}%")
            
        # 3. Dynamic unit size based on ATR stop-loss distance (e.g. 1.5 * ATR buffer)
        risk_per_unit = atr * 1.5
        allowed_risk_amt = balance * adjusted_risk_pct
        position_size = round(allowed_risk_amt / risk_per_unit, 4)
        
        logger.info(f"📊 ATR Position Sizing: Entry={entry_price}, ATR={atr}, Risk%={adjusted_risk_pct*100:.2f}%, AllowedRisk=${allowed_risk_amt:.2f}, Size={position_size}")
        return max(position_size, 0.0001)

    @staticmethod
    def validate_order(order: Dict[str, Any], balance: float, max_risk_pct: float = 0.02) -> bool:
        """Determines if the proposed trade complies with safety boundaries."""
        logger.info(f"Evaluating order risk: {order} against balance: {balance}")
        
        entry_price = order.get("entry_price", 0.0)
        stop_loss = order.get("stop_loss", 0.0)
        size = order.get("position_size", 0.0)
        
        if entry_price <= 0 or size <= 0:
            return False
            
        # Calculation of risk on SL trigger
        if stop_loss > 0 and entry_price != stop_loss:
            risk_per_unit = abs(entry_price - stop_loss)
            total_risk_amt = risk_per_unit * size
            max_allowed_risk = balance * max_risk_pct
            
            if total_risk_amt > max_allowed_risk:
                logger.warning(f"Order exceeds max risk allocation: {total_risk_amt} > {max_allowed_risk}")
                return False
                
        return True
