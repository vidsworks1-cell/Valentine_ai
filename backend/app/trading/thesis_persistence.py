import logging
import time
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.thesis_persistence")

class MarketThesis:
    """Represents an active, persistent investment thesis for a trading position."""
    def __init__(
        self,
        thesis_id: str,
        trade_id: int,
        symbol: str,
        side: str,
        strategy: str,
        entry_price: float,
        entry_confidence: float,
        market_regime: str,
        expected_duration_sec: float = 3600.0,
        invalidation_conditions: Optional[Dict[str, Any]] = None
    ):
        self.thesis_id = thesis_id
        self.trade_id = trade_id
        self.symbol = symbol.upper()
        self.side = side.upper()
        self.strategy = strategy
        self.entry_price = float(entry_price)
        self.entry_confidence = float(entry_confidence)
        self.market_regime = market_regime.upper()
        self.created_at = time.time()
        self.expected_duration_sec = float(expected_duration_sec)
        self.invalidation_conditions = invalidation_conditions or {
            "opposite_conviction_threshold": 0.88,
            "min_conviction_delta": 0.10,
            "max_adverse_r": 1.0,
            "mtf_reversal_threshold": 0.82
        }
        self.noise_signals_filtered = 0
        self.status = "ACTIVE" # ACTIVE, INVALIDATED, EXPIRED, COMPLETED
        self.last_noise_filtered_at: Optional[float] = None

    @property
    def age_seconds(self) -> float:
        return time.time() - self.created_at

    @property
    def age_minutes(self) -> float:
        return self.age_seconds / 60.0

    @property
    def is_expired(self) -> bool:
        return self.age_seconds > self.expected_duration_sec

    def get_current_confidence(self) -> float:
        """Applies a gentle time-decay to confidence over the expected horizon."""
        if self.expected_duration_sec <= 0:
            return self.entry_confidence
        elapsed_ratio = min(1.0, self.age_seconds / self.expected_duration_sec)
        # Decays at most 20% of original confidence by end of horizon
        decayed = self.entry_confidence * (1.0 - (0.20 * elapsed_ratio))
        return round(decayed, 3)

    def calculate_remaining_edge(self, current_price: float, atr: float = 0.0) -> float:
        """Calculates current R-multiple or ATR buffer remaining."""
        if self.entry_price <= 0:
            return 0.0
        if self.side == "BUY":
            price_delta = current_price - self.entry_price
        else:
            price_delta = self.entry_price - current_price
        
        if atr > 0:
            return round(price_delta / atr, 2)
        return round((price_delta / self.entry_price) * 100.0, 2)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "thesis_id": self.thesis_id,
            "trade_id": self.trade_id,
            "symbol": self.symbol,
            "side": self.side,
            "strategy": self.strategy,
            "entry_price": self.entry_price,
            "entry_confidence": self.entry_confidence,
            "current_confidence": self.get_current_confidence(),
            "market_regime": self.market_regime,
            "created_at": self.created_at,
            "age_minutes": round(self.age_minutes, 1),
            "expected_duration_minutes": round(self.expected_duration_sec / 60.0, 1),
            "noise_signals_filtered": self.noise_signals_filtered,
            "status": self.status
        }


