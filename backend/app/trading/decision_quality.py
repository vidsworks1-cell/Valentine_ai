import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

logger = logging.getLogger("lumina.trading.decision_quality")

class DecisionQualityEngine:
    """
    Decision Quality Engine for Lumina.
    Transforms Lumina from rule-based to evidence-based by auditing market outcomes
    for both approved and rejected trading decisions over time.
    
    Metrics tracked:
      - False Rejection Rate (Rejected trade would have hit TP / been profitable)
      - False Approval Rate (Approved trade hit SL / was unprofitable)
      - Precision per Guard (MTF Guard, Macro Guard, Meta Reasoning, Liquidity Guard)
      - Correct Rejection Rate (True Negative)
      - Correct Approval Rate (True Positive)
    """
    _instance: Optional['DecisionQualityEngine'] = None

    @classmethod
    def get_instance(cls) -> 'DecisionQualityEngine':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        # signal_id -> decision audit record
        self._audit_records: Dict[str, Dict[str, Any]] = {}
        # Guard -> {"true_positives": int, "false_positives": int, "true_negatives": int, "false_negatives": int}
        self._guard_stats: Dict[str, Dict[str, int]] = {
            "MacroGuard": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
            "MTFGuard": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
            "VolumeLiquidityGuard": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
            "MetaReasoning": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
            "SelfAdaptation": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
            "ConfidenceFilter": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
            "PortfolioGuard": {"tp": 0, "fp": 0, "tn": 0, "fn": 0},
        }
        self._register_subscribers()

    def _register_subscribers(self):
        try:
            from app.trading.decision_context import register_approval_subscriber, register_rejection_subscriber
            register_approval_subscriber(self.record_approval)
            register_rejection_subscriber(self.record_rejection)
            logger.info("DecisionQualityEngine registered to DecisionContext event bus.")
        except Exception as e:
            logger.error(f"Failed to register DecisionQualityEngine subscribers: {e}")

    def record_approval(self, ctx):
        """Records an approved trade decision for subsequent outcome evaluation."""
        self._audit_records[ctx.signal_id] = {
            "signal_id": ctx.signal_id,
            "timestamp": ctx.timestamp.isoformat(),
            "symbol": ctx.symbol,
            "side": ctx.side,
            "price": ctx.price,
            "strategy": ctx.active_strategy,
            "decision": "APPROVED",
            "stage": "APPROVED",
            "regime": ctx.eval_cache.get("macro_risk", {}).get("risk_level", "NORMAL"),
            "eval_cache": ctx.eval_cache,
            "outcome_evaluated": False,
            "verdict": None,  # "TRUE_POSITIVE" or "FALSE_POSITIVE"
            "pnl": 0.0
        }
        logger.debug(f"📊 [DECISION QUALITY ENGINE] Tracked APPROVED decision for {ctx.signal_id}")

    def record_rejection(self, ctx):
        """Records a rejected trade decision for subsequent counterfactual evaluation."""
        stage = ctx.rejection_stage or "UNKNOWN"
        self._audit_records[ctx.signal_id] = {
            "signal_id": ctx.signal_id,
            "timestamp": ctx.timestamp.isoformat(),
            "symbol": ctx.symbol,
            "side": ctx.side,
            "price": ctx.price,
            "strategy": ctx.active_strategy,
            "decision": "REJECTED",
            "stage": stage,
            "reason": ctx.rejection_reason,
            "category": ctx.rejection_category,
            "outcome_evaluated": False,
            "verdict": None,  # "TRUE_NEGATIVE" or "FALSE_NEGATIVE"
            "pnl": 0.0
        }
        logger.debug(f"📊 [DECISION QUALITY ENGINE] Tracked REJECTED decision for {ctx.signal_id} at [{stage}]")

    def evaluate_outcome(self, signal_id: str, pnl: float, hit_tp: bool, hit_sl: bool, counterfactual: bool = False):
        """
        Audits the eventual market outcome for a decision.
        For Approved trades:
          - pnl > 0 or hit_tp -> True Positive (Correct Approval)
          - pnl <= 0 or hit_sl -> False Positive (False Approval)
        For Rejected trades (counterfactual price tracking):
          - would have hit TP / profitable -> False Negative (False Rejection)
          - hit SL / lost -> True Negative (Correct Rejection)
        """
        record = self._audit_records.get(signal_id)
        if not record or record["outcome_evaluated"]:
            return

        decision = record["decision"]
        stage = record["stage"]
        record["pnl"] = pnl
        record["outcome_evaluated"] = True

        if decision == "APPROVED":
            if pnl > 0 or hit_tp:
                record["verdict"] = "TRUE_POSITIVE"
                self._update_guard_stats("ALL_APPROVED", tp=1)
            else:
                record["verdict"] = "FALSE_POSITIVE"
                self._update_guard_stats("ALL_APPROVED", fp=1)
        elif decision == "REJECTED":
            if counterfactual and (pnl > 0 or hit_tp):
                record["verdict"] = "FALSE_NEGATIVE"  # False rejection
                self._update_guard_stats(stage, fn=1)
            else:
                record["verdict"] = "TRUE_NEGATIVE"  # Correct rejection
                self._update_guard_stats(stage, tn=1)

        logger.info(f"🎯 [DECISION QUALITY VERDICT] Signal {signal_id} ({decision} at {stage}) -> Verdict: {record['verdict']}")

    def _update_guard_stats(self, guard: str, tp: int = 0, fp: int = 0, tn: int = 0, fn: int = 0):
        if guard not in self._guard_stats:
            self._guard_stats[guard] = {"tp": 0, "fp": 0, "tn": 0, "fn": 0}
        s = self._guard_stats[guard]
        s["tp"] += tp
        s["fp"] += fp
        s["tn"] += tn
        s["fn"] += fn

    def get_guard_precision(self, guard_name: str) -> Dict[str, float]:
        """Calculates precision, recall, and error rates for a specific pipeline guard."""
        s = self._guard_stats.get(guard_name, {"tp": 0, "fp": 0, "tn": 0, "fn": 0})
        tp, fp, tn, fn = s["tp"], s["fp"], s["tn"], s["fn"]
        
        precision = (tp / (tp + fp)) if (tp + fp) > 0 else 1.0
        rejection_accuracy = (tn / (tn + fn)) if (tn + fn) > 0 else 1.0
        false_rejection_rate = (fn / (tn + fn)) if (tn + fn) > 0 else 0.0
        false_approval_rate = (fp / (tp + fp)) if (tp + fp) > 0 else 0.0

        return {
            "guard_name": guard_name,
            "precision": round(precision, 4),
            "rejection_accuracy": round(rejection_accuracy, 4),
            "false_rejection_rate": round(false_rejection_rate, 4),
            "false_approval_rate": round(false_approval_rate, 4),
            "total_evaluations": tp + fp + tn + fn
        }

    def get_system_decision_metrics(self) -> Dict[str, Any]:
        """Returns comprehensive decision quality metrics across the entire pipeline."""
        total_records = len(self._audit_records)
        evaluated = [r for r in self._audit_records.values() if r["outcome_evaluated"]]
        
        tp = sum(1 for r in evaluated if r["verdict"] == "TRUE_POSITIVE")
        fp = sum(1 for r in evaluated if r["verdict"] == "FALSE_POSITIVE")
        tn = sum(1 for r in evaluated if r["verdict"] == "TRUE_NEGATIVE")
        fn = sum(1 for r in evaluated if r["verdict"] == "FALSE_NEGATIVE")

        precision = (tp / (tp + fp)) if (tp + fp) > 0 else 0.0
        false_rejection_rate = (fn / (tn + fn)) if (tn + fn) > 0 else 0.0

        guards_summary = {g: self.get_guard_precision(g) for g in self._guard_stats}

        return {
            "total_tracked_signals": total_records,
            "total_evaluated_outcomes": len(evaluated),
            "true_positives": tp,
            "false_positives": fp,
            "true_negatives": tn,
            "false_negatives": fn,
            "system_approval_precision": round(precision, 4),
            "system_false_rejection_rate": round(false_rejection_rate, 4),
            "guard_metrics": guards_summary
        }
