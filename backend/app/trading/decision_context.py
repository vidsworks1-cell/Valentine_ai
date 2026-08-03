import logging
from datetime import datetime, timezone
from typing import Dict, Any, Optional, List, Callable

logger = logging.getLogger("lumina.trading.decision_context")

# Event Bus Subscribers
_REJECTION_SUBSCRIBERS: List[Callable[['DecisionContext'], None]] = []
_APPROVAL_SUBSCRIBERS: List[Callable[['DecisionContext'], None]] = []

def register_rejection_subscriber(fn: Callable[['DecisionContext'], None]):
    """Registers a listener function to be notified when a DecisionContext is rejected."""
    if fn not in _REJECTION_SUBSCRIBERS:
        _REJECTION_SUBSCRIBERS.append(fn)

def register_approval_subscriber(fn: Callable[['DecisionContext'], None]):
    """Registers a listener function to be notified when a DecisionContext is locked and approved."""
    if fn not in _APPROVAL_SUBSCRIBERS:
        _APPROVAL_SUBSCRIBERS.append(fn)

def _default_rejection_handler(ctx: 'DecisionContext'):
    """Default system event handler for trade rejections."""
    symbol = ctx.symbol
    side = ctx.side
    price = ctx.price
    strategy = ctx.active_strategy
    category = ctx.rejection_category or "DEFAULT"
    reason = ctx.rejection_reason or "Pipeline rejection"
    stage = ctx.rejection_stage or "UNKNOWN"

    # 1. Adaptive Cooldown Engine
    try:
        from app.ai.reasoning import AiReasoningEngine
        engine = AiReasoningEngine.get_instance()
        if engine:
            engine.set_cooldown(symbol, side, category=category, reason=reason, price=price)
    except Exception as e:
        logger.debug(f"Event handler cooldown failed: {e}")

    # 2. Missed Opportunity Learner
    try:
        from app.ai.missed_opportunity_learner import MissedOpportunityLearner
        learner = MissedOpportunityLearner.get_instance()
        if learner:
            learner.record_missed_opportunity(
                symbol=symbol, side=side, entry_price=price,
                stop_loss=0.0, take_profit=0.0, strategy=strategy,
                category=category, reason=reason,
                confidence_score=ctx.adapted_confidence,
                confidence_threshold=ctx.dynamic_threshold
            )
    except Exception as e:
        logger.debug(f"Event handler missed opportunity learner failed: {e}")

    # 3. Execution Analytics
    try:
        from app.trading.execution_analytics import ExecutionAnalyticsEngine
        analytics = ExecutionAnalyticsEngine.get_instance()
        if analytics:
            analytics.record_missed_fill(symbol, side, strategy, reason, price)
    except Exception as e:
        logger.debug(f"Event handler execution analytics failed: {e}")

    # 4. Portfolio Attribution Risk Prevention Event
    try:
        from app.trading.portfolio_attribution import PortfolioAttributionEngine
        attribution = PortfolioAttributionEngine.get_instance()
        if attribution:
            attribution.record_risk_prevention_event(stage.lower(), 150.0, reason)
    except Exception as e:
        logger.debug(f"Event handler portfolio attribution failed: {e}")

register_rejection_subscriber(_default_rejection_handler)

