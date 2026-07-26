import unittest
import asyncio
from app.ai.feature_pruning import FeatureWeightPruningEngine


class TestFeatureWeightSelfPruning(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Initialize feature pruning engine."""
        self.engine = FeatureWeightPruningEngine.get_instance()

    async def test_initial_feature_structure_and_normalization(self):
        """Verify feature registry loads correctly with normalized weights summing to 1.0."""
        report = self.engine.get_pruning_report()

        self.assertEqual(report["total_features"], 6)
        self.assertIn("features", report)
        self.assertIn("normalized_weights", report)

        weights = report["normalized_weights"]
        total_weight = sum(weights.values())
        self.assertAlmostEqual(total_weight, 1.0, places=2)

    async def test_evaluate_and_prune_features_execution(self):
        """Verify feature evaluation updates metrics and applies rebalancing rules."""
        report = await self.engine.evaluate_and_prune_features()

        self.assertIn("top_predictive_feature", report)
        self.assertIn("active_features_count", report)
        self.assertIn("attenuated_features_count", report)
        self.assertIn("pruned_features_count", report)

        top_feature = report["top_predictive_feature"]
        self.assertIsNotNone(top_feature["key"])
        self.assertGreater(top_feature["importance_score"], 0.0)

    async def test_pruning_rule_attenuation(self):
        """Verify features with low Information Gain get ATTENUATED or PRUNED."""
        # Manually lower time_session_seasonality metrics
        self.engine._features["time_session_seasonality"]["information_gain"] = 0.05
        self.engine._features["time_session_seasonality"]["win_correlation"] = 0.01
        self.engine._features["time_session_seasonality"]["psnr_db"] = 1.1

        pruned, attenuated, active = self.engine._rebalance_weights()
        seasonality_feat = self.engine._features["time_session_seasonality"]

        self.assertEqual(seasonality_feat["status"], "PRUNED")
        self.assertEqual(seasonality_feat["normalized_weight"], 0.0)

    async def test_weight_redistribution_to_active_features(self):
        """Verify pruned weight allocation is absorbed by active features to maintain 1.0 total sum."""
        weights = self.engine.get_pruned_weights()
        total = sum(weights.values())
        self.assertAlmostEqual(total, 1.0, places=3)


if __name__ == "__main__":
    unittest.main()
