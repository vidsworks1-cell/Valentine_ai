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
        Calculates institutional Capital Ranking Score (0.0 to 100.0) based on:
        - AI Confidence & Confluence (40%)
        - Risk-to-Reward Ratio (25%)
        - Volatility Momentum & Volume Confluence (20%)
        - Spread & Execution Friction Efficiency (15%)
        """
        confidence = float(opportunity.get("confidence_score", opportunity.get("confidence", 0.75)))
        price = float(opportunity.get("price", opportunity.get("entry_price", 1.0)))
        sl = float(opportunity.get("stop_loss", price * 0.98))
        tp = float(opportunity.get("take_profit", price * 1.04))

        # Risk-to-Reward Ratio score
        risk = abs(price - sl) if abs(price - sl) > 0 else 0.01 * price
        reward = abs(tp - price)
        rrr = reward / risk if risk > 0 else 1.5
        rrr_score = min(1.0, max(0.0, (rrr - 1.0) / 2.5))  # Normalized: 1.0 RRR -> 0, 3.5 RRR -> 1.0

        # RVOL / Volatility Momentum score
        rvol = float(opportunity.get("rvol", 1.5))
        rvol_score = min(1.0, max(0.0, (rvol - 0.8) / 2.2))

        # Spread & Liquidity Score
        spread_bps = float(opportunity.get("spread_bps", 6.0))
        spread_score = max(0.0, 1.0 - (spread_bps / 25.0))

        composite = (
            (confidence * 40.0) +
            (rrr_score * 25.0) +
            (rvol_score * 20.0) +
            (spread_score * 15.0)
        )
        return round(composite, 2)

    async def enqueue_opportunity(self, opportunity: Dict[str, Any], reason: str = "Capacity Limit") -> Optional[QueuedOpportunity]:
        """Adds or updates an opportunity in the priority queue."""
        async with self._lock:
            symbol = opportunity.get("symbol", "")
            if not symbol:
                return None

            # Calculate institutional rank score
            rank_score = self.calculate_capital_rank_score(opportunity)
            
            # Remove any existing stale entry for the same symbol
            self._queue = [q for q in self._queue if q.symbol != symbol and not q.is_expired]

            queued_item = QueuedOpportunity(opportunity, rank_score)
            self._queue.append(queued_item)
            self._total_enqueued += 1

            # Sort descending by rank score (highest quality first)
            self._queue.sort(key=lambda q: q.rank_score, reverse=True)

            # Cap queue capacity
            if len(self._queue) > self.MAX_QUEUE_CAPACITY:
                dropped = self._queue.pop()
                logger.info(f"Opportunity queue capped. Dropped lowest-ranked candidate: {dropped.symbol} ({dropped.rank_score})")

            logger.info(
                f"📥 [OPPORTUNITY ENQUEUED] {symbol} placed in Opportunity Queue (Rank Score: {rank_score:.1f}, "
                f"Reason: {reason}, Queue Size: {len(self._queue)})."
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
        """Validates price freshness and dispatches candidate to OrderManager."""
        try:
            from app.trading.price_cache import PriceCache
            from app.trading.order_manager import OrderManager
            from app.scheduler.manager import LayeredSchedulerManager

            scheduler = await LayeredSchedulerManager.get_instance()
            active_mode = getattr(scheduler, "global_trading_mode", "PAPER")

            cache = await PriceCache.get_instance()
            current_price = cache.get_price(candidate.symbol)

            # Price drift guard: Do not enter if price drifted more than 0.35% from initial signal
            if current_price > 0.0 and candidate.entry_price > 0.0:
                drift_pct = abs(current_price - candidate.entry_price) / candidate.entry_price * 100.0
                if drift_pct > 0.35:
                    logger.warning(
                        f"⏳ [QUEUE DRIFT GUARD] Candidate {candidate.symbol} price drifted {drift_pct:.2f}% "
                        f"(Signal: {candidate.entry_price}, Current: {current_price}). Discarding stale entry."
                    )
                    await self.pop_candidate_for_symbol(candidate.symbol)
                    return
                # Update candidate entry price to live market price
                candidate.opportunity["price"] = current_price
                candidate.opportunity["entry_price"] = current_price

            logger.info(
                f"🚀 [QUEUE PROMOTED TO EXECUTION] Rank #1 Candidate {candidate.symbol} (Score: {candidate.rank_score:.1f}) "
                f"dispatched to OrderManager after capital release."
            )
            
            # Remove from queue
            await self.pop_candidate_for_symbol(candidate.symbol)
            self._total_dequeued_and_executed += 1

            # Dispatch order
            res = await OrderManager.get_instance().execute_order(candidate.opportunity, active_mode)
            logger.info(f"Queue promotion execution result for {candidate.symbol}: {res.get('status', 'EXECUTED')}")
        except Exception as e:
            logger.error(f"Error processing queued opportunity {candidate.symbol}: {e}", exc_info=True)

    def get_status_summary(self) -> Dict[str, Any]:
        """Provides monitoring telemetry for the Opportunity Queue."""
        return {
            "queue_depth": len(self._queue),
            "total_enqueued": self._total_enqueued,
            "total_promoted_and_executed": self._total_dequeued_and_executed,
            "total_expired": self._total_expired,
            "candidates": [item.to_dict() for item in self._queue]
        }
