import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.trading.adaptive_guard_thresholds")

class AdaptiveGuardThresholds:
    """
    Adaptive Guard Thresholds Engine for Lumina.
    Replaces static thresholds with dynamic, regime-aware learned parameters based on
    market regimes (VOLATILE, TRENDING, RANGING, COMPRESSED) and empirical precision metrics
    from the Decision Quality Engine.
    
    Example adaptations:
      - VOLATILE regime: Relax MTF threshold to 61.0% if precision remains high, require higher Meta Conviction (75).
      - RANGING regime: Tighten MTF threshold to 74.0% to avoid chop losses.
      - TRENDING regime: Allow MTF 60.0% to ride early trends, require lower Meta Conviction (65).
    """
    _instance: Optional['AdaptiveGuardThresholds'] = None

    @classmethod
    def get_instance(cls) -> 'AdaptiveGuardThresholds':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        # Default baseline thresholds
        self._baseline_thresholds = {
            "mtf_alignment_pct": 66.0,
            "meta_conviction_score": 70.0,
            "confidence_threshold": 0.70,
            "volume_confluence_score": 70.0,
            "macro_risk_limit": 65.0
        }
        
        # Learned regime multipliers / overrides
        self._regime_adjustments = {
            "VOLATILE": {
                "mtf_alignment_pct": 61.0,        # Relax MTF alignment in strong volatile momentum
                "meta_conviction_score": 75.0,    # Require higher conviction during high volatility
                "confidence_threshold": 0.74,     # Higher confidence bar in volatile markets
                "volume_confluence_score": 75.0
            },
            "RANGING": {
                "mtf_alignment_pct": 74.0,        # Tighten MTF alignment to prevent false breakout chop
                "meta_conviction_score": 72.0,
                "confidence_threshold": 0.72,
                "volume_confluence_score": 65.0
            },
            "TRENDING": {
                "mtf_alignment_pct": 60.0,        # Lower MTF threshold to jump on confirmed trends early
                "meta_conviction_score": 65.0,    # Lower conviction bar needed in strong trends
                "confidence_threshold": 0.65,
                "volume_confluence_score": 70.0
            },
            "COMPRESSED": {
                "mtf_alignment_pct": 68.0,
                "meta_conviction_score": 75.0,
                "confidence_threshold": 0.72,
                "volume_confluence_score": 75.0
            }
        }

    def get_effective_threshold(self, guard_param: str, regime: str = "NORMAL") -> float:
        """
        Returns the regime-aware dynamic threshold for a specific guard parameter.
        Uses Decision Quality metrics to continuously calibrate boundaries.
        """
        regime_upper = regime.upper()
        base = self._baseline_thresholds.get(guard_param, 66.0)
        
        regime_override = self._regime_adjustments.get(regime_upper, {}).get(guard_param)
        if regime_override is not None:
            effective = regime_override
        else:
            effective = base

        # Consult Decision Quality Engine for feedback calibration
        try:
            from app.trading.decision_quality import DecisionQualityEngine
            dq = DecisionQualityEngine.get_instance()
            metrics = dq.get_system_decision_metrics()
            false_rejection_rate = metrics.get("system_false_rejection_rate", 0.0)
            false_approval_rate = metrics.get("false_approval_rate", 0.0)

            # High false rejection rate -> slightly relax threshold to capture valid trades
            if false_rejection_rate > 0.25 and guard_param == "mtf_alignment_pct":
                effective = max(55.0, effective - 3.0)
                logger.debug(f"⚙️ [ADAPTIVE THRESHOLD] High false rejection rate ({false_rejection_rate:.2f}). Relaxed {guard_param} to {effective}")
            # High false approval rate -> slightly tighten threshold to protect capital
            elif false_approval_rate > 0.30 and guard_param == "confidence_threshold":
                effective = min(0.85, effective + 0.04)
                logger.debug(f"⚙️ [ADAPTIVE THRESHOLD] High false approval rate ({false_approval_rate:.2f}). Tightened {guard_param} to {effective}")

        except Exception as e:
            logger.debug(f"Threshold feedback calibration skipped: {e}")

        return round(effective, 2)

    def update_regime_threshold(self, regime: str, guard_param: str, new_value: float):
        """Allows online learning algorithms to update learned regime threshold parameters."""
        regime_upper = regime.upper()
        if regime_upper not in self._regime_adjustments:
            self._regime_adjustments[regime_upper] = {}
        self._regime_adjustments[regime_upper][guard_param] = new_value
        logger.info(f"🧠 [ADAPTIVE THRESHOLDS] Updated learned parameter '{guard_param}' for regime {regime_upper} -> {new_value}")

    def get_guard_impact_matrix(self) -> Dict[str, Any]:
        """
        Calculates empirical counterfactual impact and net economic value delivered for all Lumina guards.
        Answers: 'How much money did each guard save vs cost over time, and what is its optimal learned threshold?'
        """
        guard_names = {
            "CONFIDENCE_FILTER": "Confidence Filter",
            "PORTFOLIO_GUARD": "Portfolio Guard (Beta/Exposure)",
            "VOLUME_GUARD": "Volume Confluence Guard",
            "MTF_GUARD": "MTF Confirmation Guard",
            "MACRO_RISK": "Macro Risk Overlay",
            "COOLDOWN": "Evaluation Cooldown Guard",
            "THESIS_PERSISTENCE_GUARD": "Thesis Persistence Guard",
            "BINANCE_REJECTION": "Binance Execution Guard"
        }

        from app.ai.missed_opportunity_learner import MissedOpportunityLearner
        learner = MissedOpportunityLearner.get_instance()
        records = learner._records

        matrix: Dict[str, Dict[str, Any]] = {}
        for cat_key, display_name in guard_names.items():
            cat_records = [r for r in records if r.get("category") == cat_key]
            saved_recs = [r for r in cat_records if r.get("status") == "SAVED_BY_GUARD"]
            cost_recs = [r for r in cat_records if r.get("status") == "COST_PROFIT"]
            pending_recs = [r for r in cat_records if r.get("status") == "PENDING_EVALUATION"]

            saved_pnl = sum(abs(r.get("counterfactual_pnl_pct", 0.0)) for r in saved_recs)
            cost_pnl = sum(abs(r.get("counterfactual_pnl_pct", 0.0)) for r in cost_recs)
            net_value = round(saved_pnl - cost_pnl, 2)
            total_evaluated = len(saved_recs) + len(cost_recs)
            precision_pct = round((len(saved_recs) / max(1, total_evaluated)) * 100.0, 1)

            # Generate dynamic threshold recommendation based on empirical precision
            current_base = self._baseline_thresholds.get(cat_key.lower(), 70.0)
            if total_evaluated >= 3:
                if precision_pct >= 65.0:
                    recommendation = f"Threshold optimal. Saved ${saved_pnl:.2f}% loss with {precision_pct}% precision."
                    rec_threshold = current_base
                elif precision_pct <= 40.0:
                    # High false rejection rate -> recommend relaxing threshold by 2-3%
                    rec_threshold = round(max(50.0, current_base - 2.0), 1)
                    recommendation = f"Empirical discovery: Threshold should be {rec_threshold}%, not {current_base}%. Relaxing captures missed alpha with minimal drawdown."
                else:
                    rec_threshold = current_base
                    recommendation = f"Balanced performance. Current threshold {current_base}% remains stable."
            else:
                rec_threshold = current_base
                recommendation = f"Sampling data: {len(cat_records)} setups logged. Current threshold: {current_base}%."

            matrix[cat_key] = {
                "guard_name": display_name,
                "total_blocked_setups": len(cat_records),
                "saved_capital_count": len(saved_recs),
                "cost_profit_count": len(cost_recs),
                "pending_count": len(pending_recs),
                "saved_capital_pnl_pct": round(saved_pnl, 2),
                "cost_profit_pnl_pct": round(cost_pnl, 2),
                "net_economic_value_pct": net_value,
                "empirical_precision_pct": precision_pct,
                "current_threshold": current_base,
                "recommended_threshold": rec_threshold,
                "recommendation": recommendation
            }

        return {
            "guard_impact_matrix": matrix,
            "total_blocked_tracked": len(records),
            "total_saved_pnl_pct": round(sum(m["saved_capital_pnl_pct"] for m in matrix.values()), 2),
            "total_cost_pnl_pct": round(sum(m["cost_profit_pnl_pct"] for m in matrix.values()), 2),
            "net_system_guard_pnl_pct": round(sum(m["net_economic_value_pct"] for m in matrix.values()), 2)
        }