class DecisionContext:
    """
    Unified Request-Scoped Decision Context for Lumina.
    Caches evaluation results across pipeline stages to eliminate duplicate calculations,
    maintains canonical immutable strategy snapshots, short-circuits expensive AI
    reasoning when fast deterministic guards fail, enforces post-lock immutability,
    maintains an end-to-end execution pipeline trace, and publishes decision events.
    """
    
    _LOCKED_ATTRIBUTES = {
        "symbol", "side", "price", "original_strategy", "active_strategy",
        "raw_confidence", "adapted_confidence", "dynamic_threshold",
        "meta_conviction_score", "position_multiplier", "atr"
    }

    def __init__(self, symbol: str, side: str, price: float, original_strategy: str, atr: float = 0.0):
        self.__dict__["is_locked"] = False
        
        self.signal_id: str = f"sig_{int(datetime.now(timezone.utc).timestamp()*1000)}_{symbol}_{side}"
        self.timestamp: datetime = datetime.now(timezone.utc)
        self.symbol: str = symbol.upper()
        self.side: str = side.upper()
        self.price: float = price
        self.atr: float = atr

        # Decision & Model Versioning Metadata
        self.pipeline_version: str = "2.4.0-institutional"
        self.guard_config_version: str = "v1.8.2"
        self.strategy_model_version: str = "v3.1.0"
        self.confidence_model_version: str = "v2.0.0"
        
        # Strategy Immutable Snapshot Tracking
        self.original_strategy: str = original_strategy
        self.active_strategy: str = original_strategy
        self.is_substituted: bool = False
        self.substitution_reason: str = ""
        self.parameter_retuned: bool = False
        
        # Request-Scoped Evaluation Cache
        self.eval_cache: Dict[str, Any] = {}
        
        # End-to-End Pipeline Trace Log
        self.pipeline_trace: List[Dict[str, Any]] = []
        
        # Outcomes & Telemetry
        self.meta_conviction_score: float = 75.0
        self.meta_approved: bool = True
        self.meta_transcript: Dict[str, Any] = {}
        self.raw_confidence: float = 0.80
        self.adapted_confidence: float = 0.80
        self.dynamic_threshold: float = 0.70
        self.adaptive_modifier: float = 0.0
        self.position_multiplier: float = 1.0
        
        # Causal Attribution Breakdown (Decision Intelligence)
        self.causal_attribution: Dict[str, Any] = {}
        
        # Rejection & Immutability State
        self.is_rejected: bool = False
        self.rejection_stage: Optional[str] = None
        self.rejection_reason: Optional[str] = None
        self.rejection_category: Optional[str] = None

    def get_guard_result(self, guard_name: str) -> Optional[Any]:
        """Retrieves cached guard evaluation result from context."""
        return self.eval_cache.get(guard_name)

    def set_guard_result(self, guard_name: str, result: Any):
        """Caches a guard evaluation result in context."""
        self.eval_cache[guard_name] = result

    def compute_causal_attribution(self) -> Dict[str, Any]:
        """
        Computes explicit causal factor breakdown for decision confidence attribution:
        e.g., Base (0.50) + Trend (+0.24) + Volume (+0.18) + Bayesian (+0.17) - Volatility (-0.08) - Macro (-0.05)
        """
        base_confidence = 0.50
        factors = []
        
        # Trend Confirmation
        mtf_data = self.eval_cache.get("mtf_alignment", {})
        align_score = mtf_data.get("alignment_score", 0.75) if isinstance(mtf_data, dict) else 0.75
        trend_contrib = round(align_score * 0.32, 2)
        factors.append({"factor": "Trend confirmation", "impact": trend_contrib, "type": "POSITIVE"})

        # Volume & Liquidity Confirmation
        vol_data = self.eval_cache.get("volume_liquidity", {})
        vol_rvol = vol_data.get("rvol", 1.5) if isinstance(vol_data, dict) else 1.5
        vol_contrib = round(min(0.25, (vol_rvol - 1.0) * 0.20 + 0.10), 2)
        factors.append({"factor": "Volume confirmation", "impact": vol_contrib, "type": "POSITIVE"})

        # Bayesian Expectancy
        bayesian_prob = self.eval_cache.get("bayesian_probability", 0.68)
        bayesian_contrib = round((bayesian_prob - 0.50) * 0.50, 2)
        factors.append({"factor": "Bayesian expectancy", "impact": bayesian_contrib, "type": "POSITIVE" if bayesian_contrib >= 0 else "NEGATIVE"})

        # Volatility & Macro Penalties / Boosts
        macro_risk = self.eval_cache.get("macro_risk", {}).get("risk_level", "NORMAL")
        if macro_risk == "HIGH" or macro_risk == "CRITICAL":
            macro_impact = -0.12
        elif macro_risk == "ELEVATED":
            macro_impact = -0.05
        else:
            macro_impact = 0.05
        factors.append({"factor": "Macro uncertainty", "impact": macro_impact, "type": "NEGATIVE" if macro_impact < 0 else "POSITIVE"})

        # Squeeze & Liquidity Sweep
        if self.eval_cache.get("squeeze_info", {}).get("squeeze_state") == "SQUEEZE_FIRING":
            sweep_contrib = 0.12
            factors.append({"factor": "Liquidity sweep", "impact": sweep_contrib, "type": "POSITIVE"})

        # Net Attribution Summary
        total_impact = sum(f["impact"] for f in factors)
        net_confidence = round(max(0.0, min(1.0, base_confidence + total_impact)), 2)

        self.causal_attribution = {
            "signal_id": self.signal_id,
            "base_confidence": base_confidence,
            "net_confidence": net_confidence,
            "factors": factors,
            "top_positive_driver": max(factors, key=lambda f: f["impact"])["factor"] if factors else "None",
            "top_negative_driver": min(factors, key=lambda f: f["impact"])["factor"] if factors else "None"
        }
        self.add_trace("CausalAttribution", "PASS", self.causal_attribution)
        return self.causal_attribution

    def verify_pipeline_integrity(self) -> tuple[bool, str]:
        """
        Verifies all pipeline invariants before order execution:
          1. Decision is locked and NOT rejected
          2. Required guards evaluated (MacroGuard, MTFGuard, VolumeLiquidityGuard)
          3. Immutability invariants met (valid symbol, side, positive price)
          4. Adapted confidence >= dynamic threshold
        Returns (is_valid: bool, error_reason: str)
        """
        if self.is_rejected:
            return False, f"DecisionContext {self.signal_id} is rejected ({self.rejection_reason})"
        if not self.is_locked:
            return False, f"DecisionContext {self.signal_id} is not locked"
        if not self.symbol or not self.side:
            return False, "Symbol or Side is missing"
        if self.price <= 0.0:
            return False, f"Invalid entry price: {self.price}"
        if not self.active_strategy:
            return False, "Active strategy is missing"
        if self.adapted_confidence < self.dynamic_threshold:
            return False, f"Confidence invariant failed ({self.adapted_confidence} < {self.dynamic_threshold})"
        
        # Verify trace steps
        executed_steps = {t["step"] for t in self.pipeline_trace if t["status"] == "PASS"}
        required_guards = {"MacroGuard", "MTFGuard", "VolumeLiquidityGuard", "MetaReasoning"}
        missing_guards = required_guards - executed_steps
        if missing_guards:
            return False, f"Pipeline execution incomplete. Missing required guard traces: {missing_guards}"

        return True, "Pipeline Integrity Verified"

    def __setattr__(self, name: str, value: Any):
        if self.__dict__.get("is_locked", False) and name in self._LOCKED_ATTRIBUTES:
            raise AttributeError(
                f"🚫 [IMMUTABILITY VIOLATION] DecisionContext {self.signal_id} is locked. "
                f"Attempted to mutate locked field '{name}' to '{value}'."
            )
        super().__setattr__(name, value)

    def add_trace(self, step: str, status: str, details: Optional[Dict[str, Any]] = None):
        """Appends a stage record to the deterministic pipeline execution trace."""
        trace_entry = {
            "step": step,
            "status": status,  # "PASS", "FAIL", "SKIPPED", "UPDATED"
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "details": details or {}
        }
        self.pipeline_trace.append(trace_entry)
        logger.debug(f"📍 [PIPELINE TRACE] [{step}] -> {status}")

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
            old_strat = self.active_strategy
            self.active_strategy = substitute_strategy
            self.is_substituted = True
            self.substitution_reason = reason
            self.parameter_retuned = retuned
            # Clear strategy-dependent evaluation cache entries so downstream guards evaluate with new strategy weights
            self.eval_cache.pop("mtf_alignment", None)
            self.eval_cache.pop("strategy_eval", None)
            self.add_trace("StrategySubstitution", "UPDATED", {
                "from": old_strat,
                "to": substitute_strategy,
                "reason": reason
            })

    def record_rejection(self, stage: str, reason: str, category: str = "GUARD_REJECT"):
        """
        Centralized single-point rejection logger & event publisher for the decision context.
        Guarantees that a signal is rejected exactly ONCE.
        """
        if self.is_rejected:
            return
        self.is_rejected = True
        self.rejection_stage = stage
        self.rejection_reason = reason
        self.rejection_category = category
        self.add_trace(stage, "FAIL", {"reason": reason, "category": category})
        logger.warning(f"🚫 [DECISION PIPELINE REJECTED] [{stage}] {self.symbol} {self.side} ({self.active_strategy}): {reason}")
        
        # Publish EVENT_TRADE_REJECTED to all event bus subscribers
        for sub in list(_REJECTION_SUBSCRIBERS):
            try:
                sub(self)
            except Exception as e:
                logger.error(f"Error in rejection event subscriber: {e}")

    def lock_decision(self):
        """Freezes context parameters to prevent downstream mutation or drift."""
        if not self.is_locked:
            self.add_trace("DecisionLock", "PASS")
            self.__dict__["is_locked"] = True
            # Publish EVENT_TRADE_APPROVED to subscribers
            for sub in list(_APPROVAL_SUBSCRIBERS):
                try:
                    sub(self)
                except Exception as e:
                    logger.error(f"Error in approval event subscriber: {e}")

    def get_pipeline_trace_summary(self) -> List[str]:
        """Returns readable pipeline trace lines for debugging & telemetry."""
        lines = []
        for t in self.pipeline_trace:
            icon = "✔" if t["status"] == "PASS" else ("✖" if t["status"] == "FAIL" else ("⏩" if t["status"] == "SKIPPED" else "🔄"))
            lines.append(f"{t['step']} [{t['status']}] {icon}")
        return lines

    def to_setup_dict(self) -> Dict[str, Any]:
        """Converts decision context into standardized opportunity dictionary."""
        return {
            "signal_id": self.signal_id,
            "pipeline_version": self.pipeline_version,
            "guard_config_version": self.guard_config_version,
            "strategy_model_version": self.strategy_model_version,
            "confidence_model_version": self.confidence_model_version,
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
            "pipeline_trace": self.pipeline_trace,
            "is_locked": self.is_locked,
            "decision_context": self
        }

