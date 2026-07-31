import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("lumina.ai.tri_brain_architecture")

class ExecutionBrain:
    """
    Execution Brain Layer.
    Evaluates individual trading signals through deterministic fast guards,
    meta-reasoning second brain debate, and self-adaptation filters.
    """
    def __init__(self):
        from app.ai.reasoning import AiReasoningEngine
        self.engine = AiReasoningEngine.get_instance()

    async def scan_and_evaluate(self, raw_signals: List[Dict[str, Any]]):
        """Passes raw scanner signals through the two-phase decision pipeline."""
        return await self.engine.detect_high_probability_setups()

class PortfolioBrain:
    """
    Portfolio Brain Layer.
    Acts as the institutional capital allocator: schedules opportunities, ranks by EV,
    manages portfolio-wide exposure, and dispatches executions to OrderManager.
    """
    def __init__(self):
        from app.trading.portfolio_scheduler import PortfolioOpportunityScheduler
        from app.trading.order_manager import OrderManager
        from app.trading.position_manager import PositionManager
        self.scheduler = PortfolioOpportunityScheduler.get_instance()
        self.order_manager = OrderManager.get_instance()
        self.position_manager = PositionManager.get_instance()

    def schedule_and_allocate(self, approved_setup: Dict[str, Any]):
        """Queues and ranks approved setups before execution."""
        self.scheduler.queue_opportunity(approved_setup)
        allocated = self.scheduler.rank_and_allocate(max_allocations=3)
        return allocated

class LearningBrain:
    """
    Learning Brain Layer.
    Continuously audits decision quality, measures guard precision, recalibrates dynamic
    thresholds per market regime, and discovers root cause causal rules.
    """
    def __init__(self):
        from app.trading.decision_quality import DecisionQualityEngine
        from app.trading.adaptive_guard_thresholds import AdaptiveGuardThresholds
        from app.ai.causal_learning import CausalLearningEngine
        from app.ai.self_adaptation import SelfAdaptationEngine
        self.quality_engine = DecisionQualityEngine.get_instance()
        self.adaptive_thresholds = AdaptiveGuardThresholds.get_instance()
        self.causal_engine = CausalLearningEngine.get_instance()
        self.self_adaptation = SelfAdaptationEngine.get_instance()

    def get_system_intelligence_summary(self) -> Dict[str, Any]:
        """Provides a high-level summary of Lumina's learning metrics and decision quality."""
        return {
            "decision_quality": self.quality_engine.get_system_decision_metrics(),
            "causal_rules": self.causal_engine.get_discovered_causal_rules(),
            "learned_regime_thresholds": {
                "VOLATILE": self.adaptive_thresholds.get_effective_threshold("mtf_alignment_pct", "VOLATILE"),
                "RANGING": self.adaptive_thresholds.get_effective_threshold("mtf_alignment_pct", "RANGING"),
                "TRENDING": self.adaptive_thresholds.get_effective_threshold("mtf_alignment_pct", "TRENDING")
            }
        }

class TriBrainArchitecture:
    """
    Tri-Brain Master Facade for Lumina Architecture:
      1. Execution Brain (Signal Scan, Fast Guards, Meta Debate)
      2. Portfolio Brain (Capital Allocation, Scheduler, Portfolio Risk)
      3. Learning Brain (Decision Quality, Adaptive Thresholds, Causal Learning)
    """
    _instance: Optional['TriBrainArchitecture'] = None

    @classmethod
    def get_instance(cls) -> 'TriBrainArchitecture':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        self.execution_brain = ExecutionBrain()
        self.portfolio_brain = PortfolioBrain()
        self.learning_brain = LearningBrain()
        logger.info("🧠⚡ Lumina Tri-Brain Architecture (Execution | Portfolio | Learning) initialized.")
