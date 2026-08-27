import logging
import time
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.execution_database")

class OrderAttemptRecord:
    """Represents an immutable, structured order attempt log for learning and analytics."""
    def __init__(
        self,
        attempt_id: str,
        symbol: str,
        side: str,
        order_type: str,
        trading_mode: str,
        strategy_used: str,
        confidence_score: float,
        market_regime: str,
        requested_quantity: float,
        requested_price: float,
        stop_loss: float,
        take_profit: float,
        exchange_endpoint: str,
        http_status: int,
        is_successful: bool,
        error_code: Optional[int] = None,
        error_message: Optional[str] = None,
        failure_category: Optional[str] = None,
        diagnostic_advice: Optional[str] = None,
        exchange_order_id: Optional[str] = None,
        executed_qty: float = 0.0,
        executed_price: float = 0.0,
        latency_ms: float = 0.0,
        raw_request_payload: Optional[Dict[str, Any]] = None,
        raw_response_payload: Optional[Dict[str, Any]] = None
    ):
        self.attempt_id = attempt_id
        self.symbol = symbol.upper()
        self.side = side.upper()
        self.order_type = order_type.upper()
        self.trading_mode = trading_mode.upper()
        self.strategy_used = strategy_used
        self.confidence_score = float(confidence_score)
        self.market_regime = market_regime.upper()
        self.requested_quantity = float(requested_quantity)
        self.requested_price = float(requested_price)
        self.stop_loss = float(stop_loss)
        self.take_profit = float(take_profit)
        self.exchange_endpoint = exchange_endpoint
        self.http_status = int(http_status)
        self.is_successful = bool(is_successful)
        self.error_code = error_code
        self.error_message = error_message
        self.failure_category = failure_category
        self.diagnostic_advice = diagnostic_advice
        self.exchange_order_id = exchange_order_id
        self.executed_qty = float(executed_qty)
        self.executed_price = float(executed_price)
        self.latency_ms = float(latency_ms)
        self.raw_request_payload = raw_request_payload or {}
        self.raw_response_payload = raw_response_payload or {}
        self.timestamp = datetime.now(timezone.utc).isoformat()
        self.created_at = time.time()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "attempt_id": self.attempt_id,
            "symbol": self.symbol,
            "side": self.side,
            "order_type": self.order_type,
            "trading_mode": self.trading_mode,
            "strategy_used": self.strategy_used,
            "confidence_score": self.confidence_score,
            "market_regime": self.market_regime,
            "requested_quantity": self.requested_quantity,
            "requested_price": self.requested_price,
            "stop_loss": self.stop_loss,
            "take_profit": self.take_profit,
            "exchange_endpoint": self.exchange_endpoint,
            "http_status": self.http_status,
            "is_successful": self.is_successful,
            "error_code": self.error_code,
            "error_message": self.error_message,
            "failure_category": self.failure_category,
            "diagnostic_advice": self.diagnostic_advice,
            "exchange_order_id": self.exchange_order_id,
            "executed_qty": self.executed_qty,
            "executed_price": self.executed_price,
            "latency_ms": self.latency_ms,
            "raw_request_payload": self.raw_request_payload,
            "raw_response_payload": self.raw_response_payload,
            "timestamp": self.timestamp
        }


