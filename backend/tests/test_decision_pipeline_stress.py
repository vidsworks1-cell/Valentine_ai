import unittest
import asyncio
from app.trading.decision_context import DecisionContext, register_rejection_subscriber, register_approval_subscriber
from app.trading.decision_quality import DecisionQualityEngine
from app.trading.adaptive_guard_thresholds import AdaptiveGuardThresholds
from app.trading.portfolio_scheduler import PortfolioOpportunityScheduler
from app.ai.causal_learning import CausalLearningEngine
from app.ai.tri_brain_architecture import TriBrainArchitecture

class TestDecisionPipelineStress(unittest.IsolatedAsyncioTestCase):

    async def test_decision_context_immutability(self):
        ctx = DecisionContext(symbol="BTCUSDT", side="BUY", price=50000.0, original_strategy="Breakout Momentum")
        self.assertFalse(ctx.is_locked)
        ctx.symbol = "BTCUSDT"
        
        # Lock decision
        ctx.lock_decision()
        self.assertTrue(ctx.is_locked)

        # Attempting to mutate locked fields must raise AttributeError
        with self.assertRaises(AttributeError):
            ctx.price = 51000.0

        with self.assertRaises(AttributeError):
            ctx.symbol = "ETHUSDT"

    async def test_single_rejection_event_lifecycle(self):
        rejection_events = []
        def sub(context):
            rejection_events.append(context)

        register_rejection_subscriber(sub)
        
        ctx = DecisionContext(symbol="ETHUSDT", side="SELL", price=3000.0, original_strategy="Mean Reversion")
        ctx.record_rejection("MacroGuard", "High Macro Risk Lockdown", category="MACRO_RISK")
        
        # Second rejection call should be a no-op
        ctx.record_rejection("MTFGuard", "MTF Disagreement", category="MTF")

        self.assertTrue(ctx.is_rejected)
        self.assertEqual(ctx.rejection_stage, "MacroGuard")
        self.assertEqual(len([e for e in rejection_events if e.signal_id == ctx.signal_id]), 1)

    async def test_pipeline_integrity_verification(self):
        ctx = DecisionContext(symbol="SOLUSDT", side="BUY", price=150.0, original_strategy="Breakout Momentum")
        
        # Before lock, integrity fails
        is_valid, reason = ctx.verify_pipeline_integrity()
        self.assertFalse(is_valid)
        self.assertIn("not locked", reason)

        # Add traces and lock
        ctx.add_trace("MacroGuard", "PASS")
        ctx.add_trace("MTFGuard", "PASS")
        ctx.add_trace("VolumeLiquidityGuard", "PASS")
        ctx.add_trace("MetaReasoning", "PASS")
        ctx.lock_decision()

        is_valid, reason = ctx.verify_pipeline_integrity()
        self.assertTrue(is_valid)
        self.assertEqual(reason, "Pipeline Integrity Verified")

    async def test_adaptive_thresholds_and_tri_brain(self):
        adaptive = AdaptiveGuardThresholds.get_instance()
        volatile_mtf = adaptive.get_effective_threshold("mtf_alignment_pct", "VOLATILE")
        ranging_mtf = adaptive.get_effective_threshold("mtf_alignment_pct", "RANGING")

        self.assertEqual(volatile_mtf, 61.0)
        self.assertEqual(ranging_mtf, 74.0)

        tri_brain = TriBrainArchitecture.get_instance()
        summary = tri_brain.learning_brain.get_system_intelligence_summary()
        self.assertIn("decision_quality", summary)
        self.assertIn("learned_regime_thresholds", summary)

if __name__ == "__main__":
    unittest.main()
