import logging
import time
from enum import Enum
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.execution_state")

class ExecutionState(str, Enum):
    SIGNAL_DETECTED = "SIGNAL_DETECTED"
    OPPORTUNITY_APPROVED = "OPPORTUNITY_APPROVED"
    RISK_APPROVED = "RISK_APPROVED"
    ORDER_PENDING = "ORDER_PENDING"
    ORDER_SUBMITTED = "ORDER_SUBMITTED"
    PARTIALLY_FILLED = "PARTIALLY_FILLED"
    FILLED = "FILLED"
    POSITION_REGISTERED = "POSITION_REGISTERED"
    REJECTED = "REJECTED"
    CANCELLED = "CANCELLED"


def format_user_facing_message(
    state: ExecutionState,
    symbol: str,
    side: str = "BUY",
    stage: Optional[str] = None,
    reason: Optional[str] = None,
    order_id: Optional[str] = None,
    price: Optional[float] = None,
    quantity: Optional[float] = None,
    is_live: bool = True
) -> str:
    """
    Returns the strict, user-facing synchronization message for each stage in the execution lifecycle.
    """
    sym = symbol.upper()
    s = side.upper()
    q_str = f"{quantity:.6f}" if quantity is not None and quantity > 0 else ""
    p_str = f"${price:.2f}" if price is not None and price > 0 else ""

    if state == ExecutionState.SIGNAL_DETECTED:
        return f"Signal detected for {sym} ({s}). Analyzing market structure and regime..."

    elif state == ExecutionState.OPPORTUNITY_APPROVED:
        return f"{sym} opportunity approved by strategy and Meta-Reasoning. Proceeding to risk validation."

    elif state == ExecutionState.RISK_APPROVED:
        return f"{sym} passed risk and Portfolio Guard validation. Preparing order dispatch."

    elif state == ExecutionState.ORDER_PENDING:
        target = f"{q_str} {sym}" if q_str else sym
        return f"Order pending submission for {target} ({s})."

    elif state == ExecutionState.ORDER_SUBMITTED:
        venue = "Binance" if is_live else "Paper Engine"
        return f"Order submitted, awaiting {venue} confirmation."

    elif state == ExecutionState.PARTIALLY_FILLED:
        return f"Order partially filled for {sym} on Binance (Order #{order_id or 'N/A'})."

    elif state == ExecutionState.FILLED:
        venue = "Binance" if is_live else "Paper Engine"
        ord_info = f" (Order #{order_id})" if order_id else ""
        return f"Order filled on {venue}{ord_info} at {p_str}."

    elif state == ExecutionState.POSITION_REGISTERED:
        ord_info = f", Exchange Order #{order_id}" if order_id else ""
        venue_mode = "LIVE" if is_live else "PAPER"
        return f"Trade executed: Position registered in Position Manager for {sym} ({s} {q_str} @ {p_str} [{venue_mode}]{ord_info})."

    elif state == ExecutionState.REJECTED:
        stage_name = stage.upper() if stage else "RISK VALIDATION"
        if "RISK" in stage_name:
            r_msg = f": {reason}" if reason else "."
            return f"{sym} opportunity approved but rejected during risk validation{r_msg} No order was sent to Binance."
        elif "PORTFOLIO" in stage_name or "GUARD" in stage_name:
            r_msg = f": {reason}" if reason else "."
            return f"{sym} opportunity approved by Meta-Reasoning but blocked by Portfolio Guard{r_msg} No order was sent to Binance."
        elif "MARKET_DATA" in stage_name:
            return f"{sym} opportunity rejected by Market Data Guard (WebSocket feed staleness or latency). No order was sent to Binance."
        elif "THESIS" in stage_name:
            return f"{sym} opportunity rejected by Thesis Persistence Guard (active conflicting thesis). No order was sent to Binance."
        elif "BINANCE" in stage_name or "EXCHANGE" in stage_name:
            r_msg = f": {reason}" if reason else "."
            return f"Order for {sym} was rejected by Binance exchange{r_msg} No position was created."
        else:
            r_msg = f": {reason}" if reason else "."
            return f"{sym} opportunity rejected at {stage_name}{r_msg} No order was sent to Binance."

    elif state == ExecutionState.CANCELLED:
        r_msg = f": {reason}" if reason else ""
        return f"Order for {sym} was cancelled{r_msg}."

    return f"Execution state for {sym}: {state.value}."


