import unittest
import asyncio
from app.ai.ppo_exit_policy import PPOExecutionExitPolicyAgent
from app.workers.price_cache import PriceCache


class TestPPOExecutionExitPolicyAgent(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize price cache and PPO agent instance."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.agent = PPOExecutionExitPolicyAgent.get_instance()

    async def test_ppo_position_evaluation_structure(self):
        """Verify PPO exit policy returns complete action distribution and guidance."""
        result = await self.agent.evaluate_position_exit_policy(
            symbol="BTCUSDT",
            side="BUY",
            entry_price=90000.0,
            holding_duration_bars=10,
            unrealized_pnl_pct=2.8,
            atr_volatility_ratio=1.1
        )

        self.assertEqual(result["symbol"], "BTCUSDT")
        self.assertEqual(result["side"], "BUY")
        self.assertIn("recommended_action", result)
        self.assertIn("action_probabilities", result)
        self.assertIn("state_vector", result)
        self.assertIn("state_value_estimate_v", result)
        self.assertIn("action_guidance", result)
        self.assertIn("ppo_model_metadata", result)

        probs = result["action_probabilities"]
        total_prob = sum(probs.values())
        self.assertAlmostEqual(total_prob, 1.0, places=2)

    async def test_ppo_action_mapping_bounds(self):
        """Verify recommended action maps to valid action space."""
        result = await self.agent.evaluate_position_exit_policy(
            symbol="ETHUSDT",
            side="SELL",
            entry_price=3600.0,
            holding_duration_bars=20,
            unrealized_pnl_pct=-6.5,
            atr_volatility_ratio=1.8
        )

        self.assertIn(result["recommended_action"], list(PPOExecutionExitPolicyAgent.ACTION_MAP.values()))
        self.assertGreaterEqual(result["action_confidence"], 0.0)
        self.assertLessEqual(result["action_confidence"], 1.0)

    async def test_ppo_training_step_execution(self):
        """Verify online PPO training step updates episode counts and losses."""
        initial_episodes = self.agent._total_episodes_trained
        train_res = await self.agent.train_ppo_step(num_episodes=100)

        self.assertTrue(train_res["train_step_completed"])
        self.assertEqual(train_res["episodes_added"], 100)
        self.assertEqual(train_res["total_episodes_trained"], initial_episodes + 100)
        self.assertIn("clipped_surrogate_objective_l_clip", train_res)


if __name__ == "__main__":
    unittest.main()
