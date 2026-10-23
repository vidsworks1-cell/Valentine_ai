"""
Strategy Classification Engine.
Distinguishes genuine AI/algorithmic trading strategies from system bookkeeping and operational metadata labels.
"""
from typing import Set

# System bookkeeping metadata labels used for operational tracking, reconciliation, and audit trails.
# These labels MUST NEVER participate in:
# - Strategy substitution
# - Strategy ranking
# - Composite ranking
# - Expectancy comparisons
# - Strategy selection / candidate selection
# - Capital allocation / Portfolio Manager weighting
# - Champion / Challenger Elo competition
SYSTEM_STRATEGY_LABELS: Set[str] = {
    "EXCHANGE_RECONCILIATION_ADOPTED",
    "RECONCILIATION_ADOPTED",
    "MANUAL_ENTRY",
    "MANUAL_LIVE_EXECUTION",
    "POSITIONMANAGER",
    "STANDARD_STRATEGY",
    "STANDARD STRATEGY",
    "UNKNOWN",
    "GENERAL",
    "SYSTEM",
    "E2E_VALIDATION_STRATEGY"
}


def is_system_strategy_label(strategy_name: str) -> bool:
    """
    Returns True if the strategy name is a system metadata or bookkeeping label,
    and False if it is a genuine trading strategy.
    """
    if not strategy_name:
        return True
    
    cleaned = str(strategy_name).strip().upper().replace("-", "_")
    normalized = cleaned.replace(" ", "_")
    
    if normalized in SYSTEM_STRATEGY_LABELS or cleaned in SYSTEM_STRATEGY_LABELS:
        return True
        
    # Heuristic pattern checks for reconciliation or internal system prefixes
    if "RECONCILIATION" in normalized or "ADOPTED" in normalized:
        return True
    if normalized.startswith("SYS_") or normalized.startswith("SYSTEM_"):
        return True
        
    return False


def is_genuine_trading_strategy(strategy_name: str) -> bool:
    """
    Returns True if the strategy is a genuine algorithmic market strategy
    (e.g., Breakout Momentum, Volatility Breakout, Trend Following, Mean Reversion).
    """
    return not is_system_strategy_label(strategy_name)
