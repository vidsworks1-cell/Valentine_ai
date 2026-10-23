import logging
import math
import asyncio
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from sqlalchemy.future import select
from app.database.session import AsyncSessionLocal
from app.models.trading import Trade

logger = logging.getLogger("lumina.ai.walk_forward")

class WalkForwardValidator:
    """
    Walk-Forward Optimization & Strategy Refinement Engine for Lumina.
    
    Validates trading strategies across sliding historical windows and market regimes:
    - Replays recent closed trade history in 70/30 In-Sample / Out-of-Sample segments
    - Computes Walk-Forward Efficiency Ratio (WFE = OOS Profit Factor / IS Profit Factor)
    - Breaks down strategy yield per market regime (TRENDING, RANGING, VOLATILE, BREAKOUT)
    - Generates actionable strategy refinement suggestions and parameter tuning rules
    """
    _instance: Optional['WalkForwardValidator'] = None

    def __init__(self):
        self._last_run_time: Optional[datetime] = None
        self._last_report: Dict[str, Any] = {}

    @classmethod
    def get_instance(cls) -> 'WalkForwardValidator':
        if cls._instance is None:
            cls._instance = WalkForwardValidator()
        return cls._instance

    @staticmethod
    def _compute_profit_factor(wins: List[float], losses: List[float]) -> float:
        gross_profit = sum(wins)
        gross_loss = abs(sum(losses))
        if gross_loss == 0.0:
            return round(gross_profit if gross_profit > 0 else 1.0, 2)
        return round(gross_profit / gross_loss, 2)

    @staticmethod
    def _compute_sharpe_ratio(pnls: List[float]) -> float:
        if len(pnls) < 2:
            return 0.0
        mean_pnl = sum(pnls) / len(pnls)
        variance = sum((x - mean_pnl) ** 2 for x in pnls) / (len(pnls) - 1)
        std_dev = math.sqrt(variance)
        if std_dev == 0.0:
            return 0.0
        # Annualized Sharpe assuming 252 trading sessions
        return round((mean_pnl / std_dev) * math.sqrt(252), 2)

    async def run_walk_forward_validation(self) -> Dict[str, Any]:
        """
        Executes Walk-Forward validation & regime yield evaluation across closed trades in DB.
        """
        try:
            async with AsyncSessionLocal() as db:
                res = await db.execute(select(Trade).where(Trade.exit_price.isnot(None)).order_by(Trade.entry_time.asc()))
                trades = res.scalars().all()

            if not trades or len(trades) < 4:
                report = {
                    "status": "INSUFFICIENT_DATA",
                    "message": "Minimum 4 closed trades required for Walk-Forward & Strategy Refinement analysis.",
                    "evaluated_at": datetime.now(timezone.utc).isoformat(),
                    "strategies": {},
                    "regime_matrix": {},
                    "refinement_suggestions": []
                }
                self._last_report = report
                return report

            # 1. Group trades by strategy
            strat_trades: Dict[str, List[Trade]] = {}
            # 2. Group trades by regime & strategy
            regime_matrix: Dict[str, Dict[str, List[Trade]]] = {}

            from app.trading.strategy_classifier import is_system_strategy_label
            for t in trades:
                s = t.strategy_used or "Breakout Momentum"
                if is_system_strategy_label(s):
                    continue
                r = t.market_regime or "RANGING"
                strat_trades.setdefault(s, []).append(t)
                regime_matrix.setdefault(r, {}).setdefault(s, []).append(t)

            strat_results = {}
            refinement_suggestions = []

            for strat, s_list in strat_trades.items():
                total = len(s_list)
                pnls = [(t.profit_loss or 0.0) for t in s_list]
                wins = [p for p in pnls if p > 0]
                losses = [p for p in pnls if p < 0]
                
                win_rate = (len(wins) / total) * 100.0 if total > 0 else 0.0
                profit_factor = self._compute_profit_factor(wins, losses)
                sharpe = self._compute_sharpe_ratio(pnls)

                # Split 70/30 In-Sample vs Out-of-Sample
                split_idx = max(1, int(total * 0.7))
                in_sample = s_list[:split_idx]
                out_sample = s_list[split_idx:] if split_idx < total else s_list[-1:]

                in_wins = [t.profit_loss or 0.0 for t in in_sample if (t.profit_loss or 0) > 0]
                in_losses = [t.profit_loss or 0.0 for t in in_sample if (t.profit_loss or 0) < 0]
                out_wins = [t.profit_loss or 0.0 for t in out_sample if (t.profit_loss or 0) > 0]
                out_losses = [t.profit_loss or 0.0 for t in out_sample if (t.profit_loss or 0) < 0]

                in_pf = self._compute_profit_factor(in_wins, in_losses)
                out_pf = self._compute_profit_factor(out_wins, out_losses)

                wfe = round((out_pf / in_pf), 2) if in_pf > 0 else 1.0

                if wfe >= 0.85:
                    verdict = "ROBUST"
                elif wfe >= 0.60:
                    verdict = "STABLE"
                else:
                    verdict = "OVERFITTED"
                    refinement_suggestions.append(
                        f"⚠️ [OVERFITTING ALERT] {strat} shows degradation in Out-of-Sample testing (WFE: {wfe}). "
                        f"Recommend tightening confidence threshold by +0.05."
                    )

                strat_results[strat] = {
                    "total_trades": total,
                    "win_rate_pct": round(win_rate, 1),
                    "profit_factor": profit_factor,
                    "sharpe_ratio": sharpe,
                    "in_sample_pnl": round(sum(t.profit_loss or 0.0 for t in in_sample), 2),
                    "out_of_sample_pnl": round(sum(t.profit_loss or 0.0 for t in out_sample), 2),
                    "walk_forward_efficiency": wfe,
                    "verdict": verdict
                }

            # 3. Evaluate Regime Matrix
            regime_report = {}
            for reg, strats in regime_matrix.items():
                regime_report[reg] = {}
                for strat, t_list in strats.items():
                    r_pnls = [(t.profit_loss or 0.0) for t in t_list]
                    r_wins = [p for p in r_pnls if p > 0]
                    r_losses = [p for p in r_pnls if p < 0]
                    r_wr = round((len(r_wins) / len(t_list)) * 100.0, 1)
                    r_pf = self._compute_profit_factor(r_wins, r_losses)
                    
                    regime_report[reg][strat] = {
                        "trades": len(t_list),
                        "win_rate_pct": r_wr,
                        "profit_factor": r_pf,
                        "total_pnl": round(sum(r_pnls), 2)
                    }

                    if r_wr < 40.0 and len(t_list) >= 2:
                        refinement_suggestions.append(
                            f"📉 [REGIME MISMATCH] {strat} in {reg} regime has weak win rate ({r_wr}%). "
                            f"Recommend lowering risk allocation by 30% during {reg} regimes."
                        )
                    elif r_wr >= 70.0 and len(t_list) >= 2:
                        refinement_suggestions.append(
                            f"🔥 [STRONG REGIME SYNERGY] {strat} in {reg} regime achieves {r_wr}% win rate (PF: {r_pf}). "
                            f"Recommend boosting position size multiplier by 1.15x."
                        )

            self._last_run_time = datetime.now(timezone.utc)
            report = {
                "status": "SUCCESS",
                "evaluated_at": self._last_run_time.isoformat(),
                "total_trades_analyzed": len(trades),
                "strategies": strat_results,
                "regime_matrix": regime_report,
                "refinement_suggestions": refinement_suggestions
            }
            self._last_report = report
            logger.info(f"[WALK-FORWARD & STRATEGY REFINEMENT] Analyzed {len(trades)} trades across {len(strat_results)} strategies.")
            return report

        except Exception as e:
            logger.error(f"[WALK-FORWARD] Error during strategy refinement analysis: {e}")
            return {"status": "ERROR", "message": str(e)}

    def get_latest_report(self) -> Dict[str, Any]:
        return self._last_report
