import logging
from datetime import datetime, timezone
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.trading.decision_context")

class DecisionContext:
    """
    Unified Request-Scoped Decision Context for Lumina.
    Caches evaluation results across pipeline stages to eliminate duplicate calculations,
    maintains canonical immutable strategy snapshots, and short-circuits expensive AI
    reasoning when fast deterministic guards fail.
    """
    def __init__(self, symbol: str, side: str, price: float, original_strategy: str, atr: float = 0.0):
        self.signal_id: str = f"sig_{int(datetime.now(timezone.utc).timestamp()*1000)}_{symbol}_{side}"
        self.timestamp: datetime = datetime.now(timezone.utc)
        self.symbol: str = symbol.upper()
        self.side: str = side.upper()
        self.price: float = price
        self.atr: float = atr
        
        # Strategy Immutable Snapshot Tracking
        self.original_strategy: str = original_strategy
        self.active_strategy: str = original_strategy
        self.is_substituted: bool = False
        self.substitution_reason: str = ""
        self.parameter_retuned: bool = False
        
        # Request-Scoped Evaluation Cache
        self.eval_cache: Dict[str, Any] = {}
        
        # Outcomes & Telemetry
        self.meta_conviction_score: float = 75.0
        self.meta_approved: bool = True
        self.meta_transcript: Dict[str, Any] = {}
        self.raw_confidence: float = 0.80
        self.adapted_confidence: float = 0.80
        self.dynamic_threshold: float = 0.70
        self.adaptive_modifier: float = 0.0
        self.position_multiplier: float = 1.0
        
        # Rejection & Immutability State
        self.is_rejected: bool = False
        self.rejection_stage: Optional[str] = None
        self.rejection_reason: Optional[str] = None
        self.rejection_category: Optional[str] = None
        self.is_locked: bool = False

    def update_active_strategy(self, substitute_strategy: str, reason: str, retuned: bool = False):
        """Updates active strategy while maintaining original_strategy for auditing."""
        if self.is_locked:
            logger.warning(f"Attempted to update active_strategy on locked DecisionContext {self.signal_id}")
            return
        if substitute_strategy != self.active_strategy:
            logger.info(
                f"🔄 [DECISION CONTEXT] Strategy substitution applied: '{self.original_strategy}' -> '{substitute_strategy}'. "
                f"Reason: {reason}"
            )
            self.active_strategy = substitute_strategy
            self.is_substituted = True
            self.substitution_reason = reason
            self.parameter_retuned = retuned

    def record_rejection(self, stage: str, reason: str, category: str = "GUARD_REJECT"):
        """Centralized single-point rejection logger for the decision context."""
        if self.is_rejected:
            return
        self.is_rejected = True
        self.rejection_stage = stage
        self.rejection_reason = reason
        self.rejection_category = category
        logger.warning(f"🚫 [DECISION PIPELINE REJECTED] [{stage}] {self.symbol} {self.side} ({self.active_strategy}): {reason}")

    def lock_decision(self):
        """Freezes context parameters to prevent downstream mutation or drift."""
        self.is_locked = True

    def to_setup_dict(self) -> Dict[str, Any]:
        """Converts decision context into standardized opportunity dictionary."""
        return {
            "signal_id": self.signal_id,
            "symbol": self.symbol,
            "side": self.side,
            "price": self.price,
            "entry_price": self.price,
            "atr": self.atr,
            "original_strategy": self.original_strategy,
            "strategy_used": self.active_strategy,
            "strategy": self.active_strategy,
            "is_substituted": self.is_substituted,
            "substitution_reason": self.substitution_reason,
            "parameter_retuned": self.parameter_retuned,
            "meta_conviction_score": self.meta_conviction_score,
            "meta_approved": self.meta_approved,
            "meta_transcript": self.meta_transcript,
            "confidence_score": self.adapted_confidence,
            "raw_confidence_score": self.raw_confidence,
            "adaptive_modifier": self.adaptive_modifier,
            "confidence_threshold": self.dynamic_threshold,
            "is_rejected": self.is_rejected,
            "rejection_stage": self.rejection_stage,
            "rejection_reason": self.rejection_reason,
            "eval_cache": self.eval_cache,
            "decision_context": self
        }
