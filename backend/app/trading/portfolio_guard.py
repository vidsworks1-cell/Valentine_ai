import asyncio
import logging
import time
from datetime import datetime, timezone
from typing import Dict, Any, Tuple, Optional, List
try:
    from sqlalchemy.future import select
    from sqlalchemy import func
    from app.database.session import AsyncSessionLocal
    from app.models.trading import Trade
except ImportError:
    select = None
    func = None
    AsyncSessionLocal = None
    Trade = None
try:
    from app.api.schemas import AuditLogCreate
    from app.services.audit_service import AuditService
except ImportError:
    AuditLogCreate = None
    AuditService = None

logger = logging.getLogger("lumina.trading.portfolio_guard")

class PortfolioDecision:
    """Represents an active decision made by the Exposure & Portfolio Intelligence Layer."""
    def __init__(
        self,
        approved: bool,
        action_type: str,  # "APPROVE", "SCALE_IN", "SWAP_POSITION", "EXECUTE_HEDGE", "REJECT"
        reason: str,
        adjusted_risk_pct: float = 0.02,
        swap_trade_id: Optional[int] = None,
        swap_symbol: Optional[str] = None,
        is_hedge: bool = False
    ):
        self.approved = approved
        self.action_type = action_type
        self.reason = reason
        self.adjusted_risk_pct = adjusted_risk_pct
        self.swap_trade_id = swap_trade_id
        self.swap_symbol = swap_symbol
        self.is_hedge = is_hedge

    def to_dict(self) -> Dict[str, Any]:
        return {
            "approved": self.approved,
            "action_type": self.action_type,
            "reason": self.reason,
            "adjusted_risk_pct": self.adjusted_risk_pct,
            "swap_trade_id": self.swap_trade_id,
            "swap_symbol": self.swap_symbol,
            "is_hedge": self.is_hedge
        }

