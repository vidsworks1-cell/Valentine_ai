import asyncio
import logging
import time
from typing import Dict, Any, List, Optional
from datetime import datetime
from app.workers.price_cache import PriceCache
from app.database.session import AsyncSessionLocal
from app.api.schemas import TradeUpdate, AuditLogCreate

logger = logging.getLogger("lumina.trading.position_manager")

class PositionManager:
    """Layer 4 Position and Trailing Stop Manager."""
    _instance: Optional['PositionManager'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self._positions: Dict[int, Dict[str, Any]] = {}
        self.running = False
        self._task: asyncio.Task | None = None
        self.default_stale_hours: float = 4.0  # Default 4 hours stale duration
        self.default_stale_pct: float = 0.6    # Stagnant price movement threshold (0.6%)

    @classmethod
    def get_instance(cls) -> 'PositionManager':
        if cls._instance is None:
            cls._instance = PositionManager()
        return cls._instance

    async def start(self):
        self.running = True
        self._task = asyncio.create_task(self._monitor_loop())
        logger.info("Layer 4 Trailing Stop & Position Manager started.")

    async def stop(self):
        self.running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        logger.info("Layer 4 Position Manager stopped.")

    async def track_position(self, trade_id: int, position_details: Dict[str, Any]):
        """Starts real-time price monitoring and trailing adjustments for a trade."""
        entry = position_details["entry_price"]
        tp = position_details["take_profit"]
        side = position_details["side"]
        
        # Spacing based on final take profit, or default to an estimate if not present
        total_dist = abs(tp - entry) if tp > 0 else (entry * 0.05)
        
        if side == "BUY":
            position_details["tp_stages"] = [
                {"level": round(entry + total_dist * 0.35, 2), "percentage": 0.30, "hit": False, "label": "Partial TP1 (30%)"},
                {"level": round(entry + total_dist * 0.70, 2), "percentage": 0.30, "hit": False, "label": "Partial TP2 (30%)"},
                {"level": round(tp if tp > 0 else entry + total_dist, 2), "percentage": 0.40, "hit": False, "label": "Final TP (40%)"}
            ]
        else:
            position_details["tp_stages"] = [
                {"level": round(entry - total_dist * 0.35, 2), "percentage": 0.30, "hit": False, "label": "Partial TP1 (30%)"},
                {"level": round(entry - total_dist * 0.70, 2), "percentage": 0.30, "hit": False, "label": "Partial TP2 (30%)"},
                {"level": round(tp if tp > 0 else entry - total_dist, 2), "percentage": 0.40, "hit": False, "label": "Final TP (40%)"}
            ]
            
        if "opened_at" not in position_details:
            position_details["opened_at"] = time.time()
        if "stale_threshold_sec" not in position_details:
            position_details["stale_threshold_sec"] = int(self.default_stale_hours * 3600)
        if "stale_price_range_pct" not in position_details:
            position_details["stale_price_range_pct"] = self.default_stale_pct

        self._positions[trade_id] = position_details
        logger.info(f"PositionManager started tracking trade {trade_id} ({position_details['symbol']}) with TP stages: {position_details['tp_stages']} | Stale limit: {round(position_details['stale_threshold_sec']/3600, 1)}h")

    def get_active_positions(self) -> List[Dict[str, Any]]:
        return list(self._positions.values())

    async def _monitor_loop(self):
        cache = await PriceCache.get_instance()
        
        while self.running:
            try:
                active_trade_ids = list(self._positions.keys())
                for trade_id in active_trade_ids:
                    pos = self._positions.get(trade_id)
                    if not pos:
                        continue
                    
                    symbol = pos["symbol"]
                    side = pos["side"]
                    entry = pos["entry_price"]
                    size = pos["position_size"]
                    tp = pos["take_profit"]
                    sl = pos["stop_loss"]
                    
                    current_price = cache.get_price(symbol)
                    
                    # 1. Trailing Stop Management
                    if side == "BUY":
                        if current_price > pos["highest_price"]:
                            old_sl = pos["trailing_stop"]
                            pos["highest_price"] = current_price
                            new_sl = round(current_price * 0.985, 2)
                            if new_sl > old_sl and new_sl < current_price:
                                pos["trailing_stop"] = new_sl
                                logger.info(f"[STAGE: MANAGEMENT] Trailing stop updated for Trade {trade_id} {symbol} ({side}). SL raised from {old_sl} to {new_sl}.")
                                
                    elif side == "SELL":
                        if current_price < pos["lowest_price"]:
                            old_sl = pos["trailing_stop"]
                            pos["lowest_price"] = current_price
                            new_sl = round(current_price * 1.015, 2)
                            if new_sl < old_sl and new_sl > current_price:
                                pos["trailing_stop"] = new_sl
                                logger.info(f"[STAGE: MANAGEMENT] Trailing stop updated for Trade {trade_id} {symbol} ({side}). SL lowered from {old_sl} to {new_sl}.")
                    
                    # 2. Check Multi-Stage Partial Take Profits
                    stages = pos.get("tp_stages", [])
                    stage_triggered = False
                    
                    for stage in stages:
                        if not stage["hit"]:
                            level = stage["level"]
                            is_hit = False
                            if side == "BUY" and current_price >= level:
                                is_hit = True
                            elif side == "SELL" and current_price <= level:
                                is_hit = True
                                
                            if is_hit:
                                stage["hit"] = True
                                close_pct = stage["percentage"]
                                
                                # If it's the final stage (or last stage of defined levels), trigger a full exit
                                if stage == stages[-1]:
                                    await self._close_position(trade_id, current_price, "FINAL_TAKE_PROFIT_STAGE")
                                    stage_triggered = True
                                    break
                                else:
                                    close_size = round(size * close_pct, 4)
                                    if close_size > 0 and pos["position_size"] > close_size:
                                        await self._partial_close_position(trade_id, current_price, close_size, stage["label"])
                                        # Reduce active position size
                                        pos["position_size"] = round(pos["position_size"] - close_size, 4)
                                        
                                        # Lock in profits: raise stop loss to entry (break-even)
                                        if side == "BUY":
                                            pos["trailing_stop"] = max(pos["trailing_stop"], entry)
                                        else:
                                            pos["trailing_stop"] = min(pos["trailing_stop"], entry)
                    
                    if stage_triggered:
                        continue
                        
                    # 3. Check General Trailing Stop Loss Exit Criteria
                    if side == "BUY":
                        if current_price <= pos["trailing_stop"] and pos["trailing_stop"] > 0:
                            await self._close_position(trade_id, current_price, "STOP_LOSS")
                            continue
                    elif side == "SELL":
                        if current_price >= pos["trailing_stop"] and pos["trailing_stop"] > 0:
                            await self._close_position(trade_id, current_price, "STOP_LOSS")
                            continue

                    # 4. Intelligent Early Exit Engine (Thesis Invalidation Check)
                    from app.trading.mtf_confirmation import MTFConfirmationGuard
                    invalidated, inv_reason = await MTFConfirmationGuard.get_instance().check_thesis_invalidation(symbol, side, current_price)
                    if invalidated:
                        logger.warning(f"⚠️ [INTELLIGENT EARLY EXIT] Trade {trade_id} {symbol} ({side}): Thesis invalidated. Reason: {inv_reason}")
                        await self._close_position(trade_id, current_price, "THESIS_INVALIDATION")
                        continue

                    # 5. Time-Based Stale Trade Exit Engine (Capital Recycling)
                    opened_at = pos.get("opened_at", time.time())
                    time_open_sec = time.time() - opened_at
                    stale_limit_sec = pos.get("stale_threshold_sec", int(self.default_stale_hours * 3600))
                    stale_pct_limit = pos.get("stale_price_range_pct", self.default_stale_pct)
                    price_change_pct = abs((current_price - entry) / entry) * 100.0 if entry > 0 else 0.0

                    if time_open_sec >= stale_limit_sec and price_change_pct <= stale_pct_limit:
                        open_hrs = round(time_open_sec / 3600.0, 1)
                        logger.warning(
                            f"⏳ [STALE TRADE EXIT] Trade {trade_id} {symbol} ({side}): "
                            f"Stagnant for {open_hrs}h with price change of {price_change_pct:.2f}% (<= {stale_pct_limit}%). "
                            f"Exiting position to recycle capital and eliminate opportunity cost."
                        )
                        await self._close_position(trade_id, current_price, "STALE_TRADE_EXIT")
                        continue

                    # 6. Pyramiding Scale-In Evaluation for Winning Trends
                    from app.trading.pyramiding import PyramidingEngine
                    await PyramidingEngine.get_instance().evaluate_scale_in(pos, current_price)
                
                await asyncio.sleep(1.0) # Check every second
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Position monitoring error: {e}")
                await asyncio.sleep(5)

    async def _partial_close_position(self, trade_id: int, exit_price: float, close_size: float, stage_label: str):
        """Partially closes a position, updates P&L, logs audit, and updates DB."""
        pos = self._positions.get(trade_id)
        if not pos:
            return
            
        logger.info(f"🍰 PARTIAL CLOSE: Trade {trade_id} closed {close_size} units at {exit_price} ({stage_label})")
        
        entry = pos["entry_price"]
        side = pos["side"]
        symbol = pos["symbol"]
        
        # Get global trading mode
        from app.scheduler.manager import LayeredSchedulerManager
        scheduler = await LayeredSchedulerManager.get_instance()
        trading_mode = scheduler.global_trading_mode
        
        # If in TESTNET or LIVE, place partial close order on Binance
        from app.services.binance_connector import BinanceConnector
        if trading_mode in ["TESTNET", "LIVE"]:
            opposite_side = "SELL" if side == "BUY" else "BUY"
            logger.info(f"Placing real Binance Spot partial close order for {symbol} ({opposite_side} qty: {close_size}) in {trading_mode} mode...")
            binance_res = await BinanceConnector.get_instance().create_order(
                mode=trading_mode,
                symbol=symbol,
                side=opposite_side,
                order_type="MARKET",
                quantity=close_size
            )
            
            if "error" in binance_res or "msg" in binance_res and binance_res.get("error"):
                logger.error(f"❌ BINANCE PARTIAL CLOSE ORDER FAILED: {binance_res}")
                try:
                    from app.services.audit_service import AuditService
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="ERROR",
                            category="TRADING",
                            message=f"Binance partial close order failed for {symbol}",
                            details=f"Error: {binance_res}"
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log partial close failure audit event: {ae}")
                return
                
            logger.info(f"Binance partial close order placed successfully: {binance_res}")
            if "fills" in binance_res and binance_res["fills"]:
                total_qty = 0.0
                total_quote = 0.0
                for fill in binance_res["fills"]:
                    qty = float(fill.get("qty", 0.0))
                    px = float(fill.get("price", 0.0))
                    total_qty += qty
                    total_quote += qty * px
                if total_qty > 0.0:
                    exit_price = round(total_quote / total_qty, 6)
                    
        if side == "BUY":
            pnl = (exit_price - entry) * close_size
        else:
            pnl = (entry - exit_price) * close_size
            
        outcome = "WIN" if pnl > 0 else "LOSS"
        
        logger.info(f"[STAGE: MANAGEMENT] Partial take-profit stage hit for Trade {trade_id} {symbol} ({side}). Stage: {stage_label}, Price: {exit_price}, Realized P&L: {pnl} ({outcome}).")
        
        from app.services.trading_service import TradingService
        from app.services.paper_trading_service import PaperTradingService
        from app.services.audit_service import AuditService

        async with AsyncSessionLocal() as db:
            try:
                # 1. Update Trade Record in DB
                trade = await TradingService.get_trade_by_id(db, trade_id)
                if trade:
                    current_pnl = trade.profit_loss or 0.0
                    new_pnl = current_pnl + pnl
                    new_size = max(0.0, trade.position_size - close_size)
                    
                    trade_update = TradeUpdate(
                        profit_loss=new_pnl,
                        position_size=new_size
                    )
                    await TradingService.update_trade(db, trade_id, trade_update)
                
                # 2. Adjust Paper Wallet Balance (only for paper)
                if trading_mode not in ["TESTNET", "LIVE"]:
                    await PaperTradingService.adjust_balance(db, "lumina_default", pnl)
                
                # 3. Save Audit Log
                try:
                    await AuditService.create_audit_log(db, AuditLogCreate(
                        level="INFO",
                        category="TRADING",
                        message=f"Lumina VPS Partial Take-Profit Hit on Trade {trade_id}",
                        details=f"Stage: {stage_label}. Closed {close_size} units at ${exit_price}. Realized P&L: ${round(pnl, 2)} ({outcome}). Remaining: {round(pos['position_size'] - close_size, 4)} units.",
                        trade_id=trade_id
                    ))
                except Exception as ae:
                    logger.error(f"Failed to log partial take-profit hit audit event: {ae}")
                
            except Exception as e:
                logger.error(f"Error finalizing partial trade exit in DB: {e}")
                await db.rollback()

    async def _close_position(self, trade_id: int, exit_price: float, outcome_type: str):
        """Finalizes the trade, logs audit, and updates the DB."""
        pos = self._positions.pop(trade_id, None)
        if not pos:
            return
            
        logger.info(f"🚪 CLOSING POSITION: Trade {trade_id} closed at {exit_price} ({outcome_type})")
        
        entry = pos["entry_price"]
        size = pos["position_size"]
        side = pos["side"]
        symbol = pos["symbol"]
        
        # Get global trading mode
        from app.scheduler.manager import LayeredSchedulerManager
        scheduler = await LayeredSchedulerManager.get_instance()
        trading_mode = scheduler.global_trading_mode
        
        # If in TESTNET or LIVE, place closing order on Binance
        from app.services.binance_connector import BinanceConnector
        if trading_mode in ["TESTNET", "LIVE"]:
            opposite_side = "SELL" if side == "BUY" else "BUY"
            logger.info(f"Placing real Binance Spot close order for {symbol} ({opposite_side} qty: {size}) in {trading_mode} mode...")
            binance_res = await BinanceConnector.get_instance().create_order(
                mode=trading_mode,
                symbol=symbol,
                side=opposite_side,
                order_type="MARKET",
                quantity=size
            )
            
            if "error" in binance_res or ("code" in binance_res and isinstance(binance_res["code"], int) and binance_res["code"] < 0) or ("status_code" in binance_res and binance_res.get("status_code", 200) >= 400):
                logger.error(f"❌ BINANCE CLOSE ORDER FAILED: {binance_res}")
                try:
                    from app.services.audit_service import AuditService
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="ERROR",
                            category="TRADING",
                            message=f"Binance closing order failed for {symbol}",
                            details=f"Error response: {binance_res}"
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log close order failure audit event: {ae}")
                # Put position back into active tracking so we can try again or alert operator
                self._positions[trade_id] = pos
                return
                
            logger.info(f"Binance closing order placed successfully: {binance_res}")
            if "fills" in binance_res and binance_res["fills"]:
                total_qty = 0.0
                total_quote = 0.0
                for fill in binance_res["fills"]:
                    qty = float(fill.get("qty", 0.0))
                    px = float(fill.get("price", 0.0))
                    total_qty += qty
                    total_quote += qty * px
                if total_qty > 0.0:
                    exit_price = round(total_quote / total_qty, 6)
        
        # Calculate P&L
        if side == "BUY":
            pnl = (exit_price - entry) * size
        else:
            pnl = (entry - exit_price) * size
            
        outcome = "WIN" if pnl > 0 else "LOSS"
        
        logger.info(f"[STAGE: CLOSE] Closing Trade {trade_id} {symbol} ({side}) at exit price {exit_price} due to {outcome_type}. Realized P&L: {pnl} ({outcome}).")
        
        from app.services.trading_service import TradingService
        from app.services.paper_trading_service import PaperTradingService
        from app.services.audit_service import AuditService

        async with AsyncSessionLocal() as db:
            try:
                # 1. Update Trade Record in DB
                trade_update = TradeUpdate(
                    exit_price=exit_price,
                    profit_loss=pnl,
                    exit_time=datetime.utcnow(),
                    outcome=outcome
                )
                await TradingService.update_trade(db, trade_id, trade_update)
                
                # 2. Adjust Paper Wallet Balance (only for paper)
                if trading_mode not in ["TESTNET", "LIVE"]:
                    wallet = await PaperTradingService.get_wallet(db, "lumina_default")
                    if wallet:
                        await PaperTradingService.adjust_balance(db, "lumina_default", pnl)
                
                # 3. Save Audit Log
                try:
                    await AuditService.create_audit_log(db, AuditLogCreate(
                        level="INFO",
                        category="TRADING",
                        message=f"Lumina VPS Closed Position {trade_id} ({outcome_type})",
                        details=f"Exit Price: {exit_price}. P&L: ${round(pnl, 2)} ({outcome}).",
                        trade_id=trade_id
                    ))
                except Exception as ae:
                    logger.error(f"Failed to log close position success audit event: {ae}")

                # 4. Trigger Self-Adaptation Engine performance re-evaluation, Trade Journaling, Smart Re-Entry watcher, and Champion/Challenger evaluation
                try:
                    from app.ai.self_adaptation import SelfAdaptationEngine
                    from app.trading.trade_journal import TradeJournalEngine
                    from app.trading.champion_challenger import ChampionChallengerEngine
                    from app.trading.reentry_engine import SmartReentryEngine
                    
                    SmartReentryEngine.get_instance().register_closed_trade({
                        "trade_id": trade_id,
                        "symbol": symbol,
                        "side": side,
                        "entry_price": entry,
                        "exit_price": exit_price,
                        "outcome_type": outcome_type,
                        "strategy": pos.get("strategy", "Standard Strategy")
                    })

                    asyncio.create_task(SelfAdaptationEngine.get_instance().analyze_performance())
                    asyncio.create_task(TradeJournalEngine.get_instance().journal_closed_trade(trade_id))
                    asyncio.create_task(ChampionChallengerEngine.get_instance().evaluate_strategies())
                except Exception as sae:
                    logger.error(f"Failed to trigger post-trade research & adaptation workflows: {sae}")
                
            except Exception as e:
                logger.error(f"Error finalizing trade exit in DB: {e}")
                await db.rollback()
                
    async def trigger_emergency_liquidation(self) -> int:
        """Emergency Protection trigger: Liquidates all active positions instantly and stops entries."""
        from app.scheduler.manager import LayeredSchedulerManager
        scheduler = await LayeredSchedulerManager.get_instance()
        scheduler.emergency_stop_active = True
        
        try:
            from app.services.audit_service import AuditService
            async with AsyncSessionLocal() as db:
                await AuditService.create_audit_log(db, AuditLogCreate(
                    level="CRITICAL",
                    category="SYSTEM",
                    message="EMERGENCY STOP TRIGGERED BY OPERATOR",
                    details="Emergency liquidation initiated. All new trading opportunities are now blocked."
                ))
        except Exception as ae:
            logger.error(f"Failed to log emergency stop audit event: {ae}")

        active_ids = list(self._positions.keys())
        cache = await PriceCache.get_instance()
        count = 0
        
        for trade_id in active_ids:
            pos = self._positions.get(trade_id)
            if pos:
                current_price = cache.get_price(pos["symbol"])
                await self._close_position(trade_id, current_price, "EMERGENCY_PROTECTION")
                count += 1
                
        return count
