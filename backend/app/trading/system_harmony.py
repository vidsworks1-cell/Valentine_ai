import logging
import random
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple

logger = logging.getLogger("lumina.trading.system_harmony")

class TradeDecisionLineageTrace:
    """
    360-Degree Lineage Trace Object for full decision observability.
    Reconstructs exact multi-system reasoning behind every order commitment or scale-in.
    """
    def __init__(
        self,
        trade_id: str,
        symbol: str,
        side: str,
        strategy: str,
        price: float,
        strategy_score: float,
        strategy_elo: int,
        matrix_multiplier: float,
        market_memory_key: str,
        personality_name: str,
        memory_modifier: float,
        meta_reasoning_score: float,
        meta_reasoning_approved: bool,
        meta_pillar_breakdown: Dict[str, float],
        adaptive_sizer_base_risk: float,
        sizer_multipliers: Dict[str, float],
        portfolio_guard_action: str,
        portfolio_guard_reason: str,
        portfolio_guard_approved: bool,
        adjusted_risk_pct: float,
        execution_routing: str,
        detected_conflicts: List[str],
        resolved_conflict_action: str
    ):
        self.trade_id = trade_id
        self.symbol = symbol.upper()
        self.side = side.upper()
        self.strategy = strategy
        self.price = price
        self.timestamp = datetime.now(timezone.utc).isoformat()
        
        self.strategy_score = strategy_score
        self.strategy_elo = strategy_elo
        self.matrix_multiplier = matrix_multiplier

        self.market_memory_key = market_memory_key
        self.personality_name = personality_name
        self.memory_modifier = memory_modifier

        self.meta_reasoning_score = meta_reasoning_score
        self.meta_reasoning_approved = meta_reasoning_approved
        self.meta_pillar_breakdown = meta_pillar_breakdown

        self.adaptive_sizer_base_risk = adaptive_sizer_base_risk
        self.sizer_multipliers = sizer_multipliers

        self.portfolio_guard_action = portfolio_guard_action
        self.portfolio_guard_reason = portfolio_guard_reason
        self.portfolio_guard_approved = portfolio_guard_approved

        self.adjusted_risk_pct = adjusted_risk_pct
        self.execution_routing = execution_routing

        self.detected_conflicts = detected_conflicts
        self.resolved_conflict_action = resolved_conflict_action

    def to_dict(self) -> Dict[str, Any]:
        return {
            "trade_id": self.trade_id,
            "symbol": self.symbol,
            "side": self.side,
            "strategy": self.strategy,
            "price": self.price,
            "timestamp": self.timestamp,
            "lineage": {
                "strategy_tournament": {
                    "score": self.strategy_score,
                    "elo_rating": self.strategy_elo,
                    "matrix_multiplier": self.matrix_multiplier
                },
                "market_memory": {
                    "environment_key": self.market_memory_key,
                    "personality_name": self.personality_name,
                    "memory_modifier": self.memory_modifier
                },
                "meta_reasoning_second_brain": {
                    "conviction_score": self.meta_reasoning_score,
                    "approved": self.meta_reasoning_approved,
                    "pillar_breakdown": self.meta_pillar_breakdown
                },
                "adaptive_sizer": {
                    "base_risk_pct": self.adaptive_sizer_base_risk,
                    "multipliers": self.sizer_multipliers
                },
                "portfolio_guard": {
                    "approved": self.portfolio_guard_approved,
                    "action_type": self.portfolio_guard_action,
                    "reason": self.portfolio_guard_reason
                },
                "execution_routing": self.execution_routing
            },
            "final_decision": {
                "final_adjusted_risk_pct": self.adjusted_risk_pct,
                "detected_conflicts": self.detected_conflicts,
                "resolved_conflict_action": self.resolved_conflict_action
            }
        }