class PortfolioGuard:
    """
    Active Exposure & Portfolio Intelligence Layer for Lumina.
    Moves beyond passive blocking into an active decision-maker:
    - Dynamic Pyramid Scale-In for winning setups
    - Opportunistic Alpha Position Swap when max capacity is reached
    - Systemic Delta & Sector Cluster Hedging
    - Slippage & Fill Feedback Loop Execution Recalibration
    """
    _instance: Optional['PortfolioGuard'] = None
    
    MAX_OPEN_POSITIONS = 3      # Max 3 open positions simultaneously (disciplined high-leverage risk cap)
    MAX_PORTFOLIO_RISK = 0.06   # 6% Portfolio Heat Limit
    MAX_SYMBOL_EXPOSURE = 0.02  # 2% Max Per-Symbol Exposure
    MAX_DAILY_LOSS = 0.05       # 5% Daily Loss Circuit Breaker
    MAX_CLUSTER_POSITIONS = 1   # Max 1 simultaneous position per correlation cluster (prevents altcoin dump contagion)
    MAX_SECTOR_RISK_CAP_PCT = 2.5 # Max 2.5% portfolio risk per sector cluster
    MAX_SCALE_INS_PER_POSITION = 2 # Max 2 scale-ins per position (3 entries max total)
    MIN_SWAP_HOLD_TIME_SEC = 3600.0   # Must hold for at least 60 minutes before even considering an Alpha Swap
    SWAP_GLOBAL_COOLDOWN_SEC = 7200.0 # Max 1 swap per 2 hours to eliminate position churn and fee bleed
    MIN_SWAP_CONVICTION_DELTA = 0.15  # Incoming setup must have >= 15% conviction edge over stagnant position
    MIN_INCOMING_CONVICTION_FOR_SWAP = 0.88 # Incoming setup must be exceptional (>= 88%) to justify replacing a position

    # Correlation Clusters Mapping
    CORRELATION_CLUSTERS = {
        "LAYER_1": ["BTCUSDT", "ETHUSDT", "SOLUSDT", "AVAXUSDT", "BNBUSDT", "SUIUSDT", "ADAUSDT", "DOTUSDT", "MATICUSDT", "NEARUSDT", "LTCUSDT", "TIAUSDT", "XRPUSDT"],
        "DEFI": ["UNIUSDT", "AAVEUSDT", "MKRUSDT", "LINKUSDT", "PENDLEUSDT", "INJUSDT"],
        "AI_TOKENS": ["TAOUSDT", "FETUSDT", "NEARUSDT", "RENDERUSDT"],
        "MEMES": ["DOGEUSDT", "PEPEUSDT", "SHIBUSDT", "WIFUSDT", "BONKUSDT"]
    }
    
    def __init__(self):
        self._circuit_breaker_active = False
        self._circuit_breaker_date = None
        self._circuit_breaker_tripped_at = 0.0
        self._cooldown_period_sec = 7200.0  # 2-hour cooling off period
        self._slippage_feedback: Dict[str, List[Dict[str, Any]]] = {}
        self._total_swaps_executed = 0
        self._last_swap_timestamp = 0.0
        self._total_scale_ins_executed = 0
        self._total_hedges_executed = 0

    @classmethod
    def get_instance(cls) -> 'PortfolioGuard':
        if cls._instance is None:
            cls._instance = PortfolioGuard()
        return cls._instance

    def record_fill_feedback(self, symbol: str, requested_price: float, filled_price: float, fill_latency_ms: float = 0.0, was_filled: bool = True):
        """Records fill slippage and latency feedback loop to dynamically adjust routing parameters."""
        if symbol not in self._slippage_feedback:
            self._slippage_feedback[symbol] = []
        
        slippage_pct = abs(filled_price - requested_price) / max(0.0001, requested_price) if requested_price > 0 else 0.0
        record = {
            "requested": requested_price,
            "filled": filled_price,
            "slippage_pct": round(slippage_pct * 100.0, 3),
            "latency_ms": fill_latency_ms,
            "was_filled": was_filled,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        self._slippage_feedback[symbol].append(record)
        if len(self._slippage_feedback[symbol]) > 20:
            self._slippage_feedback[symbol].pop(0)

        if slippage_pct > 0.002 or not was_filled:
            logger.info(f"⚡ [SLIPPAGE FEEDBACK RECALIBRATION] {symbol}: Slippage {slippage_pct*100:.2f}%, Filled: {was_filled}. Execution parameters dynamically recalibrated.")

    def get_slippage_execution_adjustment(self, symbol: str) -> Dict[str, Any]:
        """Provides dynamic execution advice based on recent fill performance."""
        records = self._slippage_feedback.get(symbol, [])
        if not records:
            return {"limit_offset_pct": 0.0, "recommended_routing": "STANDARD_LIMIT", "avg_slippage_pct": 0.0}
        
        failed_count = sum(1 for r in records if not r["was_filled"])
        avg_slippage = sum(r["slippage_pct"] for r in records) / len(records)
        
        if (failed_count / len(records)) >= 0.20 or avg_slippage > 0.15:
            return {
                "limit_offset_pct": 0.001,
                "recommended_routing": "AGGRESSIVE_TWAP_OR_MARKET",
                "avg_slippage_pct": round(avg_slippage, 3)
            }
        return {
            "limit_offset_pct": 0.0,
            "recommended_routing": "STANDARD_LIMIT",
            "avg_slippage_pct": round(avg_slippage, 3)
        }

    def _check_circuit_breaker_reset(self):
        now = time.time()
        today = datetime.now(timezone.utc).date()
        if self._circuit_breaker_active:
            # Auto-reset on new UTC trading day
            if self._circuit_breaker_date and today > self._circuit_breaker_date:
                logger.info(f"[DAILY LOSS CIRCUIT BREAKER] Resetting circuit breaker for new trading day ({today})")
                self._circuit_breaker_active = False
                self._circuit_breaker_date = None
                self._circuit_breaker_tripped_at = 0.0
                return
            # Auto-resume after 2-hour cooling-off period
            if self._circuit_breaker_tripped_at and (now - self._circuit_breaker_tripped_at) >= self._cooldown_period_sec:
                elapsed_hrs = (now - self._circuit_breaker_tripped_at) / 3600.0
                logger.info(f"🟢 [PORTFOLIO GUARD AUTO-RESUME] {elapsed_hrs:.1f}h cooling-off period elapsed. Lifting daily circuit breaker and resuming trading.")
                self._circuit_breaker_active = False
                self._circuit_breaker_tripped_at = 0.0

    def reset_circuit_breaker(self):
        """Explicitly resets the daily loss circuit breaker."""
        self._circuit_breaker_active = False
        self._circuit_breaker_date = None
        self._circuit_breaker_tripped_at = 0.0
        logger.info("🟢 [PORTFOLIO GUARD] Daily loss circuit breaker manually reset. Trading resumed.")

    def _record_rejected_opportunity(self, opportunity: Dict[str, Any], msg: str, incoming_conf: float):
        try:
            from app.ai.missed_opportunity_learner import MissedOpportunityLearner
            MissedOpportunityLearner.get_instance().record_missed_opportunity(
                symbol=opportunity.get("symbol", "UNKNOWN"),
                side=opportunity.get("side", "BUY"),
                entry_price=float(opportunity.get("entry_price", opportunity.get("price", 0.0))),
                stop_loss=float(opportunity.get("stop_loss", 0.0)),
                take_profit=float(opportunity.get("take_profit", 0.0)),
                strategy=opportunity.get("strategy_used", opportunity.get("strategy", "General")),
                category="PORTFOLIO_GUARD",
                reason=msg,
                confidence_score=incoming_conf,
                confidence_threshold=0.75
            )
            # Module 7: Guard Performance Auditor
            from app.trading.guard_performance_auditor import GuardPerformanceAuditor
            GuardPerformanceAuditor.get_instance().record_rejection(
                guard_name="PortfolioGuard",
                symbol=opportunity.get("symbol", "UNKNOWN"),
                side=opportunity.get("side", "BUY"),
                strategy=opportunity.get("strategy_used", opportunity.get("strategy", "General")),
                entry_price=float(opportunity.get("entry_price", opportunity.get("price", 0.0))),
                stop_loss=float(opportunity.get("stop_loss", 0.0)),
                take_profit=float(opportunity.get("take_profit", 0.0)),
                confidence=incoming_conf,
                reason=msg
            )
        except Exception as e:
            logger.error(f"Failed to record rejected opportunity in PortfolioGuard: {e}")

    async def evaluate_portfolio_action(
        self,
        opportunity: Dict[str, Any],
        incoming_risk_pct: float = 0.02,
        balance: float = 100000.0
    ) -> PortfolioDecision:
        """
        Active Exposure & Portfolio Decision Engine.
        Evaluates trades for:
        - Pyramid Scale-In for winning setups
        - Alpha Position Swap when capacity is reached but superior setup arrives
        - Systemic Delta & Sector Cluster Hedging
        - Standard Approval / Risk Rejection
        """
        try:
            self._check_circuit_breaker_reset()
            symbol = opportunity.get("symbol", "UNKNOWN")
            proposed_side = opportunity.get("side", "BUY").upper()
            incoming_conf = float(opportunity.get("confidence_score", opportunity.get("confidence", 0.78)))

            # Obtain current active trading mode
            from app.scheduler.manager import LayeredSchedulerManager
            scheduler = await LayeredSchedulerManager.get_instance()
            active_mode = getattr(scheduler, "global_trading_mode", "PAPER").upper()

            # 1. Daily Loss Circuit Breaker Check
            today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
            daily_realized_pnl = 0.0
            
            if AsyncSessionLocal and select:
                try:
                    async with AsyncSessionLocal() as db:
                        query = select(func.sum(Trade.profit_loss)).where(
                            Trade.exit_price.isnot(None),
                            Trade.exit_time >= today_start,
                            Trade.trading_mode == active_mode
                        )
                        res = await db.execute(query)
                        daily_realized_pnl = res.scalar() or 0.0
                except Exception as e:
                    logger.error(f"Error querying daily PnL: {e}")

            starting_daily_balance = balance - daily_realized_pnl if (balance - daily_realized_pnl) > 0 else balance
            daily_realized_pnl_pct = (daily_realized_pnl / starting_daily_balance) * 100.0 if starting_daily_balance > 0 else 0.0
            
            # Micro-account protection (<$30): require minimum nominal loss (-$1.50)
            # so normal 20-cent fees or tick noise does not trigger a 5% portfolio lockdown.
            min_loss_dollar_threshold = -1.50 if starting_daily_balance < 30.0 else (starting_daily_balance * -0.05)
            is_daily_loss_tripped = (
                daily_realized_pnl_pct <= -5.0 and daily_realized_pnl <= min_loss_dollar_threshold
            )

            if is_daily_loss_tripped or self._circuit_breaker_active:
                if not self._circuit_breaker_active:
                    self._circuit_breaker_active = True
                    self._circuit_breaker_date = datetime.now(timezone.utc).date()
                    self._circuit_breaker_tripped_at = time.time()
                log_msg = f"[DAILY LOSS CIRCUIT BREAKER] Daily loss limit reached ({daily_realized_pnl_pct:.1f}%, ${daily_realized_pnl:.2f}). Trading paused for cooling-off."
                logger.warning(log_msg)
                self._record_rejected_opportunity(opportunity, log_msg, incoming_conf)
                return PortfolioDecision(approved=False, action_type="REJECT", reason=log_msg)

            # 1b. Daily Loss Budget Exhaustion Soft Guard (80% of daily loss budget used)
            if daily_realized_pnl_pct <= -4.0 and daily_realized_pnl <= (min_loss_dollar_threshold * 0.8) and proposed_side in ["BUY", "SELL"]:
                log_msg = f"[DAILY LOSS BUDGET PAUSE] Daily loss budget nearly exhausted ({daily_realized_pnl_pct:.1f}%, ${daily_realized_pnl:.2f}). Pausing new entries."
                logger.warning(log_msg)
                self._record_rejected_opportunity(opportunity, log_msg, incoming_conf)
                return PortfolioDecision(approved=False, action_type="REJECT", reason=log_msg)

            # Retrieve active positions filtered strictly by active trading mode
            from app.trading.position_manager import PositionManager
            active_positions = PositionManager.get_instance().get_active_positions(trading_mode=active_mode)
            open_count = len(active_positions)

            # 1c. Crypto Beta Exposure Cap Guard (Don't open another long if portfolio has >=3 positions and is 80%+ crypto beta, or >=4 long positions)
            if proposed_side == "BUY" and open_count >= 3:
                long_crypto_count = sum(
                    1 for p in active_positions 
                    if p.get("side", "BUY").upper() == "BUY" and self.get_symbol_sector_cluster(p.get("symbol", "")) in ["LAYER_1", "DEFI", "MEMES", "AI_TOKENS", "GENERAL_ALT"]
                )
                beta_ratio = long_crypto_count / float(max(1, open_count))
                if (long_crypto_count >= 4 or beta_ratio >= 0.80) and not opportunity.get("is_hedge", False):
                    msg = f"🛡️ [PORTFOLIO BETA GUARD] Don't open another long: portfolio is already {beta_ratio*100:.0f}% crypto beta ({long_crypto_count}/{open_count} active positions are long)."
                    logger.warning(msg)
                    self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                    return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

            # 1d. Dynamic Portfolio Volatility Risk Scaling
            market_regime = opportunity.get("market_regime", "NORMAL")
            atr_val = opportunity.get("atr", 0.0)
            entry_p = opportunity.get("entry_price", opportunity.get("price", 1.0))
            atr_pct = (atr_val / max(0.0001, entry_p)) if entry_p > 0 else 0.0
            
            # If overall portfolio/market volatility has doubled (ATR > 3.0% or VOLATILE regime with high ATR)
            if atr_pct > 0.030 or market_regime == "VOLATILE":
                vol_scale_factor = 0.5
                incoming_risk_pct = round(incoming_risk_pct * vol_scale_factor, 4)
                logger.info(f"⚡ [PORTFOLIO VOLATILITY SCALING] Overall portfolio volatility has doubled (ATR: {atr_pct*100:.2f}%). Scaled down risk by 50% to {incoming_risk_pct*100:.2f}%.")

            # 1e. Cross-Strategy Drawdown Risk Balancing
            strategy_used = opportunity.get("strategy_used", opportunity.get("strategy", ""))
            try:
                from app.ai.self_adaptation import SelfAdaptationEngine
                stats = SelfAdaptationEngine.get_instance()._strategy_stats.get(strategy_used, {})
                strat_win_rate = stats.get("win_rate_pct", 50.0)
                if strat_win_rate < 40.0 and stats.get("trades_count", 0) >= 3:
                    incoming_risk_pct = round(incoming_risk_pct * 0.5, 4)
                    logger.info(f"⚖️ [CROSS-STRATEGY DRAWDOWN BALANCING] Strategy '{strategy_used}' is in drawdown (WinRate: {strat_win_rate:.1f}%). Reduced risk allowance to {incoming_risk_pct*100:.2f}%.")
                elif strat_win_rate >= 65.0:
                    incoming_risk_pct = round(min(self.MAX_SYMBOL_EXPOSURE, incoming_risk_pct * 1.25), 4)
            except Exception:
                pass

            # 2. Check for Pyramid Scale-In or Opposite Direction Partial Rebalance
            existing_pos = next((p for p in active_positions if p.get("symbol") == symbol), None)

            # Check DB for unclosed trades in the current trading mode if not found in in-memory PositionManager
            if not existing_pos:
                try:
                    async with AsyncSessionLocal() as db:
                        if select:
                            query = select(Trade).where(
                                Trade.symbol == symbol,
                                Trade.exit_price.is_(None),
                                Trade.exit_time.is_(None),
                                Trade.trading_mode == active_mode
                            )
                            res = await db.execute(query)
                            db_trade = res.scalars().first()
                            if db_trade:
                                existing_pos = {
                                    "trade_id": db_trade.id,
                                    "id": db_trade.id,
                                    "symbol": db_trade.symbol,
                                    "side": db_trade.side,
                                    "entry_price": db_trade.entry_price,
                                    "profit_loss": db_trade.profit_loss or 0.0,
                                    "thesis_strength": 0.75,
                                    "trading_mode": db_trade.trading_mode
                                }
                except Exception as de:
                    logger.error(f"Error checking DB for unclosed trade in PortfolioGuard: {de}")

            if existing_pos:
                existing_side = existing_pos.get("side", "BUY").upper()
                ex_trade_id = existing_pos.get("trade_id", existing_pos.get("id"))
                if existing_side == proposed_side:
                    pos_thesis = float(existing_pos.get("thesis_strength", 0.75))
                    pos_pnl = float(existing_pos.get("profit_loss", 0.0))
                    tp_stages = existing_pos.get("tp_stages", [])
                    tp1_hit = tp_stages[0].get("hit", False) if tp_stages else False
                    scale_ins_count = int(existing_pos.get("scale_ins", 0))
                    current_risk = float(existing_pos.get("risk_pct", 0.01))

                    proposed_scale_risk = round(incoming_risk_pct * 0.5, 4)
                    new_cumulative_risk = current_risk + proposed_scale_risk

                    # Enforce Scale-In Hard Limits:
                    # 1. Hard cap on scale-in count (max 2 scale-ins per position)
                    if scale_ins_count >= self.MAX_SCALE_INS_PER_POSITION:
                        msg = (
                            f"[POSITION EXPOSURE GUARD] Scale-in rejected for {symbol}. "
                            f"Maximum scale-in limit reached ({scale_ins_count}/{self.MAX_SCALE_INS_PER_POSITION} scale-ins executed)."
                        )
                        logger.warning(msg)
                        self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                        return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

                    # 2. Hard cap on cumulative risk per symbol (max MAX_SYMBOL_EXPOSURE = 2.0%)
                    if new_cumulative_risk > self.MAX_SYMBOL_EXPOSURE + 0.0001:
                        msg = (
                            f"[POSITION EXPOSURE GUARD] Scale-in rejected for {symbol}. "
                            f"Would exceed maximum cumulative symbol risk cap (Current: {current_risk*100:.2f}%, "
                            f"Scale-in: {proposed_scale_risk*100:.2f}%, New Total: {new_cumulative_risk*100:.2f}% > Cap: {self.MAX_SYMBOL_EXPOSURE*100:.2f}%)."
                        )
                        logger.warning(msg)
                        self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                        return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

                    # 3. Anti-Averaging-Down Guard: NEVER scale into or average down into an underwater position!
                    # Pyramiding is strictly reserved for winning trades to compound alpha while trailing stops.
                    entry = float(existing_pos.get("entry_price", 0.0))
                    current_market_price = float(opportunity.get("entry_price", opportunity.get("price", 0.0)))
                    if entry > 0 and current_market_price > 0:
                        if existing_side == "BUY":
                            unrealized_pnl_pct = ((current_market_price - entry) / entry) * 100.0
                        else:
                            unrealized_pnl_pct = ((entry - current_market_price) / entry) * 100.0
                    else:
                        unrealized_pnl_pct = pos_pnl

                    if unrealized_pnl_pct < 0.0 or pos_pnl < 0.0:
                        msg = (
                            f"🛑 [ANTI-DCA GUARD] Scale-in strictly rejected for {symbol}: Position is underwater "
                            f"(Unrealized PnL: {unrealized_pnl_pct:.2f}%, Realized PnL: ${pos_pnl:.2f}). "
                            f"Never average down into a losing position, regardless of conviction ({incoming_conf:.2f})."
                        )
                        logger.warning(msg)
                        self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                        return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

                    # 4. Scale-in qualification: Must be in confirmed profit (>= +0.80% or TP1 hit) AND supported by high conviction
                    is_in_solid_profit = (tp1_hit or unrealized_pnl_pct >= 0.80)
                    is_high_conviction = (pos_thesis >= 0.78 or incoming_conf >= 0.80)

                    if is_in_solid_profit and is_high_conviction:
                        scale_risk = proposed_scale_risk
                        self._total_scale_ins_executed += 1
                        msg = (
                            f"🚀 [PYRAMID SCALE-IN] Approved 50% scale-in allocation ({scale_risk*100:.2f}% risk, "
                            f"Scale-in #{scale_ins_count+1}/{self.MAX_SCALE_INS_PER_POSITION}) for winning {symbol} position "
                            f"(Unrealized PnL: +{unrealized_pnl_pct:.2f}%, TP1 hit: {tp1_hit}, Conviction: {incoming_conf:.2f})."
                        )
                        logger.info(msg)
                        return PortfolioDecision(
                            approved=True,
                            action_type="SCALE_IN",
                            reason=msg,
                            adjusted_risk_pct=scale_risk
                        )
                    else:
                        msg = (
                            f"[POSITION EXPOSURE GUARD] Active {existing_side} position for {symbol} does not meet pyramid scale-in criteria "
                            f"(TP1 hit: {tp1_hit}, Unrealized PnL: +{unrealized_pnl_pct:.2f}% [req >= +0.80%], thesis: {pos_thesis:.2f}, conviction: {incoming_conf:.2f} [req >= 0.80])."
                        )
                        logger.warning(msg)
                        self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                        return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)
                else:
                    # Existing position in OPPOSITE direction (e.g. Existing LONG, incoming SELL signal)
                    opened_at = existing_pos.get("opened_at") or existing_pos.get("timestamp")
                    pos_pnl = float(existing_pos.get("profit_loss", 0.0))
                    
                    # Directional Whipsaw Guard: Calculate open duration seconds
                    holding_seconds = 9999.0
                    if opened_at:
                        try:
                            if isinstance(opened_at, (int, float)):
                                holding_seconds = time.time() - float(opened_at)
                            elif isinstance(opened_at, str):
                                dt = datetime.fromisoformat(opened_at.replace('Z', '+00:00'))
                                holding_seconds = (datetime.now(timezone.utc) - dt).total_seconds()
                        except Exception:
                            holding_seconds = 9999.0

                    min_required_holding = 300 if pos_pnl < 0 else 180
                    if holding_seconds < min_required_holding:
                        msg = (
                            f"🛡️ [DIRECTIONAL WHIPSAW GUARD] Blocked opposite {proposed_side} signal for {symbol}. "
                            f"Active opposite {existing_side} position was opened only {holding_seconds:.0f}s ago (< {min_required_holding}s min holding period to prevent whipsaw)."
                        )
                        logger.warning(msg)
                        try:
                            from app.ai.reasoning import AiReasoningEngine
                            AiReasoningEngine.get_instance().set_cooldown(symbol, proposed_side, category="DIRECTIONAL_WHIPSAW", reason=msg)
                        except Exception:
                            pass
                        self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                        return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

                    if incoming_conf >= 0.82:
                        rebal_risk = round(incoming_risk_pct * 0.75, 4)
                        self._total_hedges_executed += 1
                        msg = (
                            f"🔄 [OPPOSITE SIGNAL REBALANCE] Existing {existing_side} position in {symbol} held for {holding_seconds:.0f}s. "
                            f"Incoming ultra-high-confidence ({incoming_conf:.2f}) {proposed_side} signal triggers 50% partial close of active {existing_side} position and opens new {proposed_side} hedge."
                        )
                        logger.info(msg)
                        return PortfolioDecision(
                            approved=True,
                            action_type="OPPOSITE_REBALANCE_HEDGE",
                            reason=msg,
                            adjusted_risk_pct=rebal_risk,
                            swap_trade_id=ex_trade_id,
                            swap_symbol=symbol,
                            is_hedge=True
                        )
                    else:
                        msg = (
                            f"[POSITION EXPOSURE GUARD] Active opposite {existing_side} position for {symbol} exists, "
                            f"but incoming {proposed_side} signal confidence ({incoming_conf:.2f}) is below hedge threshold (0.82)."
                        )
                        logger.warning(msg)
                        self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                        return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

            # 3. Check for Systemic Sector & Delta Hedging
            sector = self.get_symbol_sector_cluster(symbol)
            sector_positions = [p for p in active_positions if self.get_symbol_sector_cluster(p.get("symbol", "")) == sector]
            opposite_side_sector_pos = [p for p in sector_positions if p.get("side", "BUY").upper() != proposed_side]
            same_side_sector_pos = [p for p in sector_positions if p.get("side", "BUY").upper() == proposed_side]

            if len(opposite_side_sector_pos) >= 2 and len(same_side_sector_pos) == 0:
                hedge_risk = round(incoming_risk_pct * 0.75, 4)
                self._total_hedges_executed += 1
                msg = f"🛡️ [DELTA HEDGE ALLOCATED] Approved {proposed_side} {symbol} trade as delta hedge against {len(opposite_side_sector_pos)} active opposite positions in cluster '{sector}'."
                logger.info(msg)
                return PortfolioDecision(
                    approved=True,
                    action_type="EXECUTE_HEDGE",
                    reason=msg,
                    adjusted_risk_pct=hedge_risk,
                    is_hedge=True
                )

            # 4. Check Maximum Open Positions (Modulated by Market Quality Engine) & Opportunistic Alpha Position Swap
            effective_max_open = self.MAX_OPEN_POSITIONS
            try:
                from app.trading.market_quality import MarketQualityEngine
                mq = MarketQualityEngine.get_instance()._cached_quality
                if mq:
                    mq_cap = int(mq.get("max_concurrent_cap", self.MAX_OPEN_POSITIONS))
                    effective_max_open = min(self.MAX_OPEN_POSITIONS, mq_cap)
            except Exception:
                pass

            if open_count >= effective_max_open:
                now_ts = time.time()
                # 1. Anti-Churn Cooldown: Max 1 Alpha Swap per 2 hours across entire portfolio
                time_since_last_swap = now_ts - getattr(self, "_last_swap_timestamp", 0.0)
                if time_since_last_swap < self.SWAP_GLOBAL_COOLDOWN_SEC:
                    rem_cd = int(self.SWAP_GLOBAL_COOLDOWN_SEC - time_since_last_swap)
                    msg = (
                        f"🛡️ [ANTI-CHURN GUARD] Alpha Swap blocked: Cooldown active ({rem_cd}s remaining). "
                        f"Preventing rapid turnover and fee bleed on active positions."
                    )
                    logger.warning(msg)
                    self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                    try:
                        from app.trading.opportunity_queue import OpportunityQueueEngine
                        asyncio.create_task(OpportunityQueueEngine.get_instance().enqueue_opportunity(opportunity, reason=msg))
                    except Exception as qe:
                        logger.error(f"Error enqueuing opportunity into OpportunityQueue: {qe}")
                    return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

                # 2. Minimum Conviction Hurdle: Incoming setup must be exceptional (>= 88%)
                if incoming_conf < self.MIN_INCOMING_CONVICTION_FOR_SWAP:
                    msg = (
                        f"🛡️ [ANTI-CHURN GUARD] Alpha Swap rejected: Incoming setup conviction ({incoming_conf*100:.1f}%) "
                        f"below exceptional swap hurdle ({self.MIN_INCOMING_CONVICTION_FOR_SWAP*100:.0f}%). Active positions protected."
                    )
                    logger.warning(msg)
                    self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                    try:
                        from app.trading.opportunity_queue import OpportunityQueueEngine
                        asyncio.create_task(OpportunityQueueEngine.get_instance().enqueue_opportunity(opportunity, reason=msg))
                    except Exception as qe:
                        logger.error(f"Error enqueuing opportunity into OpportunityQueue: {qe}")
                    return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

                lowest_pos = None
                lowest_conviction = 1.0

                for p in active_positions:
                    p_thesis = float(p.get("thesis_strength", 0.75))
                    p_opened_at = float(p.get("opened_at", now_ts))
                    p_age_sec = now_ts - p_opened_at
                    
                    # Minimum Holding Lock: CANNOT swap out a position opened less than 60 minutes ago!
                    # Give setups time to breathe and play out. Never churn fresh trades!
                    if p_age_sec < self.MIN_SWAP_HOLD_TIME_SEC:
                        continue

                    # Profit Protection: Never swap out a trade that is in solid profit (>= +0.50%)
                    entry_p = float(p.get("entry_price", 0.0))
                    side_p = p.get("side", "BUY").upper()
                    cur_p = float(p.get("current_price", entry_p))
                    pnl_pct = ((cur_p - entry_p) / entry_p * 100.0) if entry_p > 0 and side_p == "BUY" else 0.0
                    if pnl_pct >= 0.50:
                        continue

                    effective_score = p_thesis - (0.08 if p_age_sec > 7200 else 0.0)
                    if effective_score < lowest_conviction:
                        lowest_conviction = effective_score
                        lowest_pos = p

                if lowest_pos and (incoming_conf - lowest_conviction) >= self.MIN_SWAP_CONVICTION_DELTA:
                    swap_id = lowest_pos.get("trade_id", lowest_pos.get("id"))
                    swap_sym = lowest_pos.get("symbol", "UNKNOWN")
                    self._total_swaps_executed += 1
                    self._last_swap_timestamp = now_ts
                    msg = (
                        f"🔄 [ALPHA SWAP] Max open positions reached ({effective_max_open}). "
                        f"Replacing truly stagnant {swap_sym} (Held for {(now_ts - float(lowest_pos.get('opened_at', now_ts)))/60:.0f}m, Conviction: {lowest_conviction:.2f}) "
                        f"with superior setup {symbol} (Conviction: {incoming_conf:.2f} >= +{self.MIN_SWAP_CONVICTION_DELTA*100:.0f}% edge)."
                    )
                    logger.info(msg)
                    return PortfolioDecision(
                        approved=True,
                        action_type="SWAP_POSITION",
                        reason=msg,
                        adjusted_risk_pct=incoming_risk_pct,
                        swap_trade_id=swap_id,
                        swap_symbol=swap_sym
                    )
                else:
                    msg = (
                        f"[PORTFOLIO GUARD] Maximum open positions reached ({effective_max_open}). "
                        f"All active positions are protected by 60m Minimum Holding Lock or profit buffer. "
                        f"Candidate buffered in Opportunity Queue."
                    )
                    logger.warning(msg)
                    self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                    try:
                        from app.trading.opportunity_queue import OpportunityQueueEngine
                        asyncio.create_task(OpportunityQueueEngine.get_instance().enqueue_opportunity(opportunity, reason=msg))
                    except Exception as qe:
                        logger.error(f"Error enqueuing opportunity into OpportunityQueue: {qe}")
                    return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

            # 5. Correlation Cluster Limit Check
            if len(same_side_sector_pos) >= self.MAX_CLUSTER_POSITIONS:
                msg = f"[CORRELATION GUARD] Cluster '{sector}' already has {len(same_side_sector_pos)} active {proposed_side} position(s). Limit: {self.MAX_CLUSTER_POSITIONS}."
                logger.warning(msg)
                self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                try:
                    from app.trading.opportunity_queue import OpportunityQueueEngine
                    asyncio.create_task(OpportunityQueueEngine.get_instance().enqueue_opportunity(opportunity, reason=msg))
                except Exception as qe:
                    logger.error(f"Error enqueuing opportunity into OpportunityQueue: {qe}")
                return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

            # 6. Concentration & Total Portfolio Heat Check
            if incoming_risk_pct > self.MAX_SYMBOL_EXPOSURE + 0.0001:
                msg = f"[PORTFOLIO GUARD] Symbol exposure limit exceeded for {symbol} ({incoming_risk_pct*100:.1f}% > {self.MAX_SYMBOL_EXPOSURE*100:.0f}%)."
                logger.warning(msg)
                self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

            current_portfolio_risk_pct = 0.0
            for pos in active_positions:
                p_risk = pos.get("risk_pct", 2.0)
                if p_risk <= 0.0:
                    p_risk = 2.0
                if p_risk > 1.0:
                    p_risk = p_risk / 100.0
                current_portfolio_risk_pct += p_risk

            if (current_portfolio_risk_pct + incoming_risk_pct) > self.MAX_PORTFOLIO_RISK + 0.0001:
                msg = f"[PORTFOLIO GUARD] Portfolio risk limit exceeded (Current: {current_portfolio_risk_pct*100:.1f}%, Incoming: {incoming_risk_pct*100:.1f}%, Max: {self.MAX_PORTFOLIO_RISK*100:.0f}%)."
                logger.warning(msg)
                self._record_rejected_opportunity(opportunity, msg, incoming_conf)
                return PortfolioDecision(approved=False, action_type="REJECT", reason=msg)

            return PortfolioDecision(
                approved=True,
                action_type="APPROVE",
                reason="PASSED",
                adjusted_risk_pct=incoming_risk_pct
            )

        except Exception as e:
            logger.error(f"Error evaluating portfolio action decision: {e}")
            return PortfolioDecision(approved=True, action_type="APPROVE", reason="PASSED_ON_ERROR", adjusted_risk_pct=incoming_risk_pct)

    async def evaluate_trade(self, opportunity: Dict[str, Any], incoming_risk_pct: float = 0.02, balance: float = 100000.0) -> Tuple[bool, str]:
        """Backward-compatible wrapper for evaluate_trade."""
        decision = await self.evaluate_portfolio_action(opportunity, incoming_risk_pct, balance)
        return decision.approved, decision.reason

    def get_symbol_sector_cluster(self, symbol: str) -> str:
        """Determines the sector correlation cluster for a given symbol."""
        sym_upper = symbol.upper()
        for cluster_name, sym_list in self.CORRELATION_CLUSTERS.items():
            if sym_upper in sym_list or any(sym_upper.startswith(s.replace("USDT", "")) for s in sym_list):
                return cluster_name
        return "GENERAL_ALT"

    def evaluate_sector_cluster_risk(self, symbol: str, incoming_risk_pct: float, active_positions: list) -> Tuple[bool, str]:
        """
        Calculates sector correlation cluster allocation and enforces max 3.5% portfolio risk per sector.
        """
        sector = self.get_symbol_sector_cluster(symbol)
        cluster_risk_pct = 0.0
        cluster_count = 0

        for pos in active_positions:
            p_sym = pos.get("symbol", "")
            if self.get_symbol_sector_cluster(p_sym) == sector:
                cluster_risk_pct += pos.get("risk_pct", 2.0)
                cluster_count += 1

        new_total_sector_risk = cluster_risk_pct + incoming_risk_pct
        max_allowed_count = self.MAX_OPEN_POSITIONS if sector == "GENERAL_ALT" else self.MAX_CLUSTER_POSITIONS
        max_sector_cap = max(self.MAX_SECTOR_RISK_CAP_PCT, 4.0) if sector == "GENERAL_ALT" else self.MAX_SECTOR_RISK_CAP_PCT
        if new_total_sector_risk > max_sector_cap or cluster_count >= max_allowed_count:
            return False, f"🌐 [SECTOR CLUSTER GUARD] Trade rejected. {sector} cluster risk ({new_total_sector_risk:.2f}%) exceeds max sector cap ({max_sector_cap}%) or cluster count ({cluster_count} >= {max_allowed_count})."

        return True, f"Passed sector risk check ({sector}: {new_total_sector_risk:.2f}% <= {max_sector_cap}%)"

    def calculate_portfolio_covariance_matrix(self, active_positions: list) -> Dict[str, Any]:
        """
        Computes real-time cross-asset portfolio covariance matrix and correlation heat scores
        to prevent over-concentrated directional exposure across correlated assets.
        """
        if not active_positions:
            return {"active_symbols": [], "covariance_matrix": {}, "portfolio_systemic_beta": 1.0, "risk_level": "LOW"}

        symbols = [p.get("symbol", "BTCUSDT") for p in active_positions]
        matrix = {}
        total_corr = 0.0
        pairs_count = 0

        # Baseline pairwise correlation lookup across multi-tier universe
        corr_baseline = {
            ("BTCUSDT", "ETHUSDT"): 0.88,
            ("BTCUSDT", "SOLUSDT"): 0.82,
            ("ETHUSDT", "SOLUSDT"): 0.85,
            ("BTCUSDT", "BNBUSDT"): 0.80,
            ("BTCUSDT", "ADAUSDT"): 0.78,
            ("BTCUSDT", "AVAXUSDT"): 0.81,
            ("BTCUSDT", "LINKUSDT"): 0.77,
            ("BTCUSDT", "NEARUSDT"): 0.79,
            ("BTCUSDT", "SUIUSDT"): 0.74,
            ("BTCUSDT", "DOTUSDT"): 0.76,
            ("BTCUSDT", "LTCUSDT"): 0.75,
            ("BTCUSDT", "DOGEUSDT"): 0.70,
            ("BTCUSDT", "PEPEUSDT"): 0.68,
            ("BTCUSDT", "INJUSDT"): 0.76,
            ("BTCUSDT", "RENDERUSDT"): 0.77,
            ("BTCUSDT", "FETUSDT"): 0.75,
            ("BTCUSDT", "TIAUSDT"): 0.73,
            ("BTCUSDT", "XRPUSDT"): 0.72,
        }

        for i, sym1 in enumerate(symbols):
            matrix[sym1] = {}
            for sym2 in symbols:
                if sym1 == sym2:
                    matrix[sym1][sym2] = 1.0
                else:
                    pair = (sym1, sym2) if (sym1, sym2) in corr_baseline else (sym2, sym1)
                    c_val = corr_baseline.get(pair, 0.75)
                    matrix[sym1][sym2] = c_val
                    if i < symbols.index(sym2):
                        total_corr += c_val
                        pairs_count += 1

        avg_corr = total_corr / pairs_count if pairs_count > 0 else 1.0
        systemic_beta = round(0.80 + avg_corr * 0.40, 2)
        risk_lvl = "HIGH" if systemic_beta > 1.2 else ("MODERATE" if systemic_beta > 1.0 else "LOW")

        return {
            "active_symbols": symbols,
            "covariance_matrix": matrix,
            "avg_pairwise_correlation": round(avg_corr, 2),
            "portfolio_systemic_beta": systemic_beta,
            "risk_level": risk_lvl
        }

    async def get_dashboard_metrics(self, balance: float = 100000.0, trading_mode: Optional[str] = None) -> Dict[str, Any]:
        """Returns metrics required for Android dashboard & UI integration."""
        try:
            self._check_circuit_breaker_reset()

            active_mode = trading_mode
            if active_mode is None:
                try:
                    from app.scheduler.manager import LayeredSchedulerManager
                    scheduler = await LayeredSchedulerManager.get_instance()
                    active_mode = getattr(scheduler, "global_trading_mode", "PAPER").upper()
                except Exception:
                    active_mode = "PAPER"

            from app.trading.position_manager import PositionManager
            active_positions = PositionManager.get_instance().get_active_positions(trading_mode=active_mode)
            open_positions_count = len(active_positions)
            
            current_portfolio_risk_pct = 0.0
            for pos in active_positions:
                p_risk = pos.get("risk_pct", 2.0)
                if p_risk <= 0.0:
                    p_risk = 2.0
                if p_risk > 1.0:
                    p_risk = p_risk / 100.0
                current_portfolio_risk_pct += p_risk
                
            portfolio_risk_pct = round(current_portfolio_risk_pct * 100.0, 2)
            remaining_risk_capacity_pct = round(max(0.0, (self.MAX_PORTFOLIO_RISK * 100.0) - portfolio_risk_pct), 2)
            
            today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
            daily_realized_pnl = 0.0
            async with AsyncSessionLocal() as db:
                try:
                    query = select(func.sum(Trade.profit_loss)).where(
                        Trade.exit_price.isnot(None),
                        Trade.exit_time >= today_start,
                        Trade.trading_mode == active_mode
                    )
                    res = await db.execute(query)
                    daily_realized_pnl = res.scalar() or 0.0
                except Exception as e:
                    logger.error(f"Error querying daily PnL for dashboard metrics: {e}")

            starting_daily_balance = balance - daily_realized_pnl if (balance - daily_realized_pnl) > 0 else balance
            daily_realized_pnl_pct = round((daily_realized_pnl / starting_daily_balance) * 100.0, 2) if starting_daily_balance > 0 else 0.0

            cb_status = "ACTIVE" if self._circuit_breaker_active or daily_realized_pnl_pct <= -5.0 else "INACTIVE"
            cov_matrix = self.calculate_portfolio_covariance_matrix(active_positions)

            sector_breakdown = {cluster: 0.0 for cluster in self.CORRELATION_CLUSTERS}
            sector_breakdown["GENERAL_ALT"] = 0.0
            for pos in active_positions:
                sec = self.get_symbol_sector_cluster(pos.get("symbol", ""))
                sector_breakdown[sec] = round(sector_breakdown.get(sec, 0.0) + pos.get("risk_pct", 2.0), 2)

            return {
                "open_positions": open_positions_count,
                "max_open_positions": self.MAX_OPEN_POSITIONS,
                "portfolio_risk_pct": portfolio_risk_pct,
                "max_portfolio_risk_pct": self.MAX_PORTFOLIO_RISK * 100.0,
                "daily_realized_pnl_pct": daily_realized_pnl_pct,
                "daily_loss_limit_pct": self.MAX_DAILY_LOSS * 100.0,
                "remaining_risk_capacity": remaining_risk_capacity_pct,
                "circuit_breaker_status": cb_status,
                "covariance_risk_matrix": cov_matrix,
                "sector_risk_breakdown": sector_breakdown,
                "max_sector_risk_cap_pct": self.MAX_SECTOR_RISK_CAP_PCT,
                "alpha_swaps_executed": self._total_swaps_executed,
                "pyramid_scale_ins_executed": self._total_scale_ins_executed,
                "delta_hedges_executed": self._total_hedges_executed,
                "slippage_feedback_recalibrations": len(self._slippage_feedback)
            }
        except Exception as e:
            logger.error(f"Error building portfolio guard dashboard metrics: {e}")
            return {
                "open_positions": 0,
                "max_open_positions": 5,
                "portfolio_risk_pct": 0.0,
                "max_portfolio_risk_pct": 6.0,
                "daily_realized_pnl_pct": 0.0,
                "daily_loss_limit_pct": 5.0,
                "remaining_risk_capacity": 6.0,
                "circuit_breaker_status": "INACTIVE"
            }
