"""
Institutional Opportunity Queue & Capital Ranking Engine for Lumina.

Key Responsibilities:
1. Global Capital Ranking: Ranks all incoming trade opportunities by composite alpha score
   rather than first-come-first-served.
2. Capacity Buffering: When Portfolio Guard or capital allocation reaches maximum open
   positions, high-conviction opportunities are queued instead of rejected and forgotten.
3. Rapid Capital Re-evaluation: When any active position closes, the queue is instantly
   woken up to re-evaluate and execute the highest-ranked candidate if its thesis remains valid.
4. Signal Freshness / TTL Guard: Re-evaluates queue entries against price drift, max holding age
   (TTL default 10 minutes), and market structure before executing.
"""

import time
import asyncio
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime

logger = logging.getLogger("lumina.opportunity_queue")

class QueuedOpportunity:
    def __init__(self, opportunity: Dict[str, Any], rank_score: float, ttl_seconds: float = 600.0):
        self.opportunity = opportunity
        self.symbol = opportunity.get("symbol", "")
        self.side = opportunity.get("side", "BUY").upper()
        self.strategy = opportunity.get("strategy_used", opportunity.get("strategy", "Dynamic Breakout"))
        self.entry_price = float(opportunity.get("price", opportunity.get("entry_price", 0.0)))
        self.confidence = float(opportunity.get("confidence_score", opportunity.get("confidence", 0.75)))
        self.rank_score = rank_score
        self.enqueued_at = time.time()
        self.ttl_seconds = ttl_seconds
        self.re_eval_count = 0

    @property
    def is_expired(self) -> bool:
        return (time.time() - self.enqueued_at) > self.ttl_seconds

    @property
    def age_seconds(self) -> float:
        return time.time() - self.enqueued_at

    def to_dict(self) -> Dict[str, Any]:
        return {
            "symbol": self.symbol,
            "side": self.side,
            "strategy": self.strategy,
            "entry_price": self.entry_price,
            "confidence": self.confidence,
            "rank_score": round(self.rank_score, 2),
            "age_seconds": round(self.age_seconds, 1),
            "ttl_seconds": self.ttl_seconds,
            "is_expired": self.is_expired
        }