class ExecutionOutcomeDatabase:
    """
    Unified Execution Outcome Database for Lumina.
    
    Transforms every single order attempt—whether successful, partially filled, or rejected by Binance—
    into structured training and analytics data:
    
    Signal -> Order Attempt -> Exchange Response -> Execution Result -> Learning DB
    
    Answers quantitative questions:
    - How many trades failed because of MIN_NOTIONAL or LOT_SIZE precision?
    - How much profit was missed due to overly strict confidence thresholds?
    - What is the empirical error distribution across symbols and modes?
    """
    _instance: Optional['ExecutionOutcomeDatabase'] = None

    def __init__(self):
        self._attempts: List[OrderAttemptRecord] = []
        self.MAX_RECORDS = 1000

    @classmethod
    def get_instance(cls) -> 'ExecutionOutcomeDatabase':
        if cls._instance is None:
            cls._instance = ExecutionOutcomeDatabase()
        return cls._instance

    @staticmethod
    def classify_binance_failure(code: Optional[int], msg: Optional[str]) -> str:
        """Classifies Binance exchange error codes into standardized error taxonomies."""
        if code is None:
            return "UNKNOWN_FAILURE"
        if code == -2010:
            return "INSUFFICIENT_BALANCE"
        elif code in [-1013, -1111]:
            if "NOTIONAL" in str(msg).upper():
                return "MIN_NOTIONAL_BREACH"
            return "LOT_SIZE_PRECISION_ERROR"
        elif code == -1021:
            return "TIMESTAMP_DRIFT"
        elif code == -1003:
            return "RATE_LIMIT_EXCEEDED"
        elif code in [-2014, -2015]:
            return "AUTH_PERMISSION_ERROR"
        elif code == -2011:
            return "UNKNOWN_ORDER_ID"
        elif code == -1100:
            return "ILLEGAL_PARAMETER"
        return "EXCHANGE_ERROR"

    def record_attempt(
        self,
        symbol: str,
        side: str,
        order_type: str,
        trading_mode: str,
        strategy_used: str,
        confidence_score: float,
        market_regime: str,
        requested_quantity: float,
        requested_price: float,
        stop_loss: float,
        take_profit: float,
        exchange_endpoint: str,
        http_status: int,
        is_successful: bool,
        error_code: Optional[int] = None,
        error_message: Optional[str] = None,
        diagnostic_advice: Optional[str] = None,
        exchange_order_id: Optional[str] = None,
        executed_qty: float = 0.0,
        executed_price: float = 0.0,
        latency_ms: float = 0.0,
        raw_request_payload: Optional[Dict[str, Any]] = None,
        raw_response_payload: Optional[Dict[str, Any]] = None
    ) -> OrderAttemptRecord:
        """Records an order attempt with full request/response telemetry."""
        attempt_id = f"ATTEMPT_{symbol.upper()}_{int(time.time() * 1000)}"
        failure_cat = self.classify_binance_failure(error_code, error_message) if not is_successful else None

        record = OrderAttemptRecord(
            attempt_id=attempt_id,
            symbol=symbol,
            side=side,
            order_type=order_type,
            trading_mode=trading_mode,
            strategy_used=strategy_used,
            confidence_score=confidence_score,
            market_regime=market_regime,
            requested_quantity=requested_quantity,
            requested_price=requested_price,
            stop_loss=stop_loss,
            take_profit=take_profit,
            exchange_endpoint=exchange_endpoint,
            http_status=http_status,
            is_successful=is_successful,
            error_code=error_code,
            error_message=error_message,
            failure_category=failure_cat,
            diagnostic_advice=diagnostic_advice,
            exchange_order_id=exchange_order_id,
            executed_qty=executed_qty,
            executed_price=executed_price,
            latency_ms=latency_ms,
            raw_request_payload=raw_request_payload,
            raw_response_payload=raw_response_payload
        )

        self._attempts.append(record)
        if len(self._attempts) > self.MAX_RECORDS:
            self._attempts.pop(0)

        status_tag = "✅ FILLED" if is_successful else f"❌ REJECTED ({failure_cat}: Code {error_code})"
        logger.info(
            f"📥 [EXECUTION DATABASE RECORDED] {attempt_id} | {symbol} {side} | Mode: {trading_mode} | {status_tag} | Latency: {latency_ms:.1f}ms"
        )
        return record

    def get_execution_analytics_summary(self) -> Dict[str, Any]:
        """Calculates global execution failure taxonomy, success rates, and root-cause breakdown."""
        total_attempts = len(self._attempts)
        if total_attempts == 0:
            return {
                "total_order_attempts": 0,
                "successful_orders": 0,
                "failed_orders": 0,
                "execution_success_rate_pct": 100.0,
                "failure_taxonomy_breakdown": {},
                "mode_breakdown": {},
                "symbol_breakdown": {},
                "recent_attempts": []
            }

        successful = [a for a in self._attempts if a.is_successful]
        failed = [a for a in self._attempts if not a.is_successful]
        success_rate = round((len(successful) / total_attempts) * 100.0, 1)

        # Categorical Taxonomy
        tax_counts: Dict[str, int] = {}
        for f in failed:
            cat = f.failure_category or "UNKNOWN"
            tax_counts[cat] = tax_counts.get(cat, 0) + 1

        # Mode Breakdown
        mode_counts: Dict[str, Dict[str, int]] = {}
        for a in self._attempts:
            m = a.trading_mode
            if m not in mode_counts:
                mode_counts[m] = {"total": 0, "successful": 0, "failed": 0}
            mode_counts[m]["total"] += 1
            if a.is_successful:
                mode_counts[m]["successful"] += 1
            else:
                mode_counts[m]["failed"] += 1

        # Symbol Breakdown
        sym_counts: Dict[str, Dict[str, int]] = {}
        for a in self._attempts:
            s = a.symbol
            if s not in sym_counts:
                sym_counts[s] = {"total": 0, "successful": 0, "failed": 0}
            sym_counts[s]["total"] += 1
            if a.is_successful:
                sym_counts[s]["successful"] += 1
            else:
                sym_counts[s]["failed"] += 1

        return {
            "total_order_attempts": total_attempts,
            "successful_orders": len(successful),
            "failed_orders": len(failed),
            "execution_success_rate_pct": success_rate,
            "failure_taxonomy_breakdown": tax_counts,
            "mode_breakdown": mode_counts,
            "symbol_breakdown": sym_counts,
            "recent_attempts": [a.to_dict() for a in self._attempts[-25:]]
        }
