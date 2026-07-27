import unittest
import time
import asyncio
from app.ai.reasoning import AiReasoningEngine
from app.trading.mtf_confirmation import MTFConfirmationGuard
from app.trading.position_manager import PositionManager

class TestLuminaStabilityImprovements(unittest.TestCase):

    def test_issue_1_cooldown_sanity_cap(self):
        """Verify Issue 1: Cooldown calculation sanity cap (>20% move rejected) and raw logging format."""
        engine = AiReasoningEngine.get_instance()
        engine.set_cooldown("SOLUSDT", "BUY", category="CONFIDENCE_FILTER", reason="Test filter", price=75.0)

        # 1. Test normal move < 1.5%: cooldown remains active
        in_cd, reason = engine.is_in_cooldown("SOLUSDT", "BUY", current_price=75.5) # 0.67% move
        self.assertTrue(in_cd)

        # 2. Test impossible jump > 20% (e.g. 56.94% as in prompt): cooldown remains active (rejected as sanity breach)
        in_cd, reason = engine.is_in_cooldown("SOLUSDT", "BUY", current_price=117.7) # 56.93% move
        self.assertTrue(in_cd, "Cooldown should be maintained when price jump exceeds 20.0% sanity cap!")

        # 3. Test valid move between 1.5% and 20%: cooldown cleared early
        in_cd, reason = engine.is_in_cooldown("SOLUSDT", "BUY", current_price=76.5) # 2.0% move
        self.assertFalse(in_cd, "Cooldown should be cleared early for valid move between 1.5% and 20%!")

    def test_issue_2_weighted_mtf(self):
        """Verify Issue 2 & 3: Timeframe weighting and evaluate_weighted_mtf_invalidation."""
        guard = MTFConfirmationGuard.get_instance()
        
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            strength, details = loop.run_until_complete(
                guard.evaluate_weighted_mtf_invalidation("BNBUSDT", "BUY", 573.36)
            )
            self.assertIn("formatted_breakdown", details)
            self.assertGreaterEqual(strength, 0.0)
            self.assertLessEqual(strength, 1.0)
            self.assertIn("1m", details["formatted_breakdown"])
            self.assertIn("5m", details["formatted_breakdown"])
            self.assertIn("15m", details["formatted_breakdown"])
            self.assertIn("1h", details["formatted_breakdown"])
        finally:
            loop.close()

if __name__ == "__main__":
    unittest.main()