class ThesisPersistenceEngine:
    """
    Thesis Persistence Layer for Lumina.
    
    Prevents short-term signal noise and rapid flip-flopping (e.g. BUY -> SELL -> BUY -> SELL in minutes).
    A professional trader holds a conviction thesis until it is structurally invalidated,
    rather than reconsidering their entire book on every 1-minute ticker fluctuation.
    """
    _instance: Optional['ThesisPersistenceEngine'] = None

    def __init__(self):
        self._theses: Dict[str, MarketThesis] = {} # Keyed by symbol
        self._total_noise_filtered_count: int = 0
        self._theses_history: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'ThesisPersistenceEngine':
        if cls._instance is None:
            cls._instance = ThesisPersistenceEngine()
        return cls._instance

    def register_thesis(
        self,
        trade_id: int,
        symbol: str,
        side: str,
        strategy: str,
        entry_price: float,
        entry_confidence: float = 0.85,
        market_regime: str = "TRENDING",
        expected_duration_sec: Optional[float] = None
    ) -> MarketThesis:
        """Registers and arms a new active market thesis upon trade fill."""
        sym = symbol.upper()
        
        # Determine expected holding horizon based on regime and strategy
        if expected_duration_sec is None:
            if "BREAKOUT" in strategy.upper() or market_regime.upper() == "VOLATILE":
                expected_duration_sec = 2400.0  # 40 mins
            elif market_regime.upper() == "RANGING":
                expected_duration_sec = 1800.0  # 30 mins
            else:
                expected_duration_sec = 3600.0  # 60 mins (Trending default)

        thesis_id = f"THESIS_{sym}_{side.upper()}_{int(time.time())}"
        thesis = MarketThesis(
            thesis_id=thesis_id,
            trade_id=trade_id,
            symbol=sym,
            side=side,
            strategy=strategy,
            entry_price=entry_price,
            entry_confidence=entry_confidence,
            market_regime=market_regime,
            expected_duration_sec=expected_duration_sec
        )
        self._theses[sym] = thesis
        logger.info(
            f"🧠 [THESIS PERSISTENCE LAYER] Active Thesis Registered for {sym} ({side.upper()}) via {strategy} at ${entry_price:.2f} "
            f"[Conviction: {entry_confidence:.2f} | Horizon: {expected_duration_sec/60:.0f}m | Regime: {market_regime}]."
        )
        return thesis

    def evaluate_new_signal(
        self,
        symbol: str,
        proposed_side: str,
        proposed_strategy: str,
        proposed_confidence: float,
        current_price: float = 0.0
    ) -> Tuple[bool, str, Dict[str, Any]]:
        """
        Evaluates an incoming scanner opportunity against any active thesis.
        Returns:
            (allowed_to_proceed, explanation_reason, active_thesis_dict)
        """
        sym = symbol.upper()
        proposed_side = proposed_side.upper()
        thesis = self._theses.get(sym)

        if not thesis or thesis.status != "ACTIVE":
            return True, "No active thesis in place. Opportunity cleared for processing.", {}

        # If incoming signal is the SAME side (reinforcement / pyramiding candidate)
        if proposed_side == thesis.side:
            return True, f"Signal agrees with active {thesis.side} thesis (Age: {thesis.age_minutes:.1f}m).", thesis.to_dict()

        # Incoming signal is CONTRADICTORY (e.g. SELL while in BUY thesis)
        # Check: Has anything fundamentally changed to invalidate the active thesis?
        current_thesis_conf = thesis.get_current_confidence()
        min_reversal_conf = thesis.invalidation_conditions.get("opposite_conviction_threshold", 0.88)
        required_delta = thesis.invalidation_conditions.get("min_conviction_delta", 0.10)

        is_overwhelming_invalidation = (
            proposed_confidence >= min_reversal_conf and
            proposed_confidence >= (current_thesis_conf + required_delta)
        )

        if is_overwhelming_invalidation or thesis.is_expired:
            action_desc = "Overwhelming structural reversal" if is_overwhelming_invalidation else "Holding horizon expired"
            logger.warning(
                f"🚨 [THESIS INVALIDATION RECOGNIZED] Contradictory {proposed_side} signal for {sym} ({action_desc}): "
                f"Opposing Conviction: {proposed_confidence:.2f} vs Active Thesis: {current_thesis_conf:.2f}. "
                f"Allowing pipeline to proceed with position flip / close."
            )
            return True, f"Thesis invalidated by {action_desc} (Conviction: {proposed_confidence:.2f} > {current_thesis_conf:.2f}).", thesis.to_dict()

        # SUPPRESS CHOP SIGNAL
        thesis.noise_signals_filtered += 1
        thesis.last_noise_filtered_at = time.time()
        self._total_noise_filtered_count += 1

        reason = (
            f"🛡️ [THESIS PERSISTENCE GUARD] Suppressed contradictory {proposed_side} noise on {sym}: "
            f"Active {thesis.side} thesis ({thesis.strategy}) remains intact (Holding: {thesis.age_minutes:.1f}m / "
            f"{thesis.expected_duration_sec/60:.0f}m, Thesis Conviction: {current_thesis_conf:.2f} > Incoming: {proposed_confidence:.2f}). "
            f"Noise signals filtered on this position: {thesis.noise_signals_filtered}."
        )
        logger.info(reason)
        return False, reason, thesis.to_dict()

    def close_or_invalidate_thesis(self, symbol_or_trade_id: Any, reason: str = "POSITION_CLOSED"):
        """Marks an active thesis as completed or invalidated upon exit."""
        target_sym = None
        for sym, th in list(self._theses.items()):
            if sym == str(symbol_or_trade_id).upper() or str(th.trade_id) == str(symbol_or_trade_id):
                target_sym = sym
                break

        if target_sym and target_sym in self._theses:
            th = self._theses.pop(target_sym)
            th.status = "COMPLETED" if "PROFIT" in reason.upper() else "INVALIDATED"
            th_dict = th.to_dict()
            th_dict["closed_reason"] = reason
            th_dict["closed_at"] = time.time()
            self._theses_history.append(th_dict)
            if len(self._theses_history) > 100:
                self._theses_history.pop(0)
            logger.info(f"🧠 [THESIS PERSISTENCE LAYER] Concluded thesis {th.thesis_id} for {target_sym}: {reason} (Filtered {th.noise_signals_filtered} noise signals during hold).")

    def purge_expired_theses(self) -> List[str]:
        """Automatically purges expired and orphaned theses to prevent stale suppression of valid opportunities."""
        purged = []
        now = time.time()
        for sym, th in list(self._theses.items()):
            # If thesis expired past max buffer (2x expected duration or > 4 hours max)
            max_age_sec = max(th.expected_duration_sec * 1.5, 7200.0)
            if th.age_seconds > max_age_sec:
                self.close_or_invalidate_thesis(sym, reason=f"HORIZON_EXPIRED_PURGE (Held {th.age_minutes:.1f}m)")
                purged.append(sym)
        return purged

    def get_active_theses(self) -> List[Dict[str, Any]]:
        self.purge_expired_theses()
        return [th.to_dict() for th in self._theses.values()]

    def get_summary(self) -> Dict[str, Any]:
        self.purge_expired_theses()
        return {
            "active_theses_count": len(self._theses),
            "total_noise_signals_filtered": self._total_noise_filtered_count,
            "active_theses": self.get_active_theses(),
            "recent_theses_history": self._theses_history[-10:]
        }
