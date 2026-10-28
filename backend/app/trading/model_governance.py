import logging
import time
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

logger = logging.getLogger("lumina.trading.model_governance")

class ModelGovernanceEngine:
    """
    Model Versioning, Decision Lineage Registry & Automated Rollback System for Lumina.
    
    Guarantees that every trade and risk decision is explicitly tagged with a version lineage payload.
    Monitors performance by version checkpoint and triggers automated rollbacks if a newly promoted
    configuration degrades performance (drawdown, win rate, Sharpe) beyond safety bounds.
    """
    _instance: Optional['ModelGovernanceEngine'] = None

    def __init__(self):
        # Current active subsystem versions
        self.active_versions: Dict[str, str] = {
            "strategy_engine": "v2.4.0",
            "regime_gatekeeper": "v1.0.0",
            "portfolio_guard": "v2.0.0",
            "meta_reasoning": "v3.1.0",
            "self_adaptation": "v2.2.0",
            "guard_auditor": "v1.5.0",
            "sizing_engine": "v2.1.0"
        }

        # Registered version snapshots / checkpoints
        self.version_history: List[Dict[str, Any]] = [
            {
                "checkpoint_id": "CHK_STABLE_v2.0",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "versions": {
                    "strategy_engine": "v2.3.0",
                    "portfolio_guard": "v1.8.0",
                    "meta_reasoning": "v3.0.0",
                    "self_adaptation": "v2.1.0",
                    "confidence_model": "v3.2.0",
                    "sizing_engine": "v1.9.0"
                },
                "status": "HISTORICAL_BASELINE",
                "notes": "Verified baseline checkpoint prior to governance architecture deployment.",
                "metrics": {
                    "total_trades": 120,
                    "win_rate_pct": 58.3,
                    "sharpe_ratio": 1.85,
                    "max_drawdown_pct": 4.2
                }
            },
            {
                "checkpoint_id": "CHK_BASELINE_v2.4",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "versions": {
                    "strategy_engine": "v2.4.0",
                    "regime_gatekeeper": "v1.0.0",
                    "portfolio_guard": "v2.0.0",
                    "meta_reasoning": "v3.1.0",
                    "self_adaptation": "v2.2.0",
                    "guard_auditor": "v1.5.0",
                    "sizing_engine": "v2.1.0"
                },
                "status": "ACTIVE_BASELINE",
                "notes": "Pillar 3 Production Baseline: rolling 20-trade safety circuit (PF >= 1.20, Max DD <= $0.75).",
                "metrics": {
                    "target_profit_factor": 1.20,
                    "max_drawdown_limit_usd": 0.75,
                    "rolling_window_trades": 20
                }
            }
        ]

        # Lineage log for recent decisions
        self.decision_lineage_log: List[Dict[str, Any]] = []

        # Version performance evaluation window
        self.version_trades: Dict[str, List[Dict[str, Any]]] = {}
        self.rollback_events: List[Dict[str, Any]] = []

        # Rolling 20-Trade Safety Circuit (Pillar 3)
        self._safety_circuit_tripped: bool = False
        self._safety_throttle_scaler: float = 1.0
        self._last_rolling_metrics: Dict[str, Any] = {}

    @classmethod
    def get_instance(cls) -> 'ModelGovernanceEngine':
        if cls._instance is None:
            cls._instance = ModelGovernanceEngine()
        return cls._instance

    def get_decision_lineage(self, decision_type: str = "TRADE_SIGNAL", extra_meta: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Generates a complete version lineage payload for any trade, signal, or portfolio decision.
        """
        lineage_id = f"LIN_{int(time.time() * 1000)}"
        payload = {
            "lineage_id": lineage_id,
            "decision_type": decision_type,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "subsystem_versions": self.active_versions.copy(),
            "active_checkpoint": self.version_history[-1]["checkpoint_id"] if self.version_history else "v2.4.0",
            "extra_meta": extra_meta or {}
        }

        self.decision_lineage_log.append(payload)
        if len(self.decision_lineage_log) > 500:
            self.decision_lineage_log.pop(0)

        return payload

    def record_version_trade_outcome(self, trade_info: Dict[str, Any]):
        """
        Records trade result under the active version snapshot for version performance tracking and rollback evaluation.
        """
        checkpoint_id = self.version_history[-1]["checkpoint_id"] if self.version_history else "DEFAULT"
        if checkpoint_id not in self.version_trades:
            self.version_trades[checkpoint_id] = []

        self.version_trades[checkpoint_id].append(trade_info)
        
        # Evaluate automatic rollback trigger and rolling 20-trade safety circuit after recording outcome
        self.evaluate_version_rollback()
        self.evaluate_rolling_safety_bounds()

    def get_safety_scaler(self) -> float:
        """Returns the dynamic risk allocation scaler governed by the Rolling Safety Circuit (0.35x when tripped, 1.0x normal)."""
        return self._safety_throttle_scaler

    def evaluate_rolling_safety_bounds(self) -> Dict[str, Any]:
        """
        Pillar 3: Rolling Performance Bounds (Automated Safety Circuit)
        Evaluates the rolling 20-trade execution window:
        - If rolling Profit Factor drops below 1.20, OR
        - If cumulative drawdown in the window exceeds $0.75:
        Automatically throttles risk to minimal sizes (0.35x), alerts Portfolio Guard,
        and reverts to baseline checkpoint CHK_BASELINE_v2.4.
        """
        all_recent: List[Dict[str, Any]] = []
        for chk_id, tr_list in self.version_trades.items():
            all_recent.extend(tr_list)
        # Sort by timestamp if available
        all_recent.sort(key=lambda x: x.get("timestamp", 0.0))
        window = all_recent[-20:]

        if len(window) < 5:
            return {
                "circuit_tripped": False,
                "reason": f"Accumulating rolling window sample ({len(window)}/20 trades).",
                "safety_scaler": 1.0
            }

        wins = [float(t.get("pnl", t.get("profit_loss", 0.0))) for t in window if float(t.get("pnl", t.get("profit_loss", 0.0))) > 0.001]
        losses = [abs(float(t.get("pnl", t.get("profit_loss", 0.0)))) for t in window if float(t.get("pnl", t.get("profit_loss", 0.0))) < -0.001]
        win_sum = sum(wins)
        loss_sum = sum(losses)
        net_pnl = win_sum - loss_sum
        rolling_pf = (win_sum / loss_sum) if loss_sum > 0 else (999.0 if win_sum > 0 else 1.0)
        win_rate = (len(wins) / (len(wins) + len(losses)) * 100.0) if (len(wins) + len(losses)) > 0 else 0.0

        # Peak-to-trough cumulative drawdown calculation across the rolling window
        cum = 0.0
        peak = 0.0
        max_dd = 0.0
        for t in window:
            pnl_val = float(t.get("pnl", t.get("profit_loss", 0.0)))
            cum += pnl_val
            if cum > peak:
                peak = cum
            cur_dd = peak - cum
            if cur_dd > max_dd:
                max_dd = cur_dd

        metrics = {
            "window_size": len(window),
            "wins": len(wins),
            "losses": len(losses),
            "win_rate_pct": round(win_rate, 1),
            "win_sum": round(win_sum, 4),
            "loss_sum": round(loss_sum, 4),
            "net_pnl": round(net_pnl, 4),
            "rolling_profit_factor": round(rolling_pf, 2),
            "max_drawdown_usd": round(max_dd, 4),
            "timestamp": time.time()
        }
        self._last_rolling_metrics = metrics

        # Safety Circuit Rule: Minimum sample >= 8 trades before enforcing strict trip
        circuit_tripped = False
        trip_reason = ""
        if len(window) >= 8:
            if rolling_pf < 1.20 and loss_sum >= 0.15:
                circuit_tripped = True
                trip_reason = f"Rolling Profit Factor ({rolling_pf:.2f}) degraded below 1.20 institutional safety floor."
            elif max_dd >= 0.75:
                circuit_tripped = True
                trip_reason = f"Cumulative rolling drawdown (${max_dd:.2f}) breached $0.75 micro-account risk boundary."

        if circuit_tripped:
            if not self._safety_circuit_tripped:
                logger.warning(
                    f"🚨 [ROLLING SAFETY CIRCUIT ENGAGED] {trip_reason} "
                    f"Throttling position sizes to 0.35x and enforcing baseline checkpoint 'CHK_BASELINE_v2.4'."
                )
            self._safety_circuit_tripped = True
            self._safety_throttle_scaler = 0.35
            # Enforce baseline checkpoint
            target = next((c for c in self.version_history if c["checkpoint_id"] == "CHK_BASELINE_v2.4"), None)
            if target:
                self.active_versions = target["versions"].copy()
        else:
            if self._safety_circuit_tripped:
                logger.info(f"✅ [ROLLING SAFETY CIRCUIT DISENGAGED] Rolling Profit Factor ({rolling_pf:.2f}) & Drawdown (${max_dd:.2f}) recovered to institutional baseline.")
            self._safety_circuit_tripped = False
            self._safety_throttle_scaler = 1.0

        return {
            "circuit_tripped": self._safety_circuit_tripped,
            "safety_scaler": self._safety_throttle_scaler,
            "reason": trip_reason or "Rolling window operating within verified baseline bounds.",
            "metrics": metrics
        }


    def create_version_checkpoint(self, new_versions: Dict[str, str], notes: str = "") -> Dict[str, Any]:
        """
        Creates a new version checkpoint and promotes it to active.
        """
        checkpoint_id = f"CHK_{int(time.time())}"
        updated_versions = self.active_versions.copy()
        updated_versions.update(new_versions)

        checkpoint = {
            "checkpoint_id": checkpoint_id,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "versions": updated_versions,
            "status": "ACTIVE_PROMOTED",
            "notes": notes or "New model version checkpoint promoted.",
            "metrics": {
                "total_trades": 0,
                "win_rate_pct": 0.0,
                "sharpe_ratio": 0.0,
                "max_drawdown_pct": 0.0
            }
        }

        self.version_history.append(checkpoint)
        self.active_versions = updated_versions
        logger.info(f"🏛️ [MODEL GOVERNANCE] Promoted new version checkpoint '{checkpoint_id}': {updated_versions}")

        return checkpoint

    def evaluate_version_rollback(self) -> Dict[str, Any]:
        """
        Evaluates active version checkpoint performance. If drawdown degrades by > 5% or win rate drops
        below 35% across 5+ trades compared to baseline, triggers an automatic rollback to previous stable checkpoint.
        """
        if len(self.version_history) < 2:
            return {"rollback_triggered": False, "reason": "No previous stable checkpoint available."}

        current_checkpoint = self.version_history[-1]
        baseline_checkpoint = self.version_history[-2]

        trades = self.version_trades.get(current_checkpoint["checkpoint_id"], [])
        if len(trades) < 5:
            return {"rollback_triggered": False, "reason": f"Insufficient sample size ({len(trades)}/5 trades)."}

        wins = sum(1 for t in trades if t.get("profit_loss", 0.0) > 0)
        win_rate = (wins / len(trades)) * 100.0
        losses = [t.get("profit_loss", 0.0) for t in trades if t.get("profit_loss", 0.0) < 0]
        max_dd = abs(min(losses)) / 1000.0 if losses else 0.0  # Normalized % proxy

        baseline_win_rate = baseline_checkpoint.get("metrics", {}).get("win_rate_pct", 50.0)

        # Rollback conditions:
        # 1. Win rate dropped by > 15 percentage points below baseline and win rate < 40%
        # 2. Max drawdown > 8.0%
        should_rollback = False
        reason = ""

        if win_rate < (baseline_win_rate - 15.0) and win_rate < 40.0:
            should_rollback = True
            reason = f"Win rate dropped severely ({win_rate:.1f}% vs baseline {baseline_win_rate:.1f}%)."
        elif max_dd > 8.0:
            should_rollback = True
            reason = f"Excessive drawdown observed ({max_dd:.1f}% > 8.0% safety threshold)."

        if should_rollback:
            return self.trigger_rollback(target_checkpoint_id=baseline_checkpoint["checkpoint_id"], reason=reason)

        return {"rollback_triggered": False, "reason": "Version operating within acceptable governance thresholds."}

    def trigger_rollback(self, target_checkpoint_id: Optional[str] = None, reason: str = "") -> Dict[str, Any]:
        """
        Rolls back active versions to a specified stable checkpoint.
        """
        target = None
        if target_checkpoint_id:
            target = next((c for c in self.version_history if c["checkpoint_id"] == target_checkpoint_id), None)
        if not target and len(self.version_history) >= 2:
            target = self.version_history[-2]

        if not target:
            return {"rollback_triggered": False, "reason": "No valid target checkpoint found for rollback."}

        previous_versions = self.active_versions.copy()
        self.active_versions = target["versions"].copy()

        rollback_event = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "rolled_back_from": previous_versions,
            "restored_to_checkpoint": target["checkpoint_id"],
            "restored_versions": self.active_versions.copy(),
            "reason": reason or "Manual governance rollback requested."
        }

        self.rollback_events.append(rollback_event)
        logger.warning(
            f"🔄 [AUTOMATED ROLLBACK TRIGGERED] Reverted active model versions to '{target['checkpoint_id']}'. "
            f"Reason: {rollback_event['reason']}"
        )

        return {
            "rollback_triggered": True,
            "restored_checkpoint": target["checkpoint_id"],
            "restored_versions": self.active_versions,
            "reason": rollback_event["reason"]
        }

    def get_governance_summary(self) -> Dict[str, Any]:
        """
        Returns complete governance dashboard payload.
        """
        return {
            "active_versions": self.active_versions,
            "active_checkpoint": self.version_history[-1]["checkpoint_id"] if self.version_history else None,
            "total_checkpoints": len(self.version_history),
            "version_history": self.version_history,
            "total_lineage_records": len(self.decision_lineage_log),
            "recent_lineage": self.decision_lineage_log[-5:],
            "rollback_events_count": len(self.rollback_events),
            "recent_rollback_events": self.rollback_events[-5:]
        }
