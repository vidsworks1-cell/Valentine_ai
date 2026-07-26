import logging
import math
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime, timezone

logger = logging.getLogger("lumina.ai.bayesian_belief")

class BayesianBeliefCalibrationEngine:
    """
    Bayesian Dynamic Belief Calibration Engine for Lumina.
    
    Applies Bayes' Theorem to dynamically update prior win-probability beliefs P(Win)
    into posterior calibrated win-probabilities P(Win | Evidence) upon observing incoming market evidence:
    
        P(Win | Evidence) = [ P(Evidence | Win) * P(Win) ] / P(Evidence)
        
    Equivalently in log-odds form:
        Posterior Odds = Prior Odds * Likelihood Ratio (L)
        where Likelihood Ratio L = P(Evidence | Win) / P(Evidence | Loss)
        
    Key Capabilities:
    1. Strategy-Specific Prior Beliefs: Grounded in historical win rates (e.g., Breakout = 0.58, Mean Reversion = 0.55).
    2. Dynamic Likelihood Calibration: Evaluates signal evidence (MTF trend, RVOL, Squeeze, Macro, Sentiment, Liquidity).
    3. Epistemic Uncertainty Estimation: Beta distribution parameterization B(alpha, beta) for 95% Credible Intervals.
    4. Overconfidence Penalty: Penalizes signals with high epistemic uncertainty or contradictory evidence.
    """
    _instance: Optional['BayesianBeliefCalibrationEngine'] = None

    DEFAULT_PRIORS: Dict[str, float] = {
        "Breakout Momentum": 0.58,
        "Mean Reversion": 0.55,
        "Trend Following": 0.61,
        "Volatility Squeeze": 0.60,
        "DEFAULT": 0.55
    }

    # Historical evidence counts (wins, losses) per strategy for Beta(alpha, beta) calculation
    STRATEGY_COUNTS: Dict[str, Tuple[int, int]] = {
        "Breakout Momentum": (68, 48),  # 116 total trades
        "Mean Reversion": (54, 44),    # 98 total trades
        "Trend Following": (82, 52),   # 134 total trades
        "Volatility Squeeze": (72, 48), # 120 total trades
        "DEFAULT": (50, 40)
    }

    def __init__(self):
        self._last_calibrated_at: Optional[datetime] = None

    @classmethod
    def get_instance(cls) -> 'BayesianBeliefCalibrationEngine':
        if cls._instance is None:
            cls._instance = BayesianBeliefCalibrationEngine()
        return cls._instance

    def calibrate_belief(
        self,
        strategy: str,
        signals: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Calculates posterior win probability given prior belief and observed market signal evidence.
        
        `signals` dictionary parameters expected:
        - mtf_aligned: bool
        - rvol_surge: bool
        - squeeze_firing: bool
        - macro_risk_low: bool
        - sentiment_aligned: bool
        - liquidity_magnet_aligned: bool
        - is_structural_anomaly: bool
        """
        strat_key = strategy if strategy in self.DEFAULT_PRIORS else "DEFAULT"
        prior_p = self.DEFAULT_PRIORS[strat_key]

        # Convert prior probability to prior odds: O = p / (1 - p)
        prior_odds = prior_p / (1.0 - prior_p)

        # Calculate composite Likelihood Ratio (L) across evidence factors
        # L_i = P(Evidence_i | Win) / P(Evidence_i | Loss)
        likelihood_factors: Dict[str, float] = {}

        # 1. MTF Trend Confluence
        if signals.get("mtf_aligned", False):
            likelihood_factors["mtf_trend"] = 1.45  # Strong evidence towards win
        else:
            likelihood_factors["mtf_trend"] = 0.75  # Counter-trend reduces odds

        # 2. RVOL Surge
        if signals.get("rvol_surge", False):
            likelihood_factors["rvol_surge"] = 1.35
        else:
            likelihood_factors["rvol_surge"] = 0.85

        # 3. Volatility Squeeze Firing
        if signals.get("squeeze_firing", False):
            likelihood_factors["squeeze_firing"] = 1.50
        else:
            likelihood_factors["squeeze_firing"] = 0.90

        # 4. Macro Risk Regime
        if signals.get("macro_risk_low", True):
            likelihood_factors["macro_risk"] = 1.20
        else:
            likelihood_factors["macro_risk"] = 0.50  # High macro risk severely cuts likelihood

        # 5. Sentiment Alignment
        if signals.get("sentiment_aligned", False):
            likelihood_factors["sentiment"] = 1.25
        else:
            likelihood_factors["sentiment"] = 0.88

        # 6. On-Chain Liquidity Magnet Sweep
        if signals.get("liquidity_magnet_aligned", False):
            likelihood_factors["liquidity_sweep"] = 1.30
        else:
            likelihood_factors["liquidity_sweep"] = 0.92

        # 7. Structural Anomaly (Self-Supervised)
        if signals.get("is_structural_anomaly", False):
            likelihood_factors["structural_anomaly"] = 0.65  # Reduces likelihood due to unpredictable regimes

        # Calculate Net Likelihood Ratio L
        composite_likelihood_ratio = 1.0
        for name, l_val in likelihood_factors.items():
            composite_likelihood_ratio *= l_val

        composite_likelihood_ratio = round(composite_likelihood_ratio, 3)

        # Compute Posterior Odds = Prior Odds * L
        posterior_odds = prior_odds * composite_likelihood_ratio

        # Convert Posterior Odds back to Posterior Probability: P = O / (1 + O)
        posterior_p = posterior_odds / (1.0 + posterior_odds)

        # Epistemic Uncertainty & 95% Credible Interval using Beta(alpha, beta) distribution
        wins, losses = self.STRATEGY_COUNTS.get(strat_key, (50, 40))
        alpha = wins + 1
        beta_param = losses + 1
        total_n = alpha + beta_param

        # Beta mean = alpha / (alpha + beta)
        # Beta variance = (alpha * beta) / ((alpha + beta)^2 * (alpha + beta + 1))
        beta_var = (alpha * beta_param) / ((total_n ** 2) * (total_n + 1))
        std_dev = math.sqrt(beta_var)

        epistemic_uncertainty = round(std_dev * 2.0, 3)  # Approx 2x std dev uncertainty measure

        # 95% Credible Interval (Normal approximation to Beta distribution)
        ci_lower = max(0.0, round(posterior_p - (1.96 * std_dev), 3))
        ci_upper = min(1.0, round(posterior_p + (1.96 * std_dev), 3))

        # Calibrated Confidence Score: Adjusted for epistemic uncertainty
        calibrated_confidence = round(max(0.50, min(0.96, posterior_p - (epistemic_uncertainty * 0.20))), 2)

        if calibrated_confidence >= 0.82:
            calibration_action = "HIGH_BELIEF_CONVICTION_EXECUTE"
        elif calibrated_confidence >= 0.72:
            calibration_action = "MODERATE_BELIEF_EXECUTE"
        else:
            calibration_action = "LOW_BELIEF_REJECT"

        self._last_calibrated_at = datetime.now(timezone.utc)

        return {
            "strategy": strategy,
            "prior_win_probability": round(prior_p, 3),
            "prior_odds": round(prior_odds, 3),
            "composite_likelihood_ratio": composite_likelihood_ratio,
            "posterior_odds": round(posterior_odds, 3),
            "posterior_win_probability": round(posterior_p, 3),
            "calibrated_confidence": calibrated_confidence,
            "epistemic_uncertainty": epistemic_uncertainty,
            "credible_interval_95": {
                "lower_bound": ci_lower,
                "upper_bound": ci_upper
            },
            "likelihood_factors": likelihood_factors,
            "calibration_action": calibration_action,
            "beta_distribution_params": {
                "alpha_wins": alpha,
                "beta_losses": beta_param,
                "sample_size": total_n
            },
            "timestamp": self._last_calibrated_at.isoformat()
        }
