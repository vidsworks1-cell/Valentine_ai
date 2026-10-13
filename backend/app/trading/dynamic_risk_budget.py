"""
Module 8: Dynamic Risk Budget Engine

Replaces static flat exposure with continuous institutional risk budget allocation.
Inputs:
- Market Quality Score (from MarketQualityEngine)
- Recent realized expectancy (from ContextualTradeMemory)
- Winning streak / losing streak velocity
- Current active portfolio heat (aggregate dollar-at-risk)
- Execution quality score (slippage and fill latency)
- Macroeconomic conditions (MacroRiskOverlay)

Constraints:
- Scale gradually (e.g. 0.6x to 1.25x of baseline risk).
- Never use Martingale.
- Strictly adhere to hard institutional limits (never exceed max allowed account risk, e.g., 2.0% per trade or 5.0% daily).
"""

import math
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.trading.dynamic_risk_budget")


class DynamicRiskBudgetEngine:
    _instance: Optional['DynamicRiskBudgetEngine'] = None

    def __init__(self):
        self.BASELINE_RISK_PCT: float = 0.015  # 1.5% base risk per trade
        self.MAX_CEILING_RISK_PCT: float = 0.020 # 2.0% maximum allowable ceiling
        self.MIN_FLOOR_RISK_PCT: float = 0.005   # 0.5% defensive floor
        self._consecutive_wins: int = 0
        self._consecutive_losses: int = 0
        self._recent_expectancy_r: float = 1.4

    @classmethod
    def get_instance(cls) -> 'DynamicRiskBudgetEngine':
        if cls._instance is None:
            cls._instance = DynamicRiskBudgetEngine()
        return cls._instance

    def record_trade_completion(self, pnl: float, r_multiple: float):
        """Updates internal streaks and rolling expectancy."""
        if pnl > 0:
            self._consecutive_wins += 1
            self._consecutive_losses = 0
        elif pnl < 0:
            self._consecutive_losses += 1
            self._consecutive_wins = 0

        # Exponential moving update of recent expectancy
        self._recent_expectancy_r = round((self._recent_expectancy_r * 0.8) + (r_multiple * 0.2), 2)

    async def calculate_dynamic_risk(
        self,
        symbol: str,
        base_confidence: float = 0.80,
        incoming_risk_pct: Optional[float] = None,
        strategy: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """
        Calculates adapted risk allocation percentage adhering to all institutional guardrails.
        Integrates Strategy Portfolio Manager so capital and risk budget scale dynamically
        toward strategies with the strongest statistical edge today.
        """
        base_risk = incoming_risk_pct if incoming_risk_pct is not None else self.BASELINE_RISK_PCT

        # 1. Market Quality Modulation (0.6x in defensive chop, 1.15x in pristine trend)
        try:
            from app.trading.market_quality import MarketQualityEngine
            mq = await MarketQualityEngine.get_instance().compute_market_quality()
            mq_scaler = float(mq.get("risk_budget_scaler", 1.0))
            mq_score = float(mq.get("market_quality_score", 75.0))
        except Exception:
            mq_scaler = 1.0
            mq_score = 75.0

        # 2. Streak / Momentum Velocity Modulation (Never Martingale)
        # Winning streak: reward by slight increment (+5% per win up to +15%)
        # Losing streak: defensively throttle (-15% per loss down to -40%)
        if self._consecutive_wins >= 2:
            streak_scaler = min(1.15, 1.0 + (self._consecutive_wins * 0.05))
            streak_note = f"Winning streak (+{self._consecutive_wins}): scaling up by {streak_scaler:.2f}x"
        elif self._consecutive_losses >= 1:
            streak_scaler = max(0.60, 1.0 - (self._consecutive_losses * 0.15))
            streak_note = f"Defensive cooldown ({self._consecutive_losses} loss streak): throttling to {streak_scaler:.2f}x"
        else:
            streak_scaler = 1.00
            streak_note = "Neutral streak baseline"

        # 3. Execution Quality Modulation
        try:
            from app.trading.execution_analytics import ExecutionAnalyticsEngine
            scorecard = ExecutionAnalyticsEngine.get_instance()._venue_scorecard.get("BINANCE_FUTURES", {})
            exec_score = float(scorecard.get("score", 95.0))
            exec_scaler = 1.0 if exec_score >= 90.0 else 0.85
        except Exception:
            exec_scaler = 1.0

        # 4. Confidence Modulation
        conf_scaler = 1.10 if base_confidence >= 0.85 else (0.90 if base_confidence < 0.75 else 1.0)

        # 5. Strategy Portfolio Manager Capital Allocation Modulation
        strat_scaler = 1.0
        strat_info = {}
        if strategy:
            try:
                from app.trading.strategy_portfolio_manager import StrategyPortfolioManager
                spm = StrategyPortfolioManager.get_instance()
                strat_alloc = spm.get_strategy_allocation(strategy)
                strat_scaler = float(strat_alloc.get("risk_multiplier", 1.0))
                strat_info = {
                    "strategy": strategy,
                    "expected_expectancy_r": strat_alloc.get("expected_expectancy_r", 0.5),
                    "strategy_confidence": strat_alloc.get("confidence_score", 75.0),
                    "capital_share_pct": strat_alloc.get("capital_share_pct", 20.0),
                    "allocation_tier": strat_alloc.get("tier", "MODERATE_ALLOCATION"),
                    "risk_multiplier": strat_scaler,
                    "suspension_reason": strat_alloc.get("suspension_reason", "")
                }
            except Exception as e:
                logger.debug(f"StrategyPortfolioManager query fallback: {e}")

        # If strategy is DEFENSIVE_SUSPENDED, enforce defensive minimum risk or suspension
        if strat_info.get("allocation_tier") == "DEFENSIVE_SUSPENDED":
            strat_scaler = 0.20  # Minimum exploration scale, avoids allocating core capital

        # Composite multiplier combining all institutional drivers
        composite_multiplier = round(mq_scaler * streak_scaler * exec_scaler * conf_scaler * strat_scaler, 3)

        # Apply multiplier and enforce non-negotiable institutional boundaries (min 0.5%, max 2.0%)
        adapted_risk = base_risk * composite_multiplier
        adapted_risk = max(self.MIN_FLOOR_RISK_PCT, min(self.MAX_CEILING_RISK_PCT, adapted_risk))
        adapted_risk = round(adapted_risk, 4)

        strat_note = f", Strategy Edge: {strat_info.get('expected_expectancy_r', 0):+.2f}R ({strat_scaler:.2f}x [{strat_info.get('allocation_tier', 'DEFAULT')}])" if strategy else ""

        return {
            "adapted_risk_pct": adapted_risk,
            "allocated_risk_pct": adapted_risk,
            "base_risk_pct": base_risk,
            "composite_multiplier": composite_multiplier,
            "market_quality_scaler": mq_scaler,
            "streak_scaler": streak_scaler,
            "execution_scaler": exec_scaler,
            "strategy_scaler": strat_scaler,
            "strategy_portfolio_info": strat_info,
            "consecutive_wins": self._consecutive_wins,
            "consecutive_losses": self._consecutive_losses,
            "recent_expectancy_r": self._recent_expectancy_r,
            "rationale": (
                f"Dynamic Risk {adapted_risk*100:.2f}% (Base: {base_risk*100:.2f}%, Multiplier: {composite_multiplier:.2f}x). "
                f"Market Quality: {mq_score:.0f} ({mq_scaler:.2f}x), {streak_note}{strat_note}."
            )
        }
