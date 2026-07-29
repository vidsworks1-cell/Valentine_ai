import logging
import math
from typing import Dict, Any, Optional, Tuple

logger = logging.getLogger("lumina.risk.adaptive_sizer")

class AdaptivePositionSizer:
    """
    Adaptive Multi-Factor Position Sizing Engine for Lumina.
    
    Dynamically adjusts risk allocation per trade based on:
    1. Signal Confidence Score (0.70 = 0.5% risk up to 0.95+ = 2.5% risk)
    2. Strategy Performance & Historical Win Rate
    3. Market Regime (TRENDING vs RANGING vs VOLATILE)
    4. Volatility & ATR Ratio
    5. Recent Win/Loss Streak & Account Drawdown
    
    Replaces static 1% risk rules with institutional multi-factor capital allocation.
    """
    _instance: Optional['AdaptivePositionSizer'] = None

    @classmethod
    def get_instance(cls) -> 'AdaptivePositionSizer':
        if cls._instance is None:
            cls._instance = AdaptivePositionSizer()
        return cls._instance

    def calculate_adaptive_risk_pct(
        self,
        opportunity: Dict[str, Any],
        balance: float = 100000.0,
        recent_win_streak: int = 0,
        account_drawdown_pct: float = 0.0,
        strategy_win_rate_pct: float = 55.0
    ) -> Tuple[float, Dict[str, Any]]:
        """
        Calculates dynamic risk percentage (e.g., 0.005 = 0.5% to 0.025 = 2.5%).
        Returns (risk_pct: float, breakdown: Dict[str, Any]).
        """
        confidence = float(opportunity.get("confidence_score", opportunity.get("confidence", 0.75)))
        entry_price = float(opportunity.get("entry_price", opportunity.get("price", 1.0)))
        atr = float(opportunity.get("atr", entry_price * 0.01))
        regime = str(opportunity.get("market_regime", "TRENDING")).upper()
        strategy = str(opportunity.get("strategy_used", opportunity.get("strategy", "General")))

        # 1. Base Risk from Signal Confidence
        # Curve: 0.70 -> 0.005 (0.5%), 0.80 -> 0.015 (1.5%), 0.95+ -> 0.025 (2.5%)
        if confidence >= 0.92:
            base_risk_pct = 0.025
        elif confidence >= 0.85:
            base_risk_pct = 0.020
        elif confidence >= 0.78:
            base_risk_pct = 0.015
        elif confidence >= 0.73:
            base_risk_pct = 0.010
        else:
            base_risk_pct = 0.005

        # 2. Strategy Reliability & Live Elo Multipliers
        if strategy_win_rate_pct >= 65.0:
            strategy_mult = 1.20
        elif strategy_win_rate_pct >= 55.0:
            strategy_mult = 1.00
        elif strategy_win_rate_pct >= 45.0:
            strategy_mult = 0.80
        elif strategy_win_rate_pct >= 35.0:
            strategy_mult = 0.50
        elif strategy_win_rate_pct >= 20.0:
            strategy_mult = 0.30  # Reduces risk cap to ~0.15% - 0.25%
        else:
            strategy_mult = 0.15  # Severely underperforming strategy max risk cap ~0.15%

        elo_capital_mult = 1.0
        try:
            from app.trading.champion_challenger import ChampionChallengerEngine
            elo_capital_mult = ChampionChallengerEngine.get_instance().get_elo_capital_multiplier(strategy)
        except Exception as e:
            logger.error(f"Failed to fetch Elo capital multiplier for {strategy}: {e}")

        # 3. Market Regime Multiplier
        if "TREND" in regime:
            regime_mult = 1.15
        elif "RANGE" in regime or "SIDEWAYS" in regime:
            regime_mult = 0.75
        elif "VOLATILE" in regime or "HIGH_VOL" in regime:
            regime_mult = 0.50
        else:
            regime_mult = 1.00

        # 4. Relative Volatility Multiplier
        rel_vol = (atr / entry_price) if entry_price > 0 else 0.01
        if rel_vol > 0.025:
            volatility_mult = max(0.40, round(0.015 / rel_vol, 2))
        elif rel_vol > 0.015:
            volatility_mult = max(0.70, round(0.015 / rel_vol, 2))
        else:
            volatility_mult = 1.00

        # 5. Recent Win Streak & Account Drawdown Protection
        # Win streak boost: +5% per win in streak up to +20%
        streak_mult = 1.00 + min(0.20, max(-0.30, recent_win_streak * 0.05))

        # Drawdown protection: scale down if drawdown > 3%
        if account_drawdown_pct >= 0.08: # 8%+ drawdown -> cut size by 50%
            drawdown_mult = 0.50
        elif account_drawdown_pct >= 0.04: # 4%+ drawdown -> cut size by 25%
            drawdown_mult = 0.75
        else:
            drawdown_mult = 1.00

        streak_dd_mult = streak_mult * drawdown_mult

        # 6. Combined Multi-Factor Risk Calculation
        raw_adaptive_risk = base_risk_pct * strategy_mult * elo_capital_mult * regime_mult * volatility_mult * streak_dd_mult
        
        # Hard safety floor (0.5%) and cap (3.0%)
        final_risk_pct = round(max(0.005, min(0.030, raw_adaptive_risk)), 4)

        breakdown = {
            "confidence": round(confidence, 2),
            "base_risk_pct": round(base_risk_pct * 100, 2),
            "strategy_mult": strategy_mult,
            "elo_capital_multiplier": elo_capital_mult,
            "regime_mult": regime_mult,
            "volatility_mult": volatility_mult,
            "streak_mult": round(streak_mult, 2),
            "drawdown_mult": round(drawdown_mult, 2),
            "final_risk_pct": round(final_risk_pct * 100, 2)
        }

        logger.info(
            f"🎯 [ADAPTIVE POSITION SIZING] {opportunity.get('symbol', 'UNKNOWN')} ({strategy}): "
            f"Confidence {confidence:.2f} -> Base Risk {base_risk_pct*100:.1f}%. "
            f"Multipliers [Strategy: {strategy_mult}x, Regime ({regime}): {regime_mult}x, Volatility: {volatility_mult}x, Streak/DD: {streak_dd_mult:.2f}x] "
            f"==> Final Adaptive Risk: {final_risk_pct*100:.2f}%"
        )

        return final_risk_pct, breakdown
