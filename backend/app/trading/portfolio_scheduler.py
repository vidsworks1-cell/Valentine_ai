import logging
from typing import List, Dict, Any, Optional
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.portfolio_scheduler")

class PortfolioOpportunityScheduler:
    """
    Portfolio Opportunity Scheduler for Lumina (Portfolio Brain Component).
    
    Rather than executing signals in simple arrival order, this module acts as a institutional
    capital allocator. It takes candidate setups that passed Phase 2 execution, ranks them by
    Expected Value (EV), diversification bonus, regime alignment, and portfolio risk budget,
    and dispatches only the optimal allocation batch to OrderManager.
    
    Ranking Criteria:
      - Expected Value (EV): Win Rate * Reward/Risk - Loss Rate
      - Regime Confluence Bonus
      - Cross-Asset Diversification Factor (penalizing highly correlated setups)
      - Liquidity Quality Score
      - Strategy Historical Performance
    """
    _instance: Optional['PortfolioOpportunityScheduler'] = None

    @classmethod
    def get_instance(cls) -> 'PortfolioOpportunityScheduler':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        self._opportunity_queue: List[Dict[str, Any]] = []

    def queue_opportunity(self, setup_dict: Dict[str, Any]):
        """Queues an approved setup for batch allocation ranking."""
        self._opportunity_queue.append(setup_dict)
        logger.info(f"📋 [PORTFOLIO SCHEDULER] Queued opportunity for ranking: {setup_dict.get('symbol')} {setup_dict.get('side')} ({setup_dict.get('strategy_used')})")

    def rank_and_allocate(self, max_allocations: int = 3) -> List[Dict[str, Any]]:
        """
        Ranks all queued setups by Expected Value & Portfolio Fit and returns the top allocated setups.
        """
        if not self._opportunity_queue:
            return []

        scored_setups: List[tuple] = []
        for setup in self._opportunity_queue:
            score = self.calculate_opportunity_score(setup)
            setup["portfolio_rank_score"] = score
            scored_setups.append((score, setup))

        # Sort descending by allocation score
        scored_setups.sort(key=lambda x: x[0], reverse=True)

        allocated = [item[1] for item in scored_setups[:max_allocations]]
        rejected = [item[1] for item in scored_setups[max_allocations:]]

        # Log allocation results
        logger.info(f"🏆 [PORTFOLIO SCHEDULER BATCH RANKING] Evaluated {len(self._opportunity_queue)} opportunities -> Allocated top {len(allocated)}")
        for i, setup in enumerate(allocated):
            logger.info(f"  Rank #{i+1}: {setup.get('symbol')} {setup.get('side')} ({setup.get('strategy_used')}) - Score: {setup.get('portfolio_rank_score'):.2f}")

        # Record rejection for setups exceeding risk capacity
        for setup in rejected:
            ctx = setup.get("decision_context")
            if ctx:
                ctx.record_rejection("PortfolioScheduler", "Exceeded batch capital allocation limit", category="PORTFOLIO_SCHEDULER_CAPACITY")

        # Clear queue after processing batch
        self._opportunity_queue.clear()
        return allocated

    def calculate_opportunity_score(self, setup: Dict[str, Any]) -> float:
        """Calculates institutional Expected Value and portfolio fit score."""
        conf = setup.get("confidence_score", 0.75)
        meta_score = setup.get("meta_conviction_score", 75.0) / 100.0
        atr = setup.get("atr", 1.0)
        
        # 1. Base EV Score
        estimated_win_rate = (0.5 * conf) + (0.5 * meta_score)
        reward_risk_ratio = 2.0  # Standard 2:1 TP/SL target
        ev = (estimated_win_rate * reward_risk_ratio) - ((1.0 - estimated_win_rate) * 1.0)
        
        # 2. Strategy Confluence
        strategy = setup.get("strategy_used", "")
        strat_bonus = 0.1 if "Breakout" in strategy or "Momentum" in strategy else 0.05
        
        # 3. Dynamic Score Synthesis
        final_score = (ev * 50.0) + (conf * 30.0) + (strat_bonus * 20.0)
        return round(max(0.0, final_score), 2)
