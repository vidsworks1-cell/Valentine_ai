import unittest
import asyncio
from app.ai.bayesian_belief import BayesianBeliefCalibrationEngine


class TestBayesianBeliefCalibrationEngine(unittest.TestCase):
    def setUp(self):
        """Initialize Bayesian Belief Calibration Engine."""
        self.engine = BayesianBeliefCalibrationEngine.get_instance()

    def test_bayesian_calibration_structure(self):
        """Verify calibration output structure and posterior calculation bounds."""
        signals = {
            "mtf_aligned": True,
            "rvol_surge": True,
            "squeeze_firing": True,
            "macro_risk_low": True,
            "sentiment_aligned": True,
            "liquidity_magnet_aligned": True,
            "is_structural_anomaly": False
        }

        res = self.engine.calibrate_belief("Breakout Momentum", signals)

        self.assertEqual(res["strategy"], "Breakout Momentum")
        self.assertEqual(res["prior_win_probability"], 0.58)
        self.assertIn("composite_likelihood_ratio", res)
        self.assertIn("posterior_win_probability", res)
        self.assertIn("calibrated_confidence", res)
        self.assertIn("epistemic_uncertainty", res)
        self.assertIn("credible_interval_95", res)

        post_p = res["posterior_win_probability"]
        self.assertGreater(post_p, res["prior_win_probability"])
        self.assertLessEqual(post_p, 1.0)

    def test_bayesian_calibration_negative_signals(self):
        """Verify negative signal evidence reduces posterior win probability."""
        signals = {
            "mtf_aligned": False,
            "rvol_surge": False,
            "squeeze_firing": False,
            "macro_risk_low": False,
            "sentiment_aligned": False,
            "liquidity_magnet_aligned": False,
            "is_structural_anomaly": True
        }

        res = self.engine.calibrate_belief("Mean Reversion", signals)

        post_p = res["posterior_win_probability"]
        prior_p = res["prior_win_probability"]

        self.assertLess(post_p, prior_p)
        self.assertEqual(res["calibration_action"], "LOW_BELIEF_REJECT")


if __name__ == "__main__":
    unittest.main()
