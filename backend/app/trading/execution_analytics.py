import logging
import time
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.execution_analytics")

class ExecutionAnalyticsEngine:
    """
    Performance Analytics & Execution Efficiency Engine for Lumina.
    
    Tracks and analyzes order execution metrics across symbols and strategies:
    - Order Latency: Signal -> Submit -> Fill round-trip latency in milliseconds.
    - Slippage: Ordered vs Fill price variance (% and absolute value).
    - Fill Rate & Missed Fills: Filled vs rejected/timed-out orders.
    - Execution Quality Index: Composite score (0-100) combining latency, slippage, and fill efficiency.
    """
    _instance: Optional['ExecutionAnalyticsEngine'] = None

    def __init__(self):
        self._execution_logs: List[Dict[str, Any]] = []
        self._missed_fills: List[Dict[str, Any]] = []
        self.MAX_LOGS = 500

    @classmethod
    def get_instance(cls) -> 'ExecutionAnalyticsEngine':
        if cls._instance is None:
            cls._instance = ExecutionAnalyticsEngine()
        return cls._instance

    def record_execution(
        self,
        symbol: str,
        side: str,
        strategy: str,
        ordered_price: float,
        fill_price: float,
        position_size: float,
        signal_timestamp_ms: float,
        fill_timestamp_ms: float,
        fees: float = 0.0,
        status: str = "FILLED"
    ) -> Dict[str, Any]:
        """
        Records a completed execution event with detailed latency and slippage metrics.
        """
        now_ms = time.time() * 1000.0
        if fill_timestamp_ms <= 0:
            fill_timestamp_ms = now_ms
            
        latency_ms = round(max(1.0, fill_timestamp_ms - signal_timestamp_ms), 2)
        
        # Calculate slippage
        slippage_abs = round(abs(fill_price - ordered_price), 4)
        slippage_pct = round((slippage_abs / ordered_price) * 100.0, 4) if ordered_price > 0 else 0.0
        
        # Adverse slippage calculation (+ if worse than ordered, - if better)
        if side.upper() == "BUY":
            is_adverse = fill_price > ordered_price
        else:
            is_adverse = fill_price < ordered_price

        # Compute execution quality score (0 - 100)
        # Latency score: 100 if <50ms, decreases as latency rises
        latency_score = max(0.0, 100.0 - (latency_ms / 10.0))
        # Slippage score: 100 if <0.02%, decreases as slippage rises
        slippage_score = max(0.0, 100.0 - (slippage_pct * 1000.0))
        quality_score = round((latency_score * 0.4) + (slippage_score * 0.6), 1)

        log_entry = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "strategy": strategy,
            "ordered_price": round(ordered_price, 4),
            "fill_price": round(fill_price, 4),
            "position_size": position_size,
            "slippage_abs": slippage_abs,
            "slippage_pct": slippage_pct,
            "is_adverse_slippage": is_adverse,
            "latency_ms": latency_ms,
            "fees": round(fees, 4),
            "status": status,
            "quality_score": quality_score,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }

        self._execution_logs.append(log_entry)
        if len(self._execution_logs) > self.MAX_LOGS:
            self._execution_logs.pop(0)

        logger.info(
            f"⚡ [EXECUTION TELEMETRY] {symbol} {side} ({strategy}): "
            f"Latency: {latency_ms}ms | Slippage: {slippage_pct:.4f}% | Quality: {quality_score}/100"
        )
        return log_entry

    def record_missed_fill(self, symbol: str, side: str, strategy: str, reason: str, price: float = 0.0):
        """Records a missed or rejected fill event for efficiency analysis."""
        entry = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "strategy": strategy,
            "reason": reason,
            "price": price,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        self._missed_fills.append(entry)
        if len(self._missed_fills) > self.MAX_LOGS:
            self._missed_fills.pop(0)
        logger.warning(f"🚫 [MISSED FILL RECORDED] {symbol} {side} via {strategy}: {reason}")

    def get_execution_metrics(self) -> Dict[str, Any]:
        """
        Calculates aggregate execution efficiency and performance analytics.
        """
        total_execs = len(self._execution_logs)
        total_missed = len(self._missed_fills)
        total_orders = total_execs + total_missed
        fill_rate_pct = round((total_execs / total_orders) * 100.0, 2) if total_orders > 0 else 100.0

        if total_execs == 0:
            return {
                "total_executed_orders": 0,
                "total_missed_fills": total_missed,
                "fill_rate_pct": fill_rate_pct,
                "latency_metrics": {"avg_ms": 0.0, "min_ms": 0.0, "max_ms": 0.0, "p95_ms": 0.0},
                "slippage_metrics": {"avg_pct": 0.0, "avg_abs": 0.0, "adverse_slippage_rate_pct": 0.0},
                "execution_quality_score": 100.0,
                "symbol_breakdown": {},
                "recent_executions": []
            }

        latencies = [e["latency_ms"] for e in self._execution_logs]
        slippage_pcts = [e["slippage_pct"] for e in self._execution_logs]
        slippage_abss = [e["slippage_abs"] for e in self._execution_logs]
        quality_scores = [e["quality_score"] for e in self._execution_logs]
        adverse_count = sum(1 for e in self._execution_logs if e["is_adverse_slippage"])

        sorted_latencies = sorted(latencies)
        p95_index = int(len(sorted_latencies) * 0.95)
        p95_latency = sorted_latencies[min(p95_index, len(sorted_latencies) - 1)]

        # Symbol breakdown
        sym_breakdown = {}
        for e in self._execution_logs:
            sym = e["symbol"]
            if sym not in sym_breakdown:
                sym_breakdown[sym] = {"count": 0, "avg_latency_ms": 0.0, "avg_slippage_pct": 0.0, "total_fees": 0.0}
            sym_breakdown[sym]["count"] += 1
            sym_breakdown[sym]["avg_latency_ms"] += e["latency_ms"]
            sym_breakdown[sym]["avg_slippage_pct"] += e["slippage_pct"]
            sym_breakdown[sym]["total_fees"] += e["fees"]

        for sym, d in sym_breakdown.items():
            cnt = d["count"]
            d["avg_latency_ms"] = round(d["avg_latency_ms"] / cnt, 2)
            d["avg_slippage_pct"] = round(d["avg_slippage_pct"] / cnt, 4)
            d["total_fees"] = round(d["total_fees"], 2)

        return {
            "total_executed_orders": total_execs,
            "total_missed_fills": total_missed,
            "fill_rate_pct": fill_rate_pct,
            "latency_metrics": {
                "avg_ms": round(sum(latencies) / len(latencies), 2),
                "min_ms": round(min(latencies), 2),
                "max_ms": round(max(latencies), 2),
                "p95_ms": round(p95_latency, 2)
            },
            "slippage_metrics": {
                "avg_pct": round(sum(slippage_pcts) / len(slippage_pcts), 4),
                "avg_abs": round(sum(slippage_abss) / len(slippage_abss), 4),
                "adverse_slippage_rate_pct": round((adverse_count / total_execs) * 100.0, 1)
            },
            "execution_quality_score": round(sum(quality_scores) / len(quality_scores), 1),
            "symbol_breakdown": sym_breakdown,
            "recent_executions": self._execution_logs[-15:]
        }
