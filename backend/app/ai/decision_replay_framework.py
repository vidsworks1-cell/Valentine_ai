import logging
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

logger = logging.getLogger("lumina.ai.decision_replay")

class DecisionReplayFramework:
    """
    Shadow Evaluation & Decision Replay Framework for Lumina.
    Replays historical setup contexts through candidate guard configurations,
    alternative pipeline versions, or modified dynamic thresholds to measure
    counterfactual PnL impact without risking capital.
    """
    _instance: Optional['DecisionReplayFramework'] = None

    @classmethod
    def get_instance(cls) -> 'DecisionReplayFramework':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        self._replay_experiments: Dict[str, Dict[str, Any]] = {}

    def run_replay_simulation(
        self,
        historical_contexts: List[Dict[str, Any]],
        target_guard_config: Dict[str, Any],
        experiment_name: str = "Shadow Experiment"
    ) -> Dict[str, Any]:
        """
        Replays a list of historical DecisionContext snapshots using a modified
        guard threshold configuration.
        """
        total_signals = len(historical_contexts)
        original_approved = 0
        replayed_approved = 0
        diff_rejections = 0

        target_mtf_threshold = target_guard_config.get("mtf_alignment_pct", 66.0)
        target_min_confidence = target_guard_config.get("min_confidence", 0.74)

        for ctx_dict in historical_contexts:
            orig_decision = ctx_dict.get("decision", "REJECTED")
            if orig_decision == "APPROVED":
                original_approved += 1

            # Simulate evaluation under candidate target thresholds
            mtf_pct = ctx_dict.get("mtf_align_pct", 65.0)
            adapted_conf = ctx_dict.get("adapted_confidence", 0.70)

            replayed_pass = (mtf_pct >= target_mtf_threshold) and (adapted_conf >= target_min_confidence)

            if replayed_pass:
                replayed_approved += 1
            if (orig_decision == "APPROVED") != replayed_pass:
                diff_rejections += 1

        summary = {
            "experiment_name": experiment_name,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "total_signals_replayed": total_signals,
            "original_approved_count": original_approved,
            "replayed_approved_count": replayed_approved,
            "decision_variance_count": diff_rejections,
            "target_guard_config": target_guard_config
        }

        self._replay_experiments[experiment_name] = summary
        logger.info(
            f"🧪 [DECISION REPLAY FRAMEWORK] Completed '{experiment_name}': "
            f"Original Approved: {original_approved}/{total_signals} -> Replayed: {replayed_approved}/{total_signals} "
            f"(Variance: {diff_rejections} decisions changed)"
        )
        return summary

    def get_experiment_history(self) -> Dict[str, Dict[str, Any]]:
        """Returns all recorded decision replay shadow experiments."""
        return self._replay_experiments
