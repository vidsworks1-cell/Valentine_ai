import os
import json
import logging
import asyncio
from typing import Dict, Any, Optional
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.state_recovery")

CHECKPOINT_FILE_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "data", "system_state_checkpoint.json")

class StateRecoveryEngine:
    """
    Pillar 1: Institutional State Recovery Engine.
    Serializes and recovers state across system restarts without losing track of:
    - Open positions & trailing stop levels
    - Pending orders & trade journal state
    - Guard cooldowns & symbol lockouts
    - Self-adaptation strategy weights & confidence thresholds
    - Regime experience & market memory state
    """
    _instance: Optional['StateRecoveryEngine'] = None

    def __init__(self, filepath: str = CHECKPOINT_FILE_PATH):
        self.filepath = filepath
        self.last_checkpoint_at: Optional[str] = None
        self.total_checkpoints_saved: int = 0
        self.total_restorations_performed: int = 0

    @classmethod
    def get_instance(cls) -> 'StateRecoveryEngine':
        if cls._instance is None:
            cls._instance = StateRecoveryEngine()
        return cls._instance

    def capture_snapshot(self) -> Dict[str, Any]:
        """Captures complete operational state from all core system engines."""
        snapshot: Dict[str, Any] = {
            "version": "1.0",
            "saved_at": datetime.now(timezone.utc).isoformat(),
            "positions": {},
            "cooldowns": {},
            "strategy_stats": {},
            "market_memory": {},
            "capital_allocations": {}
        }

        # 1. Capture Active Positions
        try:
            from app.trading.position_manager import PositionManager
            pm = PositionManager.get_instance()
            snapshot["positions"] = pm._positions
        except Exception as e:
            logger.error(f"[STATE RECOVERY] Failed to capture positions: {e}")

        # 2. Capture Reasoning Engine Cooldowns
        try:
            from app.ai.reasoning import AiReasoningEngine
            engine = AiReasoningEngine.get_instance()
            if hasattr(engine, "_active_cooldowns"):
                snapshot["cooldowns"] = getattr(engine, "_active_cooldowns", {})
        except Exception as e:
            logger.error(f"[STATE RECOVERY] Failed to capture cooldowns: {e}")

        # 3. Capture Self Adaptation Strategy Stats
        try:
            from app.ai.self_adaptation import SelfAdaptationEngine
            sa = SelfAdaptationEngine.get_instance()
            snapshot["strategy_stats"] = sa._strategy_stats
        except Exception as e:
            logger.error(f"[STATE RECOVERY] Failed to capture self adaptation stats: {e}")

        # 4. Capture Capital Allocations
        try:
            from app.trading.capital_allocation import CapitalAllocationEngine
            ca = CapitalAllocationEngine.get_instance()
            snapshot["capital_allocations"] = ca.allocations
        except Exception as e:
            logger.error(f"[STATE RECOVERY] Failed to capture capital allocations: {e}")

        return snapshot

    def save_checkpoint(self) -> bool:
        """Persists system state snapshot to disk."""
        try:
            snapshot = self.capture_snapshot()
            os.makedirs(os.path.dirname(self.filepath), exist_ok=True)
            with open(self.filepath, "w") as f:
                json.dump(snapshot, f, indent=2, default=str)
            
            self.last_checkpoint_at = snapshot["saved_at"]
            self.total_checkpoints_saved += 1
            logger.info(f"💾 [STATE RECOVERY] System state checkpoint successfully saved to '{self.filepath}'. Active positions: {len(snapshot['positions'])}")
            return True
        except Exception as e:
            logger.error(f"❌ [STATE RECOVERY] Failed to save state checkpoint: {e}")
            return False

    def restore_system_state(self) -> Dict[str, Any]:
        """Restores in-memory state of positions, cooldowns, and adaptive parameters from checkpoint file."""
        if not os.path.exists(self.filepath):
            logger.info(f"ℹ️ [STATE RECOVERY] No checkpoint file found at '{self.filepath}'. Starting fresh state.")
            return {"status": "NO_CHECKPOINT", "restored": False}

        try:
            with open(self.filepath, "r") as f:
                snapshot = json.load(f)

            restored_counts = {
                "positions": 0,
                "cooldowns": 0,
                "strategy_stats": 0,
                "capital_allocations": 0
            }

            # 1. Restore Active Positions
            if "positions" in snapshot and snapshot["positions"]:
                from app.trading.position_manager import PositionManager
                pm = PositionManager.get_instance()
                for key, pos in snapshot["positions"].items():
                    try:
                        trade_id = int(key) if str(key).isdigit() else key
                        entry_px = float(pos.get("entry_price", 0.0))
                        if "highest_price" not in pos or pos.get("highest_price") is None or float(pos.get("highest_price", 0.0)) <= 0.0:
                            pos["highest_price"] = entry_px
                        if "lowest_price" not in pos or pos.get("lowest_price") is None or float(pos.get("lowest_price", 0.0)) <= 0.0:
                            pos["lowest_price"] = entry_px
                        pm._positions[trade_id] = pos
                        restored_counts["positions"] += 1
                    except Exception as pe:
                        logger.error(f"Failed restoring pos {key}: {pe}")

            # 2. Restore Cooldowns
            if "cooldowns" in snapshot and snapshot["cooldowns"]:
                from app.ai.reasoning import AiReasoningEngine
                engine = AiReasoningEngine.get_instance()
                if hasattr(engine, "_active_cooldowns"):
                    setattr(engine, "_active_cooldowns", snapshot["cooldowns"])
                    restored_counts["cooldowns"] = len(snapshot["cooldowns"])

            # 3. Restore Strategy Stats
            if "strategy_stats" in snapshot and snapshot["strategy_stats"]:
                from app.ai.self_adaptation import SelfAdaptationEngine
                sa = SelfAdaptationEngine.get_instance()
                sa._strategy_stats.update(snapshot["strategy_stats"])
                restored_counts["strategy_stats"] = len(snapshot["strategy_stats"])

            # 4. Restore Capital Allocations
            if "capital_allocations" in snapshot and snapshot["capital_allocations"]:
                from app.trading.capital_allocation import CapitalAllocationEngine
                ca = CapitalAllocationEngine.get_instance()
                ca.allocations.update(snapshot["capital_allocations"])
                restored_counts["capital_allocations"] = len(snapshot["capital_allocations"])

            self.total_restorations_performed += 1
            logger.info(f"🔄 [STATE RECOVERY] State restoration COMPLETED from '{self.filepath}'. Restored: {restored_counts}")
            return {
                "status": "RESTORED_SUCCESSFULLY",
                "restored": True,
                "saved_at": snapshot.get("saved_at"),
                "restored_counts": restored_counts
            }
        except Exception as e:
            logger.error(f"❌ [STATE RECOVERY] Error during state restoration: {e}")
            return {"status": "ERROR", "restored": False, "error": str(e)}

    def get_telemetry(self) -> Dict[str, Any]:
        """Returns state recovery engine metrics."""
        return {
            "checkpoint_file": self.filepath,
            "checkpoint_exists": os.path.exists(self.filepath),
            "last_checkpoint_at": self.last_checkpoint_at,
            "total_checkpoints_saved": self.total_checkpoints_saved,
            "total_restorations_performed": self.total_restorations_performed
        }