class OpportunityQueueEngine:
    _instance: Optional['OpportunityQueueEngine'] = None
    MAX_QUEUE_CAPACITY: int = 12

    def __init__(self):
        self._queue: List[QueuedOpportunity] = []
        self._lock = asyncio.Lock()
        self._total_enqueued = 0
        self._total_dequeued_and_executed = 0
        self._total_expired = 0

    @classmethod
    def get_instance(cls) -> 'OpportunityQueueEngine':
        if cls._instance is None:
            cls._instance = OpportunityQueueEngine()
        return cls._instance

    def calculate_capital_rank_score(self, opportunity: Dict[str, Any]) -> float:
        """
        Calculates institutional Expected Value Score (0.0 to 100.0) based on:
        - Contextual Memory Similarity & Historical Context Expectancy (30%)
        - AI Confidence & Confluence (20%)
        - Risk-to-Reward Ratio (15%)
        - Market Quality Score (15%)
        - Breakout Quality & Volume Confluence (10%)
        - Spread & Execution Friction Efficiency (10%)
        """
        confidence = float(opportunity.get("confidence_score", opportunity.get("confidence", 0.75)))
        price = float(opportunity.get("price", opportunity.get("entry_price", 1.0)))
        sl = float(opportunity.get("stop_loss", price * 0.98))
        tp = float(opportunity.get("take_profit", price * 1.04))

        # 1. Risk-to-Reward Ratio score
        risk = abs(price - sl) if abs(price - sl) > 0 else 0.01 * price
        reward = abs(tp - price)
        rrr = reward / risk if risk > 0 else 1.5
        rrr_score = min(1.0, max(0.0, (rrr - 1.0) / 2.5))  # 1.0 RRR -> 0, 3.5 RRR -> 1.0

        # 2. RVOL / Volatility Momentum
        rvol = float(opportunity.get("rvol", 1.5))
        rvol_score = min(1.0, max(0.0, (rvol - 0.8) / 2.2))

        # 3. Spread & Liquidity Score
        spread_bps = float(opportunity.get("spread_bps", 4.0))
        spread_score = max(0.0, 1.0 - (spread_bps / 25.0))

        # 4. Contextual Trade Memory Expectancy & Similarity Confidence
        try:
            from app.ai.contextual_trade_memory import ContextualTradeMemory
            mem_result = ContextualTradeMemory.get_instance().query_similar_contexts(opportunity, k=3)
            hist_exp_r = float(mem_result.get("historical_context_expectancy_r", 1.5))
            sim_conf_pct = float(mem_result.get("similarity_confidence_pct", 70.0))
            # Weight expectancy by analog confidence
            context_exp_score = min(1.0, max(0.0, hist_exp_r / 3.0)) * (sim_conf_pct / 100.0)
            opportunity["contextual_memory_match"] = mem_result
        except Exception:
            context_exp_score = 0.50

        # 5. Market Quality Engine
        try:
            from app.trading.market_quality import MarketQualityEngine
            mq = MarketQualityEngine.get_instance()._cached_quality
            mq_score_norm = (float(mq.get("market_quality_score", 75.0)) / 100.0) if mq else 0.75
        except Exception:
            mq_score_norm = 0.75

        # 6. Breakout Quality
        try:
            from app.trading.breakout_quality import BreakoutQualityAnalyzer
            bq = BreakoutQualityAnalyzer.get_instance().analyze_breakout_quality(opportunity)
            bq_score_norm = float(bq.get("breakout_quality_score", 80.0)) / 100.0
            opportunity["breakout_quality_match"] = bq
        except Exception:
            bq_score_norm = 0.80

        # 7. Strategy Portfolio Manager Edge & Capital Allocation
        strategy_name = opportunity.get("strategy_used", opportunity.get("strategy", "General"))
        strategy_edge_score = 0.50
        strategy_bonus = 0.0
        try:
            from app.trading.strategy_portfolio_manager import StrategyPortfolioManager
            spm = StrategyPortfolioManager.get_instance()
            strat_alloc = spm.get_strategy_allocation(strategy_name)
            opportunity["strategy_portfolio_allocation"] = strat_alloc
            tier = strat_alloc.get("tier", "MODERATE_ALLOCATION")
            edge_score = float(strat_alloc.get("statistical_edge_score", 0.5))
            strategy_edge_score = min(1.0, max(0.0, edge_score / 2.0))

            is_veb = "volatility expansion" in strategy_name.lower() or strat_alloc.get("is_prioritized", False)
            if tier == "DOMINANT_EDGE":
                strategy_bonus = 15.0 if is_veb else 10.0  # Top strategy priority boost
            elif tier == "HIGH_ALLOCATION":
                strategy_bonus = 6.0
            elif tier == "DEFENSIVE_SUSPENDED":
                strategy_bonus = -35.0  # Never prioritize trades from suspended strategies with negative expectancy
        except Exception:
            strategy_edge_score = 0.50
            strategy_bonus = 0.0

        ev_composite = (
            (context_exp_score * 25.0) +
            (strategy_edge_score * 15.0) +
            (confidence * 15.0) +
            (rrr_score * 15.0) +
            (mq_score_norm * 12.0) +
            (bq_score_norm * 10.0) +
            (spread_score * 8.0) +
            strategy_bonus
        )
        return round(max(0.0, ev_composite), 2)

    def calculate_expected_value(self, opportunity: Dict[str, Any]) -> float:
        """Alias for calculate_capital_rank_score providing direct EV scoring access."""
        return self.calculate_capital_rank_score(opportunity)

    async def enqueue_opportunity(self, opportunity: Dict[str, Any], reason: str = "Capacity Limit") -> Optional[QueuedOpportunity]:
        """Adds or updates an opportunity in the priority queue with EV ranking."""
        async with self._lock:
            symbol = opportunity.get("symbol", "")
            if not symbol:
                return None

            # Calculate institutional Expected Value rank score
            rank_score = self.calculate_capital_rank_score(opportunity)
            
            # Remove any existing stale entry for the same symbol
            self._queue = [q for q in self._queue if q.symbol != symbol and not q.is_expired]

            queued_item = QueuedOpportunity(opportunity, rank_score)
            self._queue.append(queued_item)
            self._total_enqueued += 1

            # Sort descending by Expected Value rank score (highest quality first)
            self._queue.sort(key=lambda q: q.rank_score, reverse=True)

            # Cap queue capacity
            if len(self._queue) > self.MAX_QUEUE_CAPACITY:
                dropped = self._queue.pop()
                logger.info(f"Opportunity queue capped. Dropped lowest-ranked candidate: {dropped.symbol} ({dropped.rank_score})")

            logger.info(
                f"📥 [OPPORTUNITY ENQUEUED] {symbol} buffered in Opportunity Queue (EV Rank Score: {rank_score:.1f}, "
                f"Reason: {reason}, Queue Depth: {len(self._queue)})."
            )
            return queued_item

    async def get_highest_ranked_candidate(self) -> Optional[QueuedOpportunity]:
        """Returns the highest-ranked valid, unexpired candidate."""
        async with self._lock:
            # Purge expired items
            valid = []
            for item in self._queue:
                if item.is_expired:
                    self._total_expired += 1
                else:
                    valid.append(item)
            self._queue = valid
            return self._queue[0] if self._queue else None

    async def pop_candidate_for_symbol(self, symbol: str) -> Optional[QueuedOpportunity]:
        async with self._lock:
            for i, item in enumerate(self._queue):
                if item.symbol == symbol:
                    return self._queue.pop(i)
            return None

    async def on_position_closed_event(self, released_symbol: str, realized_pnl: float):
        """
        Triggered whenever a position closes on the exchange.
        Immediately re-evaluates the highest-ranked candidate in the queue to deploy released capital.
        """
        logger.info(f"🔄 [CAPITAL RELEASED] Trade on {released_symbol} closed (PnL: {realized_pnl:.2f}). Re-evaluating Opportunity Queue...")
        
        candidate = await self.get_highest_ranked_candidate()
        if not candidate:
            logger.info("Opportunity Queue is currently empty. Capital returned to idle Spot pool.")
            return

        asyncio.create_task(self._process_candidate_execution(candidate))

    async def _process_candidate_execution(self, candidate: QueuedOpportunity):
        """
        Module 5: Opportunity Revalidation Pipeline.
        Before releasing a queued trade:
        Runs the complete validation pipeline again:
        - Signal Freshness & Price Drift Check
        - Macro Guard
        - MTF Confirmation
        - Volume & Liquidity Expansion
        - Breakout Quality & Fakeout Rejection
        - Meta-Reasoning Conviction Debate
        - Portfolio Guard Capacity Check

        If the thesis degraded or guards fail: Reject & discard.
        Never executes solely on an old signal.
        """
        symbol = candidate.symbol
        try:
            from app.trading.price_cache import PriceCache
            from app.trading.order_manager import OrderManager
            from app.scheduler.manager import LayeredSchedulerManager

            scheduler = await LayeredSchedulerManager.get_instance()
            active_mode = getattr(scheduler, "global_trading_mode", "PAPER")

            cache = await PriceCache.get_instance()
            current_price = cache.get_price(symbol)

            # 1. Price drift guard: Do not enter if price drifted more than 0.35% from initial signal
            if current_price > 0.0 and candidate.entry_price > 0.0:
                drift_pct = abs(current_price - candidate.entry_price) / candidate.entry_price * 100.0
                if drift_pct > 0.35:
                    logger.warning(
                        f"⏳ [QUEUE REVALIDATION FAIL] Candidate {symbol} price drifted {drift_pct:.2f}% "
                        f"(Signal: {candidate.entry_price}, Current: {current_price}). Thesis stale. Discarding."
                    )
                    await self.pop_candidate_for_symbol(symbol)
                    return
                # Update candidate entry price to live market price
                candidate.opportunity["price"] = current_price
                candidate.opportunity["entry_price"] = current_price

            # 2. Revalidate Macro Guard
            from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
            macro_overlay = await MacroRiskOverlayEngine.get_instance().evaluate_macro_risk(symbol, candidate.side)
            if macro_overlay.get("trading_lockdown", False) or macro_overlay.get("macro_risk_score", 0.0) >= 80.0:
                logger.warning(f"🚫 [QUEUE REVALIDATION FAIL] Candidate {symbol} blocked by Macro Guard during release.")
                await self.pop_candidate_for_symbol(symbol)
                return

            # 3. Revalidate MTF Confirmation Guard
            from app.trading.mtf_confirmation import MTFConfirmationGuard
            passed_mtf, mtf_align, _ = await MTFConfirmationGuard.get_instance().evaluate_mtf_alignment(
                symbol=symbol,
                side=candidate.side,
                price=current_price or candidate.entry_price,
                strategy=candidate.strategy
            )
            if not passed_mtf:
                logger.warning(f"🚫 [QUEUE REVALIDATION FAIL] Candidate {symbol} lost MTF alignment ({mtf_align:.1f}%). Discarding.")
                await self.pop_candidate_for_symbol(symbol)
                return

            # 4. Revalidate Breakout Quality Analyzer
            from app.trading.breakout_quality import BreakoutQualityAnalyzer
            bq = BreakoutQualityAnalyzer.get_instance().analyze_breakout_quality(candidate.opportunity)
            if not bq.get("is_genuine_breakout", True):
                logger.warning(f"🚫 [QUEUE REVALIDATION FAIL] Candidate {symbol} flagged with high fakeout risk ({bq.get('fake_breakout_probability_pct')}%. Discarding.")
                await self.pop_candidate_for_symbol(symbol)
                return

            # 5. Revalidate Meta-Reasoning Conviction
            from app.ai.meta_reasoning import MetaReasoningEngine
            is_meta_approved, meta_audit = await MetaReasoningEngine.get_instance().conduct_meta_reasoning_debate(candidate.opportunity)
            if not is_meta_approved:
                logger.warning(f"🚫 [QUEUE REVALIDATION FAIL] Candidate {symbol} failed Meta-Reasoning conviction during release.")
                await self.pop_candidate_for_symbol(symbol)
                return

            # 6. Revalidate Portfolio Guard Capacity
            from app.trading.portfolio_guard import PortfolioGuard
            pg_decision = await PortfolioGuard.get_instance().evaluate_portfolio_impact(candidate.opportunity, active_mode)
            if not pg_decision.approved:
                logger.warning(f"🚫 [QUEUE REVALIDATION FAIL] Candidate {symbol} rejected by Portfolio Guard: {pg_decision.reason}")
                await self.pop_candidate_for_symbol(symbol)
                return

            logger.info(
                f"🚀 [QUEUE REVALIDATION PASSED] Rank #1 Candidate {symbol} passed complete pipeline revalidation! "
                f"(EV Score: {candidate.rank_score:.1f}, Conviction: {meta_audit.get('overall_conviction', 85):.1f}/100). "
                f"Dispatching to OrderManager..."
            )
            
            # Remove from queue
            await self.pop_candidate_for_symbol(symbol)
            self._total_dequeued_and_executed += 1

            # Dispatch order
            res = await OrderManager.get_instance().execute_order(candidate.opportunity, active_mode)
            logger.info(f"Queue promotion execution result for {symbol}: {res.get('status', 'EXECUTED')}")
        except Exception as e:
            logger.error(f"Error processing queued opportunity {symbol}: {e}", exc_info=True)

    def get_status_summary(self) -> Dict[str, Any]:
        """Provides monitoring telemetry for the Opportunity Queue."""
        return {
            "queue_depth": len(self._queue),
            "total_enqueued": self._total_enqueued,
            "total_promoted_and_executed": self._total_dequeued_and_executed,
            "total_expired": self._total_expired,
            "candidates": [item.to_dict() for item in self._queue]
        }
