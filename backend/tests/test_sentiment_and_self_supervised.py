import unittest
import asyncio
from app.ai.sentiment_engine import MarketSentimentEngine
from app.ai.self_supervised_learning import SelfSupervisedLearningEngine
from app.workers.price_cache import PriceCache


class TestSentimentAndSelfSupervisedEngine(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Setup price cache state."""
        self.cache = await PriceCache.get_instance()
        self.cache.update_price("BTCUSDT", 92500.0)
        self.sentiment_engine = MarketSentimentEngine.get_instance()
        self.ssl_engine = SelfSupervisedLearningEngine.get_instance()

    async def test_sentiment_analysis_structure(self):
        """Verify sentiment engine returns composite sentiment scores and headline distributions."""
        data = await self.sentiment_engine.analyze_sentiment("BTCUSDT")

        self.assertEqual(data["symbol"], "BTCUSDT")
        self.assertIn("composite_sentiment_score", data)
        self.assertIn("sentiment_regime", data)
        self.assertIn("social_volume_surge_ratio", data)
        self.assertIn("fear_and_greed_index", data)
        self.assertIn("distribution", data)
        self.assertIn("recent_headlines", data)

        dist = data["distribution"]
        total_pct = dist["positive_pct"] + dist["neutral_pct"] + dist["negative_pct"]
        self.assertAlmostEqual(total_pct, 100.0, places=1)

    async def test_self_supervised_vector_projection_and_anomaly_detection(self):
        """Verify state vector projection into contrastive embedding space and similarity matching."""
        result = await self.ssl_engine.extract_and_project_market_state("BTCUSDT")

        self.assertEqual(result["symbol"], "BTCUSDT")
        self.assertEqual(len(result["state_vector"]), 6)
        self.assertIn("closest_learned_prototype", result)
        self.assertIn("prototype_similarity", result)
        self.assertIn("anomaly_score", result)
        self.assertIn("is_structural_anomaly", result)
        self.assertIn("model_metadata", result)

    async def test_contrastive_learning_epoch_execution(self):
        """Verify unsupervised training epoch updates contrastive loss and sample count."""
        initial_samples = self.ssl_engine._trained_samples_count
        res = await self.ssl_engine.train_contrastive_epoch()

        self.assertTrue(res["epoch_completed"])
        self.assertGreater(res["trained_samples_count"], initial_samples)
        self.assertLessEqual(res["contrastive_loss"], 0.20)


if __name__ == "__main__":
    unittest.main()
