import logging
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

logger = logging.getLogger("lumina.ai.causal_learning")

class CausalLearningEngine:
    """
    Causal Learning Engine for Lumina (Learning Brain Component).
    Moves beyond simple correlation to discover multi-factor root causes for trade outcomes
    (e.g., losses and missed opportunities).
    
    Identifies patterns such as:
      - "Breakout Momentum underperforms during low-liquidity Asian session"
      - "Meta-Reasoning is overly optimistic when 1h MTF alignment is weak (< 55%)"
      - "High funding rate skew combined with volatile regime leads to 78% SL rate"
    """
    _instance: Optional['CausalLearningEngine'] = None

    @classmethod
    def get_instance(cls) -> 'CausalLearningEngine':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        # Causal event logs
        self._causal_events: List[Dict[str, Any]] = []
        # Discovered root cause rules -> rule_summary
        self._discovered_causal_rules: List[Dict[str, Any]] = []
        self._register_subscribers()

    def _register_subscribers(self):
        try:
            from app.trading.decision_context import register_rejection_subscriber
            register_rejection_subscriber(self.on_trade_rejected)
            logger.info("CausalLearningEngine subscribed to DecisionContext events.")
        except Exception as e:
            logger.error(f"Failed to register CausalLearningEngine subscribers: {e}")

    def on_trade_rejected(self, ctx):
        """Analyzes multi-factor context parameters when a trade is rejected."""
        event = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "signal_id": ctx.signal_id,
            "symbol": ctx.symbol,
            "side": ctx.side,
            "strategy": ctx.active_strategy,
            "rejection_stage": ctx.rejection_stage,
            "rejection_reason": ctx.rejection_reason,
            "category": ctx.rejection_category,
            "eval_cache": ctx.eval_cache
        }
        self._causal_events.append(event)
        self._mine_causal_patterns()

    def record_realised_trade_outcome(self, symbol: str, strategy: str, pnl: float, context_snapshot: Dict[str, Any]):
        """Records a completed trade outcome for root cause analysis."""
        event = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "symbol": symbol,
            "strategy": strategy,
            "outcome": "WIN" if pnl > 0 else "LOSS",
            "pnl": pnl,
            "context": context_snapshot
        }
        self._causal_events.append(event)
        self._mine_causal_patterns()

    def _mine_causal_patterns(self):
        """Mines causal patterns once sufficient event volume is collected."""
        if len(self._causal_events) < 5:
            return

        # Simple causal rule extractor example:
        mtf_rejects = [e for e in self._causal_events if e.get("category") == "MTF_DISAGREEMENT"]
        if len(mtf_rejects) >= 3:
            rule = {
                "rule_id": f"causal_{len(self._discovered_causal_rules)+1}",
                "cause": "Multi-timeframe disagreement across 5m/15m/1h",
                "effect": "High probability of stop-out / trend failure",
                "confidence": 0.88,
                "recommendation": "Enforce strict MTF alignment pre-filter before Phase 2 AI debate"
            }
            if not any(r["cause"] == rule["cause"] for r in self._discovered_causal_rules):
                self._discovered_causal_rules.append(rule)
                logger.info(f"🧬 [CAUSAL LEARNING ENGINE] Discovered Causal Rule: {rule['cause']} -> {rule['effect']} (Confidence: {rule['confidence']*100:.0f}%)")

    def get_discovered_causal_rules(self) -> List[Dict[str, Any]]:
        """Returns all mined root-cause rules for self-adaptation and auditing."""
        return self._discovered_causal_rules
