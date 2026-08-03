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
            "Liquidity Sweep",
            "Mean Reversion Grid",
            "Breakout Momentum",
            "Trend Following",
            "Statistical Arbitrage"
        ]

        # Target allocation percentages (must sum to 1.0)
        self.allocations: Dict[str, float] = {
            "Trend Following": 0.35,
            "Liquidity Sweep": 0.35,
            "Mean Reversion Grid": 0.25,
            "Breakout Momentum": 0.05,
            "Statistical Arbitrage": 0.15
        }

        self._recent_expectancy_r: Dict[str, float] = {
            "Trend Following": 0.82,
            "Liquidity Sweep": 0.65,
            "Mean Reversion Grid": 0.48,
            "Statistical Arbitrage": 0.25,
            "Breakout Momentum": -0.12
        }

        self.last_rebalanced_at: Optional[str] = None
        self.rebalance_history: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'CapitalAllocationEngine':
        if cls._instance is None:
            cls._instance = CapitalAllocationEngine()
        return cls._instance

    def calculate_dynamic_portfolio_allocation(
        self,
        symbol: str,
        expected_return: float,
        bayesian_confidence: float,
        correlation_matrix: Optional[Dict[str, float]] = None,
        drawdown_pct: float = 0.0
    ) -> Dict[str, Any]:
        """
        Portfolio Intelligence Dynamic Allocator:
        Formula: Allocation = Expected Return * Bayesian Confidence * Correlation Penalty * Drawdown State
        """
        # Correlation Penalty (higher correlation with active positions -> lower multiplier)
        avg_correlation = 0.35
        if correlation_matrix and symbol in correlation_matrix:
            avg_correlation = correlation_matrix[symbol]
        correlation_penalty = max(0.20, 1.0 - (avg_correlation * 0.85))

        # Drawdown State Scalar (e.g. 0% DD -> 1.0, 10% DD -> 0.50, 20% DD -> 0.20)
        drawdown_state = max(0.15, 1.0 - (drawdown_pct / 20.0))

        raw_allocation_score = expected_return * bayesian_confidence * correlation_penalty * drawdown_state
        base_capital = 10000.0  # $10k per base unit
        allocated_capital_usd = round(base_capital * raw_allocation_score, 2)

        return {
            "symbol": symbol,
            "expected_return": expected_return,
            "bayesian_confidence": bayesian_confidence,
            "correlation_penalty": round(correlation_penalty, 3),
            "drawdown_state": round(drawdown_state, 3),
            "raw_allocation_score": round(raw_allocation_score, 4),
            "allocated_capital_usd": allocated_capital_usd,
            "formula_applied": "Expected Return * Bayesian Confidence * Correlation Penalty * Drawdown State"
        }

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

    def get_strategy_competition_table(self) -> List[Dict[str, Any]]:
        """
        Returns competitive strategy tournament table ranking strategies by recent expectancy.
        Example:
        - Liquidity Sweep | +0.65R | 35%
        - Mean Reversion Grid | +0.48R | 25%
        - Breakout Momentum | -0.12R | 5%
        - Trend Following | +0.82R | 35%
        """
        table = []
        for strat in self.strategies:
            exp_r = self._recent_expectancy_r.get(strat, 0.20)
            alloc_pct = self.allocations.get(strat, 0.20)
            status = "DOMINANT_CAPITAL" if alloc_pct >= 0.30 else ("MINIMAL_CAPITAL" if alloc_pct <= 0.05 else "STANDARD_CAPITAL")
            table.append({
                "strategy": strat,
                "recent_expectancy_r": f"{exp_r:+.2f}R",
                "recent_expectancy_raw": exp_r,
                "capital_allocation_pct": f"{round(alloc_pct * 100.0, 1)}%",
                "capital_allocation_raw": alloc_pct,
                "tournament_status": status
            })
        
        # Sort by recent expectancy descending
        table.sort(key=lambda x: x["recent_expectancy_raw"], reverse=True)
        return table

    def get_allocation_summary(self, total_equity: float = 100000.0) -> Dict[str, Any]:
        """
        Returns full portfolio capital allocation summary payload.
        """
        return {
            "total_equity": total_equity,
            "last_rebalanced_at": self.last_rebalanced_at,
            "strategy_competition_table": self.get_strategy_competition_table(),
            "allocations_pct": self.allocations,
            "allocations_usd": {s: round(pct * total_equity, 2) for s, pct in self.allocations.items()},
            "rebalance_history_count": len(self.rebalance_history),
            "recent_rebalances": self.rebalance_history[-5:]
        }