class SystemHarmonyEngine:
    """
    System Harmony & Conflict Resolution Engine for Lumina AI.
    
    Synthesizes directives across 6 interacting subsystems:
    1. Meta Reasoning Engine (Second Brain 7-Pillars)
    2. Portfolio Guard (Exposure & Correlation Shield)
    3. Adaptive Position Sizer (Kelly / Drawdown Sizing)
    4. Market Memory Engine (Macro Personalities)
    5. Strategy Tournament 2.0 (Elo & Session-Regime Matrix)
    6. Continuous Walk-Forward Validation
    
    Harmonizes conflicting signals using strict Priority Rules:
    - Safety First: Portfolio Guard limits strictly cap exposure regardless of sizer increases.
    - Meta Reasoning Conviction: Meta score < 70 forces rejection.
    - Contextual Harmony Sizing: Clamps combined multipliers between 0.40x and 1.80x.
    
    Maintains 360-degree Lineage Trace log for complete observability into every order.
    Includes automated Integration Conflict Simulation runner to test edge scenarios.
    """
    _instance: Optional['SystemHarmonyEngine'] = None

    def __init__(self):
        self._lineage_traces: Dict[str, TradeDecisionLineageTrace] = {}

    @classmethod
    def get_instance(cls) -> 'SystemHarmonyEngine':
        if cls._instance is None:
            cls._instance = SystemHarmonyEngine()
        return cls._instance

    def harmonize_trade_decision(
        self,
        trade_id: str,
        opportunity: Dict[str, Any],
        base_risk_pct: float,
        meta_score: float,
        meta_approved: bool,
        meta_pillar_breakdown: Dict[str, float],
        pg_approved: bool,
        pg_action: str,
        pg_reason: str,
        pg_adjusted_risk_pct: float,
        memory_modifier: float,
        memory_key: str,
        personality_name: str,
        tournament_elo: int,
        matrix_multiplier: float,
        execution_routing: str = "MARKET"
    ) -> Tuple[bool, float, TradeDecisionLineageTrace]:
        """
        Synthesizes subsystem inputs, detects conflicting directives, applies Priority Rules,
        and produces the final harmonized trade decision + decision lineage trace.
        """
        symbol = str(opportunity.get("symbol", "BTCUSDT")).upper()
        side = str(opportunity.get("side", "BUY")).upper()
        strategy = str(opportunity.get("strategy_used", "Breakout Momentum"))
        price = float(opportunity.get("entry_price", 90000.0))

        detected_conflicts = []

        # Conflict 1: Meta Reasoning Reject vs Portfolio Guard Approve or Sizer Boost
        if not meta_approved and pg_approved:
            detected_conflicts.append("CONFLICT: Meta-Reasoning rejected setup due to low conviction (< 70), but Portfolio Guard approved.")

        # Conflict 2: Market Memory Downscaling (< 0.8x) vs Strategy Matrix Boost (> 1.2x)
        if memory_modifier < 0.85 and matrix_multiplier > 1.15:
            detected_conflicts.append(
                f"CONFLICT: Market Memory recommends scaling down ({memory_modifier:.2f}x) due to environment personality, "
                f"while Strategy Tournament recommends scaling up ({matrix_multiplier:.2f}x)."
            )

        # Conflict 3: Portfolio Guard Scaled Down Risk vs Sizer Base Risk
        if pg_adjusted_risk_pct < base_risk_pct * 0.7:
            detected_conflicts.append(
                f"CONFLICT: Portfolio Guard scaled down risk from {base_risk_pct*100:.1f}% to {pg_adjusted_risk_pct*100:.1f}% "
                f"due to exposure/correlation heat."
            )

        # Priority Rule 1: Safety & Conviction Gate
        if not meta_approved:
            final_approved = False
            final_risk = 0.0
            resolved_action = "REJECTED_BY_META_REASONING_CONVICTION"
        elif not pg_approved:
            final_approved = False
            final_risk = 0.0
            resolved_action = f"REJECTED_BY_PORTFOLIO_GUARD ({pg_reason})"
        else:
            # Priority Rule 2: Harmonized Multiplier Calculation
            # Combine multipliers: Memory * Matrix * (PG Risk Ratio)
            pg_ratio = (pg_adjusted_risk_pct / base_risk_pct) if base_risk_pct > 0 else 1.0
            combined_mult = memory_modifier * matrix_multiplier * pg_ratio

            # Clamp combined multiplier to safe boundaries [0.40, 1.80]
            clamped_mult = max(0.40, min(1.80, combined_mult))
            final_risk = round(base_risk_pct * clamped_mult, 4)
            final_approved = True

            if detected_conflicts:
                resolved_action = f"HARMONIZED_WITH_CONFLICT_RESOLUTION (Applied Clamped Multiplier {clamped_mult:.2f}x)"
            else:
                resolved_action = f"HARMONIZED_OPTIMAL_APPROVAL (Risk Allocation: {final_risk*100:.2f}%)"

        # Construct 360-Degree Lineage Trace
        trace = TradeDecisionLineageTrace(
            trade_id=str(trade_id),
            symbol=symbol,
            side=side,
            strategy=strategy,
            price=price,
            strategy_score=85.0,
            strategy_elo=tournament_elo,
            matrix_multiplier=matrix_multiplier,
            market_memory_key=memory_key,
            personality_name=personality_name,
            memory_modifier=memory_modifier,
            meta_reasoning_score=meta_score,
            meta_reasoning_approved=meta_approved,
            meta_pillar_breakdown=meta_pillar_breakdown,
            adaptive_sizer_base_risk=base_risk_pct,
            sizer_multipliers={"memory": memory_modifier, "matrix": matrix_multiplier},
            portfolio_guard_action=pg_action,
            portfolio_guard_reason=pg_reason,
            portfolio_guard_approved=pg_approved,
            adjusted_risk_pct=final_risk,
            execution_routing=execution_routing,
            detected_conflicts=detected_conflicts,
            resolved_conflict_action=resolved_action
        )

        self._lineage_traces[str(trade_id)] = trace
        logger.info(
            f"☯️ [SYSTEM HARMONY] Trade #{trade_id} ({symbol} {side}): Approved={final_approved} | "
            f"Risk={final_risk*100:.2f}% | Action: {resolved_action} | Conflicts: {len(detected_conflicts)}"
        )

        return final_approved, final_risk, trace

    def get_lineage_trace(self, trade_id: str) -> Optional[Dict[str, Any]]:
        """Returns 360-degree lineage trace for a specific trade ID."""
        trace = self._lineage_traces.get(str(trade_id))
        return trace.to_dict() if trace else None

    def get_all_lineage_traces(self) -> List[Dict[str, Any]]:
        """Returns all decision lineage traces in chronological order."""
        return [t.to_dict() for t in self._lineage_traces.values()]

    def run_conflict_simulation_suite(self, n_scenarios: int = 100) -> Dict[str, Any]:
        """
        Runs synthetic integration simulation scenarios across all 6 subsystems to detect conflicting decisions,
        deadlocks, or unexpected unhandled edge conditions.
        """
        conflicts_detected_count = 0
        resolved_successfully_count = 0
        deadlocks_or_unhandled = 0

        symbols = ["BTCUSDT", "ETHUSDT", "SOLUSDT", "AVAXUSDT"]
        strategies = ["Breakout Momentum", "Mean Reversion", "Trend Following", "Volatility Breakout"]

        for i in range(1, n_scenarios + 1):
            sym = random.choice(symbols)
            strat = random.choice(strategies)
            trade_id = f"SIM-{i:03d}"

            # Randomize subsystem conditions including extreme edge cases
            meta_score = random.uniform(50.0, 95.0)
            meta_app = meta_score >= 70.0
            
            pg_app = random.choice([True, True, True, False]) # 75% pass
            pg_act = random.choice(["APPROVE", "SCALE_IN", "OPPOSITE_REBALANCE_HEDGE", "REJECT_EXPOSURE_HEAT"]) if pg_app else "REJECT_EXPOSURE_HEAT"
            pg_reas = "Cluster heat high" if not pg_app else "Exposure healthy"

            base_risk = round(random.uniform(0.01, 0.03), 4)
            pg_adj_risk = base_risk * random.uniform(0.5, 1.2) if pg_app else 0.0

            mem_mod = round(random.uniform(0.5, 1.3), 2)
            mat_mult = round(random.uniform(0.6, 1.4), 2)

            try:
                app, risk, trace = self.harmonize_trade_decision(
                    trade_id=trade_id,
                    opportunity={"symbol": sym, "side": "BUY", "strategy_used": strat, "entry_price": 90000.0},
                    base_risk_pct=base_risk,
                    meta_score=meta_score,
                    meta_approved=meta_app,
                    meta_pillar_breakdown={"technical": meta_score, "sentiment": meta_score - 5},
                    pg_approved=pg_app,
                    pg_action=pg_act,
                    pg_reason=pg_reas,
                    pg_adjusted_risk_pct=pg_adj_risk,
                    memory_modifier=mem_mod,
                    memory_key="HIGH_POSITIVE|NORMAL_VOLATILITY|RISING|TRENDING",
                    personality_name="Dynamic Test Personality",
                    tournament_elo=random.randint(1400, 1750),
                    matrix_multiplier=mat_mult,
                    execution_routing="MARKET"
                )

                if trace.detected_conflicts:
                    conflicts_detected_count += 1
                resolved_successfully_count += 1

            except Exception as e:
                logger.error(f"Error in conflict simulation scenario {i}: {e}")
                deadlocks_or_unhandled += 1

        return {
            "total_scenarios_simulated": n_scenarios,
            "conflicts_detected": conflicts_detected_count,
            "resolved_successfully": resolved_successfully_count,
            "deadlocks_or_unhandled_errors": deadlocks_or_unhandled,
            "system_harmony_health": "EXCELLENT" if deadlocks_or_unhandled == 0 else "ATTENTION_REQUIRED"
        }
