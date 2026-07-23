import logging
import math
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from sqlalchemy.future import select
from sqlalchemy import desc
from app.database.session import AsyncSessionLocal
from app.models.trading import Trade
from app.api.schemas import AuditLogCreate
from app.services.audit_service import AuditService

logger = logging.getLogger("lumina.trading.champion_challenger")

class ChampionChallengerEngine:
    """
    Strategy Champion / Challenger System for Lumina.
    
    Continuously evaluates and compares trading strategies:
    - Champion: Primary strategy allocated highest weight and priority execution.
    - Challengers: Secondary strategies tested alongside champion.
    
    Metrics Tracked per Strategy:
    - Expectancy = (Win Rate * Avg Win) - (Loss Rate * Avg Loss)
    - Sharpe Ratio = (Mean Return / Std Dev Return) * sqrt(252)
    - Profit Factor = Gross Profit / Gross Loss
    - Win Rate & Max Drawdown
    
    Automatic Promotion:
    When a Challenger's Sharpe Ratio and Expectancy consistently outperform the Champion over a minimum sample size (e.g. 15+ trades), it is automatically promoted to Champion status with full audit logging.
    """
    _instance: Optional['ChampionChallengerEngine'] = None

    def __init__(self):
        self._champion_strategy = "Breakout Momentum"
        self._challenger_strategies = ["Trend Following", "Mean Reversion"]
        self._last_evaluated_at: Optional[datetime] = None
        self._strategy_leaderboard: List[Dict[str, Any]] = []
        self._promotion_history: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'ChampionChallengerEngine':
        if cls._instance is None:
            cls._instance = ChampionChallengerEngine()
        return cls._instance

    @property
    def champion_strategy(self) -> str:
        return self._champion_strategy

    async def evaluate_strategies(self) -> List[Dict[str, Any]]:
        """
        Queries closed trades, groups by strategy, and calculates quantitative metrics.
        Promotes top performing challenger if criteria met.
        """
        try:
            async with AsyncSessionLocal() as db:
                query = (
                    select(Trade)
                    .where(Trade.exit_price.isnot(None))
                    .order_by(desc(Trade.exit_time))
                    .limit(150)
                )
                res = await db.execute(query)
                trades = res.scalars().all()

            strat_trades: Dict[str, List[Trade]] = {
                "Breakout Momentum": [],
                "Trend Following": [],
                "Mean Reversion": []
            }

            for t in trades:
                s_name = t.strategy_used or "Breakout Momentum"
                if s_name not in strat_trades:
                    strat_trades[s_name] = []
                strat_trades[s_name].append(t)

            leaderboard = []

            for strat_name, s_list in strat_trades.items():
                total_trades = len(s_list)
                if total_trades == 0:
                    leaderboard.append({
                        "strategy": strat_name,
                        "role": "CHAMPION" if strat_name == self._champion_strategy else "CHALLENGER",
                        "total_trades": 0,
                        "win_rate_pct": 0.0,
                        "expectancy": 0.0,
                        "profit_factor": 0.0,
                        "sharpe_ratio": 0.0,
                        "max_drawdown_pct": 0.0,
                        "score": 0.0
                    })
                    continue

                pnl_list = [(tr.profit_loss or 0.0) for tr in s_list]
                wins = [p for p in pnl_list if p > 0]
                losses = [p for p in pnl_list if p <= 0]

                win_rate = (len(wins) / total_trades) if total_trades > 0 else 0.0
                loss_rate = 1.0 - win_rate

                avg_win = (sum(wins) / len(wins)) if len(wins) > 0 else 0.0
                avg_loss = abs(sum(losses) / len(losses)) if len(losses) > 0 else 0.0

                expectancy = (win_rate * avg_win) - (loss_rate * avg_loss)
                gross_profit = sum(wins)
                gross_loss = abs(sum(losses))
                profit_factor = (gross_profit / gross_loss) if gross_loss > 0 else (gross_profit if gross_profit > 0 else 1.0)

                # Sharpe Ratio
                mean_pnl = sum(pnl_list) / total_trades
                variance = sum((p - mean_pnl) ** 2 for p in pnl_list) / total_trades if total_trades > 1 else 0.0
                std_dev = math.sqrt(variance) if variance > 0 else 1.0
                sharpe_ratio = (mean_pnl / std_dev) * math.sqrt(252) if std_dev > 0 else 0.0

                # Max Drawdown
                peak = 0.0
                cum = 0.0
                max_dd = 0.0
                for p in reversed(pnl_list): # chronological order
                    cum += p
                    if cum > peak:
                        peak = cum
                    dd = (peak - cum)
                    if dd > max_dd:
                        max_dd = dd
                
                # Overall Quant Performance Composite Score (0 - 100)
                score = round(max(0.0, min(100.0, (win_rate * 40.0) + (min(2.5, profit_factor) * 20.0) + (min(3.0, max(0.0, sharpe_ratio)) * 10.0) + (max(0.0, expectancy) * 5.0))), 1)

                leaderboard.append({
                    "strategy": strat_name,
                    "role": "CHAMPION" if strat_name == self._champion_strategy else "CHALLENGER",
                    "total_trades": total_trades,
                    "win_rate_pct": round(win_rate * 100.0, 1),
                    "expectancy": round(expectancy, 2),
                    "profit_factor": round(profit_factor, 2),
                    "sharpe_ratio": round(sharpe_ratio, 2),
                    "max_drawdown": round(max_dd, 2),
                    "score": score
                })

            # Sort leaderboard by score descending
            leaderboard.sort(key=lambda x: x["score"], reverse=True)
            self._strategy_leaderboard = leaderboard
            self._last_evaluated_at = datetime.now(timezone.utc)

            # Check for Champion Promotion
            top_strat = leaderboard[0]
            if top_strat["strategy"] != self._champion_strategy and top_strat["total_trades"] >= 8:
                old_champion = self._champion_strategy
                new_champion = top_strat["strategy"]
                
                self._champion_strategy = new_champion
                if old_champion in self._challenger_strategies:
                    self._challenger_strategies.remove(old_champion)
                self._challenger_strategies.append(old_champion)

                event = {
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                    "old_champion": old_champion,
                    "new_champion": new_champion,
                    "reason": f"Strategy '{new_champion}' outperformed with Score {top_strat['score']}/100 (Expectancy: ${top_strat['expectancy']}, Sharpe: {top_strat['sharpe_ratio']})."
                }
                self._promotion_history.append(event)
                
                logger.info(f"🏆 [CHAMPION PROMOTION] {new_champion} promoted to CHAMPION replacing {old_champion}!")

                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="INFO",
                            category="STRATEGY",
                            message=f"[CHAMPION PROMOTION] {new_champion} promoted to CHAMPION",
                            details=event["reason"]
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log champion promotion audit: {ae}")

            return self._strategy_leaderboard

        except Exception as e:
            logger.error(f"[CHAMPION-CHALLENGER] Error evaluating strategies: {e}")
            return self._strategy_leaderboard

    def get_champion_challenger_summary(self) -> Dict[str, Any]:
        """Returns champion/challenger state and leaderboard for API/Dashboard."""
        return {
            "champion_strategy": self._champion_strategy,
            "challenger_strategies": self._challenger_strategies,
            "leaderboard": self._strategy_leaderboard,
            "promotion_history": self._promotion_history,
            "last_evaluated_at": self._last_evaluated_at.isoformat() if self._last_evaluated_at else None
        }
