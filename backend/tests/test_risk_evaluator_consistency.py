import unittest
from app.risk.evaluator import RiskEvaluator

class TestRiskEvaluatorConsistency(unittest.TestCase):
    def test_bnb_mismatch_case_resolved(self):
        """
        Tests the exact scenario reported in the prompt:
        - Balance: $992.50, Risk %: 2% ($19.85 allowed risk)
        - BNB Entry Price: $596.20
        - Stop Loss: $581.30 (Stop Distance = $14.90, i.e. 2.0x ATR where ATR = $7.45)
        - Old code calculated position size using hardcoded 1.5x ATR ($11.175), yielding 1.7763 BNB.
        - Validator then evaluated Actual Risk as 1.7763 * $14.90 = $26.46 > $19.85, REJECTING the trade!
        - Fixed code must calculate position size using exact stop distance ($14.90) -> 1.3322 BNB.
        - Validator must evaluate Actual Risk as 1.3322 * $14.90 = $19.849 <= $19.85, ACCEPTING the trade!
        """
        balance = 992.50
        max_risk_pct = 0.02
        entry_price = 596.20
        stop_loss = 581.30
        atr = 7.45

        # 1. Calculate position size using updated RiskEvaluator
        position_size = RiskEvaluator.calculate_atr_position_size(
            balance=balance,
            entry_price=entry_price,
            atr=atr,
            max_risk_pct=max_risk_pct,
            stop_loss=stop_loss
        )

        stop_distance = abs(entry_price - stop_loss)
        expected_allowed_risk = balance * max_risk_pct # $19.85
        actual_risk = position_size * stop_distance

        self.assertLessEqual(actual_risk, expected_allowed_risk + 1e-4)
        self.assertAlmostEqual(actual_risk, expected_allowed_risk, delta=0.05)

        # 2. Validate order with RiskEvaluator
        order_dict = {
            "entry_price": entry_price,
            "stop_loss": stop_loss,
            "atr": atr,
            "position_size": position_size
        }

        passed = RiskEvaluator.validate_order(order_dict, balance, max_risk_pct)
        self.assertTrue(passed, "Order should pass risk validation without exceeding max risk allocation!")

    def test_high_volatility_scaling_consistency(self):
        """Verify that when relative volatility > 1.5%, both sizing and validation agree on scaled risk."""
        balance = 100000.0
        max_risk_pct = 0.02
        entry_price = 100.0
        atr = 3.0 # Relative volatility = 3% > 1.5% -> scaled risk pct = 0.02 * (0.015 / 0.03) = 0.01 (1%)
        stop_loss = 94.0 # Stop distance = 6.0

        size = RiskEvaluator.calculate_atr_position_size(
            balance=balance,
            entry_price=entry_price,
            atr=atr,
            max_risk_pct=max_risk_pct,
            stop_loss=stop_loss
        )

        order_dict = {
            "entry_price": entry_price,
            "stop_loss": stop_loss,
            "atr": atr,
            "position_size": size
        }

        passed = RiskEvaluator.validate_order(order_dict, balance, max_risk_pct)
        self.assertTrue(passed)

if __name__ == "__main__":
    unittest.main()