class ExecutionStateTracker:
    """
    Central Execution State Synchronization Engine for Lumina.
    Enforces the Hard Rule: NO component may claim a live trade exists without
    a Position Manager confirmation backed by an exchange order ID.
    """
    _instance: Optional['ExecutionStateTracker'] = None

    def __init__(self):
        # Current active states by symbol
        self._current_states: Dict[str, Dict[str, Any]] = {}
        # History of transitions
        self._state_history: List[Dict[str, Any]] = []
        self._max_history = 500

    @classmethod
    def get_instance(cls) -> 'ExecutionStateTracker':
        if cls._instance is None:
            cls._instance = ExecutionStateTracker()
        return cls._instance

    def transition(
        self,
        symbol: str,
        state: ExecutionState,
        side: str = "BUY",
        stage: Optional[str] = None,
        reason: Optional[str] = None,
        order_id: Optional[str] = None,
        price: Optional[float] = None,
        quantity: Optional[float] = None,
        trading_mode: str = "PAPER",
        metadata: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Records an execution state transition and formats the user-facing message.
        """
        sym = symbol.upper()
        is_live = trading_mode in ["TESTNET", "LIVE"]
        user_message = format_user_facing_message(
            state=state,
            symbol=sym,
            side=side,
            stage=stage,
            reason=reason,
            order_id=order_id,
            price=price,
            quantity=quantity,
            is_live=is_live
        )

        record = {
            "symbol": sym,
            "side": side.upper(),
            "state": state.value,
            "stage": stage,
            "reason": reason,
            "order_id": order_id,
            "price": price,
            "quantity": quantity,
            "trading_mode": trading_mode,
            "is_live": is_live,
            "user_message": user_message,
            "metadata": metadata or {},
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "timestamp_ms": time.time() * 1000.0
        }

        self._current_states[sym] = record
        self._state_history.append(record)
        if len(self._state_history) > self._max_history:
            self._state_history.pop(0)

        log_level = logging.WARNING if state in [ExecutionState.REJECTED, ExecutionState.CANCELLED] else logging.INFO
        logger.log(log_level, f"🔄 [EXECUTION STATE SYNC] {sym} -> {state.value} | {user_message}")
        return record

    def get_state(self, symbol: str) -> Optional[Dict[str, Any]]:
        """Returns the current state record for a symbol."""
        return self._current_states.get(symbol.upper())

    def get_all_states(self) -> Dict[str, Dict[str, Any]]:
        """Returns all current state records."""
        return self._current_states

    def get_history(self, limit: int = 50) -> List[Dict[str, Any]]:
        """Returns recent execution state transition history."""
        return self._state_history[-limit:]

    def verify_live_trade_claims(
        self,
        symbol: str,
        trading_mode: str = "LIVE"
    ) -> Tuple[bool, str, Optional[Dict[str, Any]]]:
        """
        HARD RULE ENFORCEMENT:
        Verifies all 5 conditions before any AI assistant/reporting layer can claim a trade is executed:
        1. Order Manager submitted order.
        2. Binance Connector returned successful response.
        3. Binance order status confirmed FILLED (or PARTIALLY_FILLED).
        4. Position Manager registered the new position.
        5. Execution Analytics recorded the fill.
        """
        from app.trading.position_manager import PositionManager
        sym = symbol.upper()
        pm = PositionManager.get_instance()
        active_positions = pm.get_active_positions(trading_mode=trading_mode)
        matching_pos = next((p for p in active_positions if p.get("symbol", "").upper() == sym), None)

        if not matching_pos:
            return (
                False,
                f"No active position registered in Position Manager for {sym}. Trade cannot be claimed as executed.",
                None
            )

        binance_order_id = matching_pos.get("binance_order_id")
        if trading_mode in ["TESTNET", "LIVE"] and not binance_order_id:
            return (
                False,
                f"Position for {sym} exists in memory but lacks verified exchange order ID. Trade cannot be claimed as live executed.",
                matching_pos
            )

        state_record = self._current_states.get(sym)
        if state_record and state_record.get("state") == ExecutionState.REJECTED.value:
            return (
                False,
                f"Execution state for {sym} was REJECTED: {state_record.get('user_message')}",
                None
            )

        return (
            True,
            f"Trade execution strictly confirmed for {sym} (Order #{binance_order_id or 'PAPER'}).",
            matching_pos
        )
