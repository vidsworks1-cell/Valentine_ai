import logging
import random
from typing import Dict, Any

logger = logging.getLogger("lumina.paper_trading.simulator")

class PaperSimulator:
    """Simulates realistic filled prices, slippage, and fees for paper trades."""
    
    @staticmethod
    def simulate_execution(order: Dict[str, Any]) -> Dict[str, Any]:
        """Adds standard slippage and exchange fee parameters to a trade simulation."""
        logger.info(f"Simulating market execution for order: {order}")
        
        entry_price = order.get("entry_price", 100.0)
        side = order.get("side", "BUY")
        
        # Add random simulated slippage (0.01% - 0.05%)
        slippage_pct = random.uniform(0.0001, 0.0005)
        slippage_direction = 1 if side == "BUY" else -1
        fill_price = entry_price * (1 + (slippage_pct * slippage_direction))
        
        # Standard fee rate (0.1%)
        fees = (fill_price * order.get("position_size", 1.0)) * 0.001
        
        return {
            "symbol": order.get("symbol", "BTCUSDT"),
            "side": side,
            "ordered_price": entry_price,
            "fill_price": fill_price,
            "fees": fees,
            "slippage": abs(fill_price - entry_price)
        }
