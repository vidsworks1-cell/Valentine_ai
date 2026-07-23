import logging
import random
import time
from typing import Dict, Any

logger = logging.getLogger("lumina.paper_trading.simulator")

class PaperSimulator:
    """Simulates realistic filled prices, slippage, latency, and fees for paper trades."""
    
    @staticmethod
    def simulate_execution(order: Dict[str, Any]) -> Dict[str, Any]:
        """Adds realistic slippage, execution latency, and exchange fee parameters to a trade simulation."""
        logger.info(f"Simulating market execution for order: {order}")
        
        entry_price = order.get("entry_price", 100.0)
        side = order.get("side", "BUY")
        symbol = order.get("symbol", "BTCUSDT")
        strategy = order.get("strategy", "Breakout Momentum")
        
        # Simulated execution latency (18ms to 85ms)
        simulated_latency_ms = random.uniform(18.0, 85.0)
        now_ms = time.time() * 1000.0
        signal_time_ms = order.get("signal_timestamp_ms", now_ms - simulated_latency_ms)
        fill_time_ms = signal_time_ms + simulated_latency_ms

        # Add random simulated slippage (0.01% - 0.05%)
        slippage_pct = random.uniform(0.0001, 0.0005)
        slippage_direction = 1 if side == "BUY" else -1
        fill_price = entry_price * (1 + (slippage_pct * slippage_direction))
        
        # Standard fee rate (0.1%)
        fees = (fill_price * order.get("position_size", 1.0)) * 0.001
        
        # Record in Execution Analytics Engine
        try:
            from app.trading.execution_analytics import ExecutionAnalyticsEngine
            ExecutionAnalyticsEngine.get_instance().record_execution(
                symbol=symbol,
                side=side,
                strategy=strategy,
                ordered_price=entry_price,
                fill_price=fill_price,
                position_size=order.get("position_size", 1.0),
                signal_timestamp_ms=signal_time_ms,
                fill_timestamp_ms=fill_time_ms,
                fees=fees
            )
        except Exception as e:
            logger.error(f"Failed to record execution telemetry: {e}")

        return {
            "symbol": symbol,
            "side": side,
            "ordered_price": entry_price,
            "fill_price": fill_price,
            "fees": fees,
            "slippage": abs(fill_price - entry_price),
            "latency_ms": round(simulated_latency_ms, 2)
        }

