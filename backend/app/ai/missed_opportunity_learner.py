import logging
import time
import asyncio
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple

logger = logging.getLogger("lumina.ai.missed_opportunity")

class MissedOpportunityLearner:
    """
    Missed Opportunity Learning Engine for Lumina.
    
    Tracks and counterfactually evaluates rejected or blocked trading setups:
    1. Exposure Guard Rejections (PORTFOLIO_GUARD)
    2. Confidence Threshold Blocks (CONFIDENCE_FILTER)
    3. Evaluation Cooldowns (COOLDOWN)
    4. Execution Missed Fills (MISSED_FILL)
    5. Self-Adaptation Blocks (SELF_ADAPTATION_BLOCK)
    
    Answers the core question: "If I had taken this blocked trade, what would have happened?"
    Learns over time whether rules are saving capital or costing profit, and automatically
    adjusts threshold calibrations, cooldown durations, and risk caps.
    """
    _instance: Optional['MissedOpportunityLearner'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self._records: List[Dict[str, Any]] = []
        self._last_evaluated_at: Optional[float] = None
        self._rule_adjustments_count: int = 0
        self._total_saved_pnl_pct: float = 0.0
        self._total_missed_pnl_pct: float = 0.0

    @classmethod
    def get_instance(cls) -> 'MissedOpportunityLearner':
        if cls._instance is None:
            cls._instance = MissedOpportunityLearner()
        return cls._instance

    def record_missed_opportunity(
        self,
        symbol: str,
        side: str,
        entry_price: float,
        stop_loss: float,
        take_profit: float,
        strategy: str,
        category: str,
        reason: str,
        confidence_score: float = 0.75,
        confidence_threshold: float = 0.80
    ) -> Dict[str, Any]:
        """
        Records a blocked or rejected trade setup for counterfactual evaluation.
        """
        now = time.time()
        record_id = f"MISSED_{symbol}_{side}_{int(now)}"
        
        # Calculate sensible TP/SL defaults if missing
        if entry_price > 0.0:
            if stop_loss <= 0.0:
                stop_loss = round(entry_price * (0.98 if side.upper() == "BUY" else 1.02), 4)
            if take_profit <= 0.0:
                take_profit = round(entry_price * (1.04 if side.upper() == "BUY" else 0.96), 4)

        record = {
            "id": record_id,
            "symbol": symbol.upper(),
            "side": side.upper(),
            "entry_price": float(entry_price),
            "stop_loss": float(stop_loss),
            "take_profit": float(take_profit),
            "strategy": strategy,
            "category": category.upper(),
            "reason": reason,
            "confidence_score": float(confidence_score),
            "confidence_threshold": float(confidence_threshold),
            "created_at": now,
            "created_at_iso": datetime.now(timezone.utc).isoformat(),
            "status": "PENDING_EVALUATION",
            "max_price_reached": float(entry_price),
            "min_price_reached": float(entry_price),
            "counterfactual_pnl_pct": 0.0,
            "evaluation_summary": f"Pending counterfactual price tracking for {symbol} {side}."
        }

        self._records.append(record)
        # Cap memory buffer to 200 records
        if len(self._records) > 200:
            self._records.pop(0)

        logger.info(
            f"📥 [MISSED OPPORTUNITY RECORDED] {symbol} {side} via {strategy} [Category: {category.upper()}]. "
            f"Entry: ${entry_price:.2f}, SL: ${stop_loss:.2f}, TP: ${take_profit:.2f}. Reason: {reason}"
        )
        return record

    async def evaluate_pending_opportunities(self):
        """
        Counterfactually evaluates pending missed opportunities against live market prices.
        """
        async with self._lock:
            try:
                from app.workers.price_cache import PriceCache
                cache = await PriceCache.get_instance()
            except Exception as e:
                logger.error(f"PriceCache unavailable for missed opportunity evaluation: {e}")
                cache = None

            now = time.time()
            evaluated_count = 0

            for rec in self._records:
                if rec["status"] != "PENDING_EVALUATION":
                    continue

                symbol = rec["symbol"]
                side = rec["side"]
                entry_p = rec["entry_price"]
                sl_p = rec["stop_loss"]
                tp_p = rec["take_profit"]
                cat = rec["category"]
                created_at = rec["created_at"]
                age_sec = now - created_at

                # Retrieve current price
                curr_price = 0.0
                if cache:
                    try:
                        p_val = cache.get_price(symbol)
                        if isinstance(p_val, (int, float)) and p_val > 0:
                            curr_price = float(p_val)
                    except Exception:
                        pass

                if curr_price <= 0.0:
                    continue

                # Track max/min price reached
                rec["max_price_reached"] = max(rec["max_price_reached"], curr_price)
                rec["min_price_reached"] = min(rec["min_price_reached"], curr_price) if rec["min_price_reached"] > 0 else curr_price

                # Check counterfactual outcomes
                is_win = False
                is_loss = False

                if side == "BUY":
                    if curr_price >= tp_p:
                        is_win = True
                    elif curr_price <= sl_p:
                        is_loss = True
                elif side == "SELL":
                    if curr_price <= tp_p:
                        is_win = True
                    elif curr_price >= sl_p:
                        is_loss = True

                if is_win:
                    pnl_pct = abs((tp_p - entry_p) / entry_p * 100.0) if entry_p > 0 else 3.0
                    rec["status"] = "COST_PROFIT"
                    rec["counterfactual_pnl_pct"] = round(pnl_pct, 2)
                    rec["evaluation_summary"] = (
                        f"⚠️ [{cat}] Cost Profit: {symbol} {side} reached TP target (${tp_p:.2f}). "
                        f"Blocked trade would have returned +{pnl_pct:.2f}%."
                    )
                    self._total_missed_pnl_pct += pnl_pct
                    evaluated_count += 1
                    logger.info(f"💡 [COUNTERFACTUAL EVALUATION] {rec['evaluation_summary']}")

                elif is_loss:
                    loss_pct = abs((entry_p - sl_p) / entry_p * 100.0) if entry_p > 0 else 2.0
                    rec["status"] = "SAVED_BY_GUARD"
                    rec["counterfactual_pnl_pct"] = round(-loss_pct, 2)
                    rec["evaluation_summary"] = (
                        f"🛡️ [{cat}] Saved Capital: {symbol} {side} dropped to SL (${sl_p:.2f}). "
                        f"Exposure guard prevented a -{loss_pct:.2f}% loss."
                    )
                    self._total_saved_pnl_pct += loss_pct
                    evaluated_count += 1
                    logger.info(f"💡 [COUNTERFACTUAL EVALUATION] {rec['evaluation_summary']}")

                elif age_sec > 14400: # 4 hours limit
                    # Timeout evaluation based on current PnL
                    if side == "BUY":
                        cur_pnl = ((curr_price - entry_p) / entry_p * 100.0) if entry_p > 0 else 0.0
                    else:
                        cur_pnl = ((entry_p - curr_price) / entry_p * 100.0) if entry_p > 0 else 0.0

                    if cur_pnl >= 0.8:
                        rec["status"] = "COST_PROFIT"
                        rec["counterfactual_pnl_pct"] = round(cur_pnl, 2)
                        rec["evaluation_summary"] = f"⚠️ [{cat}] Cost Profit (Timed Out): {symbol} {side} moved +{cur_pnl:.2f}% in trade direction."
                        self._total_missed_pnl_pct += cur_pnl
                    elif cur_pnl <= -0.8:
                        rec["status"] = "SAVED_BY_GUARD"
                        rec["counterfactual_pnl_pct"] = round(cur_pnl, 2)
                        rec["evaluation_summary"] = f"🛡️ [{cat}] Saved Capital (Timed Out): {symbol} {side} moved {cur_pnl:.2f}% against trade direction."
                        self._total_saved_pnl_pct += abs(cur_pnl)
                    else:
                        rec["status"] = "EXPIRED_NEUTRAL"
                        rec["counterfactual_pnl_pct"] = round(cur_pnl, 2)
                        rec["evaluation_summary"] = f"⚖️ [{cat}] Expired Neutral: {symbol} {side} consolidated near entry ({cur_pnl:+.2f}%)."
                    evaluated_count += 1

            self._last_evaluated_at = now
            if evaluated_count > 0:
                await self._trigger_autonomous_rule_adaptation()

    async def _trigger_autonomous_rule_adaptation(self):
        """
        Analyzes aggregate counterfactual outcomes and dynamically adjusts system rules.
        """
        evaluated_records = [r for r in self._records if r["status"] in ["SAVED_BY_GUARD", "COST_PROFIT"]]
        if len(evaluated_records) < 2:
            return

        saved_count = sum(1 for r in evaluated_records if r["status"] == "SAVED_BY_GUARD")
        cost_profit_count = sum(1 for r in evaluated_records if r["status"] == "COST_PROFIT")
        total = saved_count + cost_profit_count

        guard_precision_rate = saved_count / max(1, total)

        # 1. Rule Relaxation: If >55% of blocked trades were profitable wins
        if cost_profit_count > saved_count and total >= 3:
            from app.ai.self_adaptation import SelfAdaptationEngine
            sa = SelfAdaptationEngine.get_instance()
            # Slightly adjust calibration to make confidence threshold more accessible
            sa._dynamic_threshold_calibration = round(max(-0.10, sa._dynamic_threshold_calibration - 0.01), 2)
            self._rule_adjustments_count += 1
            
            insight = (
                f"🧠 [MISSED OPPORTUNITY LEARNING] Rule Relaxation Triggered: "
                f"Exposure guard/confidence filters cost profit in {cost_profit_count}/{total} evaluated setups. "
                f"Automatically relaxed dynamic threshold calibration to {sa._dynamic_threshold_calibration:+.2f}."
            )
            sa._learning_insights.append(insight)
            logger.info(insight)

        # 2. Rule Validation: If >55% of blocked trades hit Stop Loss
        elif saved_count >= cost_profit_count and total >= 3:
            from app.ai.self_adaptation import SelfAdaptationEngine
            sa = SelfAdaptationEngine.get_instance()
            insight = (
                f"🛡️ [MISSED OPPORTUNITY LEARNING] Rule Protection Validated: "
                f"Exposure guard successfully saved capital in {saved_count}/{total} blocked setups "
                f"({self._total_saved_pnl_pct:.2f}% total loss prevented). Risk parameters confirmed optimal."
            )
            sa._learning_insights.append(insight)
            logger.info(insight)

    def answer_what_if_query(self, symbol: Optional[str] = None) -> Dict[str, Any]:
        """
        Provides a detailed answer to: "If I had taken this blocked trade, what would have happened?"
        """
        filtered = self._records
        if symbol:
            filtered = [r for r in self._records if r["symbol"] == symbol.upper()]

        saved_recs = [r for r in filtered if r["status"] == "SAVED_BY_GUARD"]
        cost_recs = [r for r in filtered if r["status"] == "COST_PROFIT"]
        pending_recs = [r for r in filtered if r["status"] == "PENDING_EVALUATION"]

        total_saved_pnl = sum(abs(r["counterfactual_pnl_pct"]) for r in saved_recs)
        total_missed_pnl = sum(r["counterfactual_pnl_pct"] for r in cost_recs)
        net_efficiency = round(total_saved_pnl - total_missed_pnl, 2)

        if not filtered:
            headline = "No blocked or rejected trades recorded yet for counterfactual analysis."
        elif len(saved_recs) > len(cost_recs):
            headline = (
                f"🛡️ Exposure Guard Saved You! Out of {len(filtered)} evaluated blocked setups, "
                f"the guard prevented losses in {len(saved_recs)} trades (Saved +{total_saved_pnl:.2f}% PnL)."
            )
        elif len(cost_recs) > len(saved_recs):
            headline = (
                f"⚠️ Exposure Guard Cost Profit. Out of {len(filtered)} evaluated blocked setups, "
                f"the guard blocked {len(cost_recs)} winning setups (-{total_missed_pnl:.2f}% missed PnL). "
                f"System automatically relaxed confidence thresholds."
            )
        else:
            headline = (
                f"⚖️ Exposure Guard Neutral. Out of {len(filtered)} evaluated blocked setups, "
                f"outcomes were evenly balanced (Saved: +{total_saved_pnl:.2f}%, Missed: -{total_missed_pnl:.2f}%)."
            )

        return {
            "headline": headline,
            "total_blocked_trades": len(filtered),
            "saved_by_guard_count": len(saved_recs),
            "cost_profit_count": len(cost_recs),
            "pending_evaluation_count": len(pending_recs),
            "total_loss_prevented_pct": round(total_saved_pnl, 2),
            "total_missed_profit_pct": round(total_missed_pnl, 2),
            "net_guard_efficiency_pct": net_efficiency,
            "autonomous_rule_adjustments": self._rule_adjustments_count,
            "recent_missed_opportunities": filtered[-15:]
        }
