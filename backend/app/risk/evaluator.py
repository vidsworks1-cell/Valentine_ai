import logging
from typing import Dict, Any

logger = logging.getLogger("lumina.risk.evaluator")

class RiskEvaluator:
    """Evaluates proposed orders against predefined strategy risk profiles."""
    
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
