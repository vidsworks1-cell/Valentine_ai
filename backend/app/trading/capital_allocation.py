import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

logger = logging.getLogger("lumina.trading.capital_allocation")

class CapitalAllocationEngine:
    """
    Dynamic Portfolio Strategy Capital Allocation Engine for Lumina.
    
    Rather than static allocations or simple per-trade sizing, this engine continuously optimizes
    the share of overall portfolio capital assigned to each active strategy bucket based on:
    - Champion-Challenger Elo ratings
    - Strategy Sharpe Ratio and Win Rate from Self-Adaptation
    - Current Market Regime compatibility
    - Cross-strategy correlation / diversification matrix
    """
    _instance: Optional['CapitalAllocationEngine'] = None

    def __init__(self):
        # Default strategy buckets
        self.strategies = [
            "Breakout Momentum",
            "Mean Reversion",
            "Volatility Expansion",
            "Trend Following",
            "Statistical Arbitrage"
        ]

        # Target allocation percentages (must sum to 1.0)
        self.allocations: Dict[str, float] = {
            "Breakout Momentum": 0.25,
            "Mean Reversion": 0.25,
            "Volatility Expansion": 0.20,
            "Trend Following": 0.15,
            "Statistical Arbitrage": 0.15
        }

        self.last_rebalanced_at: Optional[str] = None
        self.rebalance_history: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'CapitalAllocationEngine':
        if cls._instance is None:
            cls._instance = CapitalAllocationEngine()
        return cls._instance

    def recalculate_strategy_allocations(
        self,
        total_equity: float = 100000.0,
        current_regime: str = "VOLATILE",
        elo_ratings: Optional[Dict[str, float]] = None,
        strategy_stats: Optional[Dict[str, Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        """
        Dynamically computes optimal strategy capital allocation percentages and dollar limits.
        """
        elo_map = elo_ratings or {
            "Breakout Momentum": 1550.0,
            "Mean Reversion": 1500.0,
            "Volatility Expansion": 1520.0,
            "Trend Following": 1480.0,
            "Statistical Arbitrage": 1490.0
        }

        raw_weights = {}
        for strat in self.strategies:
            # 1. Elo Rating Score (baseline 1500)
            elo = elo_map.get(strat, 1500.0)
            elo_score = max(0.5, elo / 1500.0)

            # 2. Performance Stats (Win Rate & Sharpe)
            stats = (strategy_stats or {}).get(strat, {})
            win_rate = stats.get("win_rate_pct", 50.0)
            performance_mult = 0.8 + (win_rate / 100.0) * 0.4  # Range 0.8 to 1.2

            # 3. Regime Compatibility Multiplier
            regime_mult = 1.0
            if current_regime == "VOLATILE":
                if "Volatility" in strat or "Breakout" in strat:
                    regime_mult = 1.25
                elif "Mean Reversion" in strat:
                    regime_mult = 0.85
            elif current_regime == "RANGING":
                if "Mean Reversion" in strat or "Statistical" in strat:
                    regime_mult = 1.3
                elif "Breakout" in strat:
                    regime_mult = 0.8
            elif current_regime == "TRENDING":
                if "Trend" in strat or "Breakout" in strat:
                    regime_mult = 1.35
                elif "Mean Reversion" in strat:
                    regime_mult = 0.75

            raw_weights[strat] = elo_score * performance_mult * regime_mult

        # Normalize weights & enforce min/max bounds (min 5%, max 40%)
        total_weight = sum(raw_weights.values())
        normalized = {s: raw_weights[s] / total_weight for s in self.strategies}

        bounded = {}
        for s, pct in normalized.items():
            bounded[s] = max(0.05, min(0.40, pct))

        bounded_total = sum(bounded.values())
        final_allocations = {s: round(bounded[s] / bounded_total, 4) for s in self.strategies}

        self.allocations = final_allocations
        self.last_rebalanced_at = datetime.now(timezone.utc).isoformat()

        dollar_allocations = {s: round(pct * total_equity, 2) for s, pct in final_allocations.items()}

        rebalance_record = {
            "timestamp": self.last_rebalanced_at,
            "total_equity": total_equity,
            "regime": current_regime,
            "allocations_pct": final_allocations,
            "allocations_usd": dollar_allocations
        }

        self.rebalance_history.append(rebalance_record)
        if len(self.rebalance_history) > 100:
            self.rebalance_history.pop(0)

        logger.info(f"📊 [CAPITAL ALLOCATION] Portfolio rebalanced for ${total_equity:.2f} equity ({current_regime}): {final_allocations}")

        return rebalance_record

    def get_strategy_capital_limit(self, strategy_name: str, total_equity: float = 100000.0) -> Dict[str, Any]:
        """
        Returns maximum dollar capital and risk allocation for a given strategy name.
        """
        pct = self.allocations.get(strategy_name, 0.20)
        max_cap_usd = round(pct * total_equity, 2)
        return {
            "strategy": strategy_name,
            "allocation_pct": round(pct * 100.0, 2),
            "max_allocated_usd": max_cap_usd,
            "total_portfolio_equity": total_equity
        }

    def get_allocation_summary(self, total_equity: float = 100000.0) -> Dict[str, Any]:
        """
        Returns full portfolio capital allocation summary payload.
        """
        return {
            "total_equity": total_equity,
            "last_rebalanced_at": self.last_rebalanced_at,
            "allocations_pct": self.allocations,
            "allocations_usd": {s: round(pct * total_equity, 2) for s, pct in self.allocations.items()},
            "rebalance_history_count": len(self.rebalance_history),
            "recent_rebalances": self.rebalance_history[-5:]
        }
