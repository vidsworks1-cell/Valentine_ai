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
        
        # Ensure highest_price and lowest_price are always initialized for peak & drawdown tracking
        if "highest_price" not in position_details or position_details["highest_price"] is None or position_details["highest_price"] <= 0.0:
            position_details["highest_price"] = entry
        if "lowest_price" not in position_details or position_details["lowest_price"] is None or position_details["lowest_price"] <= 0.0:
            position_details["lowest_price"] = entry

        # Ensure trailing_stop and stop_loss are always present
        if "trailing_stop" not in position_details or position_details["trailing_stop"] is None:
            position_details["trailing_stop"] = float(position_details.get("stop_loss", 0.0))
        if "stop_loss" not in position_details or position_details["stop_loss"] is None:
            position_details["stop_loss"] = float(position_details.get("trailing_stop", 0.0))

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

        # Lumina Stability Additions: Thesis Strength, Grace Period & Trend Persistence Engine
        position_details["thesis_strength"] = float(position_details.get("confidence", position_details.get("entry_confidence", 0.83)))
        position_details["entry_strategy"] = position_details.get("strategy", "Volatility Expansion Breakout")
        position_details["grace_period_sec"] = float(position_details.get("grace_period_sec", 90.0))  # 90s Grace Period
        position_details["invalidation_consecutive_cycles"] = 0
        position_details["invalidation_first_detected_at"] = 0.0
        position_details["trend_history"] = []

        if "trading_mode" not in position_details:
            position_details["trading_mode"] = "PAPER"

        self._positions[trade_id] = position_details
        logger.info(f"PositionManager started tracking trade {trade_id} ({position_details['symbol']}) [Mode: {position_details['trading_mode']}] with TP stages: {position_details['tp_stages']} | Stale limit: {round(position_details['stale_threshold_sec']/3600, 1)}h")

    def get_active_positions(self, trading_mode: Optional[str] = None) -> List[Dict[str, Any]]:
        target_mode = trading_mode
        if target_mode is None:
            try:
                from app.scheduler.manager import LayeredSchedulerManager
                if LayeredSchedulerManager._instance is not None:
                    target_mode = getattr(LayeredSchedulerManager._instance, "global_trading_mode", None)
            except Exception:
                target_mode = None

        if target_mode and target_mode.upper() != "ALL":
            mode_upper = target_mode.upper()
            return [pos for pos in self._positions.values() if pos.get("trading_mode", "PAPER").upper() == mode_upper]
        return list(self._positions.values())

    async def scale_in_position(self, symbol: str, additional_size: float, add_price: float, added_risk_pct: float = 0.0) -> Optional[Dict[str, Any]]:
        """Scales into an existing open position by recalculating weighted average entry price, expanded position size, and cumulative risk."""
        for trade_id, pos in list(self._positions.items()):
            if pos.get("symbol") == symbol:
                old_size = pos.get("position_size", 0.0)
                old_entry = pos.get("entry_price", add_price)
                new_size = old_size + additional_size
                if new_size > 0:
                    new_avg_entry = ((old_size * old_entry) + (additional_size * add_price)) / new_size
                    pos["position_size"] = round(new_size, 6)
                    pos["entry_price"] = round(new_avg_entry, 4)
                    pos["scale_ins"] = pos.get("scale_ins", 0) + 1
                    pos["risk_pct"] = round(float(pos.get("risk_pct", 0.01)) + added_risk_pct, 6)
                    pos["thesis_strength"] = min(0.99, float(pos.get("thesis_strength", 0.80)) + 0.05)
                    logger.info(
                        f"🚀 [PYRAMID SCALE-IN EXECUTED] {symbol} trade {trade_id} (Scale-in #{pos['scale_ins']}): "
                        f"size expanded from {old_size} to {new_size:.4f}, avg entry adjusted to ${new_avg_entry:.2f}, "
                        f"cumulative risk: {pos['risk_pct']*100:.2f}%"
                    )
                    return pos
        return None

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
                    tp = pos.get("take_profit", 0.0)
                    sl = pos.get("stop_loss", 0.0)
                    if "trailing_stop" not in pos or pos["trailing_stop"] is None:
                        pos["trailing_stop"] = float(sl)
                    if "highest_price" not in pos or pos["highest_price"] is None or float(pos.get("highest_price", 0.0)) <= 0.0:
                        pos["highest_price"] = entry
                    if "lowest_price" not in pos or pos["lowest_price"] is None or float(pos.get("lowest_price", 0.0)) <= 0.0:
                        pos["lowest_price"] = entry
                    
                    current_price = cache.get_price(symbol)
                    if current_price <= 0.0:
                        logger.debug(f"Skipping position monitoring for Trade {trade_id} ({symbol}): invalid or missing price ({current_price})")
                        continue
                    
                    # 1. Regime-Adaptive Dynamic Trailing & Breakeven Protection Engine
                    regime = pos.get("market_regime", "TRENDING").upper()
                    atr = pos.get("atr", entry * 0.01) if pos.get("atr", 0.0) > 0.0 else entry * 0.01

                    # Correlated Risk Management: Macro Shock Guard on BTC selloff
                    btc_price = cache.get_price("BTCUSDT")
                    if not hasattr(self, "_btc_price_history"):
                        self._btc_price_history = []
                    self._btc_price_history.append(btc_price)
                    if len(self._btc_price_history) > 10:
                        self._btc_price_history.pop(0)

                    btc_shock_detected = False
                    if len(self._btc_price_history) >= 3:
                        btc_peak = max(self._btc_price_history)
                        btc_drop_pct = (btc_peak - btc_price) / btc_peak * 100.0 if btc_peak > 0 else 0.0
                        if btc_drop_pct >= 1.2:
                            btc_shock_detected = True

                    # If BTC shock detected and position is an altcoin BUY, lock breakeven immediately
                    if btc_shock_detected and symbol != "BTCUSDT" and side == "BUY":
                        be_level = round(entry * 1.0005, 2)
                        if pos["trailing_stop"] < be_level:
                            pos["trailing_stop"] = be_level
                            logger.warning(f"🚨 [CORRELATED MACRO SHOCK GUARD] Rapid BTC selloff ({btc_drop_pct:.2f}% drop)! Tightened trailing stop on {symbol} to breakeven ({be_level}) to prevent systemic drawdown cascade.")
                    
                    # Update peak tracking
                    if side == "BUY":
                        if current_price > pos.get("highest_price", entry):
                            pos["highest_price"] = current_price
                        favorable_move = current_price - entry
                        peak_move = pos["highest_price"] - entry
                    else:
                        if pos.get("lowest_price", entry) == 0.0 or current_price < pos.get("lowest_price", entry):
                            pos["lowest_price"] = current_price
                        favorable_move = entry - current_price
                        peak_move = entry - pos["lowest_price"]

                    move_in_atr = favorable_move / atr if atr > 0 else 0.0
                    peak_in_atr = peak_move / atr if atr > 0 else 0.0

                    # Assign regime-specific triggers & trailing multipliers
                    if regime == "RANGING":
                        be_trigger_atr = 0.5
                        trail_activation_atr = 0.7
                        trail_mult = 0.8
                    elif regime == "VOLATILE":
                        be_trigger_atr = 0.8
                        trail_activation_atr = 1.0
                        # Tighten trailing distance if parabolic expansion beyond 2.0x ATR
                        trail_mult = 1.0 if peak_in_atr >= 2.0 else 1.2
                    else:  # TRENDING or default
                        be_trigger_atr = 0.8
                        trail_activation_atr = 1.0
                        trail_mult = 1.0

                    pos["regime_trailing_details"] = {
                        "regime": regime,
                        "be_trigger_atr": be_trigger_atr,
                        "trail_activation_atr": trail_activation_atr,
                        "trail_dist_atr": trail_mult,
                        "current_move_atr": round(move_in_atr, 2),
                        "peak_move_atr": round(peak_in_atr, 2)
                    }

                    # A. Breakeven Protection Guard (Locks in Entry + 0.1% Fee Buffer)
                    if (move_in_atr >= be_trigger_atr or peak_in_atr >= be_trigger_atr):
                        be_level = round(entry * 1.001 if side == "BUY" else entry * 0.999, 2)
                        
                        if side == "BUY" and pos["trailing_stop"] < be_level:
                            pos["trailing_stop"] = be_level
                            if not pos.get("breakeven_activated", False):
                                pos["breakeven_activated"] = True
                                pos["breakeven_price"] = be_level
                                logger.info(
                                    f"🛡️ [REGIME BREAKEVEN PROTECTION] Trade {trade_id} {symbol} ({side}, Regime: {regime}): "
                                    f"Breakeven locked at {be_level} (Entry: {entry}). Current Move: {move_in_atr:.2f} ATR."
                                )
                        elif side == "SELL" and (pos["trailing_stop"] > be_level or pos["trailing_stop"] == 0.0):
                            pos["trailing_stop"] = be_level
                            if not pos.get("breakeven_activated", False):
                                pos["breakeven_activated"] = True
                                pos["breakeven_price"] = be_level
                                logger.info(
                                    f"🛡️ [REGIME BREAKEVEN PROTECTION] Trade {trade_id} {symbol} ({side}, Regime: {regime}): "
                                    f"Breakeven locked at {be_level} (Entry: {entry}). Current Move: {move_in_atr:.2f} ATR."
                                )

                    # B. Regime-Adaptive Dynamic Trailing Stop Adjustment
                    if peak_in_atr >= trail_activation_atr:
                        trail_dist = round(trail_mult * atr, 2)
                        if side == "BUY":
                            target_sl = round(pos["highest_price"] - trail_dist, 2)
                            if target_sl > pos["trailing_stop"] and target_sl < current_price:
                                old_sl = pos["trailing_stop"]
                                pos["trailing_stop"] = target_sl
                                pos["trailing_active"] = True
                                logger.info(
                                    f"📈 [REGIME DYNAMIC TRAILING] Trade {trade_id} {symbol} ({side}, Regime: {regime}): "
                                    f"SL raised from {old_sl} to {target_sl} (Highest: {pos['highest_price']}, Trail Dist: {trail_dist} [{trail_mult}x ATR])."
                                )
                        elif side == "SELL":
                            target_sl = round(pos["lowest_price"] + trail_dist, 2)
                            if (target_sl < pos["trailing_stop"] or pos["trailing_stop"] == 0.0) and target_sl > current_price:
                                old_sl = pos["trailing_stop"]
                                pos["trailing_stop"] = target_sl
                                pos["trailing_active"] = True
                                logger.info(
                                    f"📉 [REGIME DYNAMIC TRAILING] Trade {trade_id} {symbol} ({side}, Regime: {regime}): "
                                    f"SL lowered from {old_sl} to {target_sl} (Lowest: {pos['lowest_price']}, Trail Dist: {trail_dist} [{trail_mult}x ATR])."
                                )
                    
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

                    # 4. Intelligent Early Exit Engine (Thesis Invalidation Check with Grace Period & Entry/Exit Symmetry)
                    from app.trading.mtf_confirmation import MTFConfirmationGuard
                    holding_time_sec = time.time() - pos.get("opened_at", time.time())
                    grace_period_sec = pos.get("grace_period_sec", 90.0)

                    invalidity_strength, mtf_details = await MTFConfirmationGuard.get_instance().evaluate_weighted_mtf_invalidation(symbol, side, current_price)

                    # Trend Persistence Engine: maintain trend history
                    trend_history = pos.setdefault("trend_history", [])
                    trend_history.append(invalidity_strength)
                    if len(trend_history) > 10:
                        trend_history.pop(0)

                    thesis_strength = pos.get("thesis_strength", 0.83)

                    # Entry / Exit Symmetry: Require current invalidity strength to meet or exceed entry thesis strength
                    strength_succeeded = invalidity_strength >= thesis_strength

                    if strength_succeeded:
                        if pos.get("invalidation_first_detected_at", 0.0) == 0.0:
                            pos["invalidation_first_detected_at"] = time.time()
                        pos["invalidation_consecutive_cycles"] = pos.get("invalidation_consecutive_cycles", 0) + 1
                    else:
                        # Reset persistence counter if market conditions recover
                        pos["invalidation_consecutive_cycles"] = 0
                        pos["invalidation_first_detected_at"] = 0.0

                    first_detected = pos.get("invalidation_first_detected_at", 0.0)
                    persistence_time_sec = time.time() - first_detected if first_detected > 0.0 else 0.0
                    consecutive_cycles = pos.get("invalidation_consecutive_cycles", 0)

                    # Check invalidation trigger conditions:
                    # 1. Reversal must persist for >= 3 consecutive cycles OR >= 60 seconds
                    # 2. Invalidity strength must satisfy entry/exit symmetry (invalidity_strength >= thesis_strength)
                    persist_confirmed = (consecutive_cycles >= 3 or persistence_time_sec >= 60.0)

                    if strength_succeeded and persist_confirmed:
                        if holding_time_sec < grace_period_sec:
                            logger.info(
                                f"🛡️ [GRACE PERIOD PROTECTED] Trade {trade_id} {symbol} ({side}): "
                                f"Thesis invalidation signal suppressed during Grace Period ({holding_time_sec:.1f}s / {grace_period_sec:.0f}s). "
                                f"Invalidity Strength: {invalidity_strength:.2f} >= Thesis Strength: {thesis_strength:.2f}."
                            )
                        else:
                            # Enhanced Diagnostics Logging
                            logger.warning(
                                f"🚪 [INTELLIGENT EARLY EXIT - THESIS INVALIDATED]\n"
                                f"  - Trade ID: {trade_id} ({symbol} {side})\n"
                                f"  - Entry Confidence (Thesis Strength): {thesis_strength:.2f}\n"
                                f"  - Entry Strategy: {pos.get('entry_strategy', 'Volatility Expansion Breakout')}\n"
                                f"  - Current Confidence: {pos.get('confidence', thesis_strength):.2f}\n"
                                f"  - Current Strategy / Opposing Bias: {mtf_details.get('opposing_side', 'N/A')}\n"
                                f"  - Trend Score (Invalidity Strength): {invalidity_strength:.2f} / {thesis_strength:.2f}\n"
                                f"  - MTF Breakdown: {mtf_details.get('formatted_breakdown', 'N/A')}\n"
                                f"  - Reason Score: {invalidity_strength * 100:.1f}%\n"
                                f"  - Persistence Time: {persistence_time_sec:.1f}s ({consecutive_cycles} cycles)\n"
                                f"  - Holding Duration: {holding_time_sec:.1f}s (Grace Period Passed: {grace_period_sec:.0f}s)\n"
                                f"  - Exit Confidence: {invalidity_strength:.2f}\n"
                                f"  - Final Decision: CLOSED via THESIS_INVALIDATION"
                            )
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
            
            if "error" in binance_res or ("code" in binance_res and isinstance(binance_res["code"], int) and binance_res["code"] < 0) or ("status_code" in binance_res and binance_res.get("status_code", 200) >= 400):
                logger.error(f"❌ BINANCE PARTIAL CLOSE ORDER FAILED: {binance_res}")
                # Reset stage hit flag so partial close can retry
                for s in pos.get("tp_stages", []):
                    if s.get("label") == stage_label:
                        s["hit"] = False
                        break
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
            step_precision = {
                "BTCUSDT": 5,
                "ETHUSDT": 4,
                "BNBUSDT": 3,
                "SOLUSDT": 2
            }
            prec = step_precision.get(symbol.upper(), 4)
            close_qty = round(size, prec)
            logger.info(f"Placing real Binance Spot close order for {symbol} ({opposite_side} qty: {close_qty}) in {trading_mode} mode...")
            binance_res = await BinanceConnector.get_instance().create_order(
                mode=trading_mode,
                symbol=symbol,
                side=opposite_side,
                order_type="MARKET",
                quantity=close_qty
            )
            
            if "error" in binance_res or ("code" in binance_res and isinstance(binance_res["code"], int) and binance_res["code"] < 0) or ("status_code" in binance_res and binance_res.get("status_code", 200) >= 400):
                err_msg = str(binance_res.get("msg", "")) + " " + str(binance_res.get("raw_response", ""))
                is_dust_or_filter_error = any(kw in err_msg for kw in ["LOT_SIZE", "MIN_NOTIONAL", "PERCENT_PRICE", "INVALID_SYMBOL", "ILLEGAL_CHARS"])
                
                pos["_close_retries"] = pos.get("_close_retries", 0) + 1
                
                if is_dust_or_filter_error or pos["_close_retries"] >= 3:
                    logger.warning(
                        f"⚠️ BINANCE CLOSE CANNOT EXECUTE ON EXCHANGE ({symbol} qty {size}): {binance_res}. "
                        f"Finalizing internal position tracking to prevent infinite retry loops."
                    )
                    try:
                        from app.services.audit_service import AuditService
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="WARNING",
                                category="TRADING",
                                message=f"Binance close skipped on exchange for {symbol} (dust / unexecutable lot size)",
                                details=f"Size: {size}. Error: {binance_res}"
                            ))
                    except Exception:
                        pass
                    # Do not re-insert into active tracking; proceed to finalize locally in DB
                else:
                    logger.error(f"❌ BINANCE CLOSE ORDER FAILED (attempt {pos['_close_retries']}/3): {binance_res}")
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
                    # Put position back into active tracking for next tick retry
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

                    entry_time = pos.get("entry_time") or pos.get("created_at")
                    holding_minutes = 0.0
                    if isinstance(entry_time, datetime):
                        holding_minutes = max(0.1, (datetime.utcnow() - entry_time).total_seconds() / 60.0)
                    elif isinstance(entry_time, (int, float)):
                        holding_minutes = max(0.1, (time.time() - float(entry_time)) / 60.0)

                    stop_loss_px = float(pos.get("stop_loss", 0.0))
                    risk_per_unit = abs(entry - stop_loss_px) if stop_loss_px > 0 and stop_loss_px != entry else 0.0
                    if risk_per_unit > 0:
                        r_multiple = (exit_price - entry) / risk_per_unit if side == "BUY" else (entry - exit_price) / risk_per_unit
                    else:
                        r_multiple = 1.0 if pnl > 0 else (-1.0 if pnl < 0 else 0.0)

                    SelfAdaptationEngine.get_instance().record_post_trade_retro({
                        "id": trade_id,
                        "symbol": symbol,
                        "profit_loss": pnl,
                        "profit_loss_pct": (pnl / (entry * size)) * 100.0 if (entry * size) > 0 else 0.0,
                        "strategy_used": pos.get("strategy", "Standard Strategy"),
                        "exit_reason": outcome_type,
                        "is_probationary_trade": pos.get("is_probationary_trade", False),
                        "probation_trigger": pos.get("probation_trigger"),
                        "probation_metadata": pos.get("probation_metadata"),
                        "holding_minutes": holding_minutes,
                        "r_multiple": r_multiple
                    })
                    asyncio.create_task(SelfAdaptationEngine.get_instance().analyze_performance())
                    asyncio.create_task(TradeJournalEngine.get_instance().journal_closed_trade(trade_id))
                    ChampionChallengerEngine.get_instance().record_trade_outcome(
                        strategy=pos.get("strategy", "Breakout Momentum"),
                        pnl=pnl,
                        exit_reason=outcome_type
                    )
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

    async def sync_exchange_positions(self, mode: str = "TESTNET") -> Dict[str, Any]:
        """Synchronizes exchange asset balances/open orders with internal position tracking state."""
        from app.services.binance_connector import BinanceConnector
        connector = BinanceConnector.get_instance()
        
        if mode in ["TESTNET", "LIVE"]:
            balances = await connector.get_wallet_balances(mode)
            if isinstance(balances, list):
                tracked_symbols = {pos["symbol"] for pos in self._positions.values()}
                non_zero_assets = [b for b in balances if float(b.get("free", 0.0)) > 0 or float(b.get("locked", 0.0)) > 0]
                return {
                    "status": "SUCCESS",
                    "mode": mode,
                    "exchange_non_zero_balances": len(non_zero_assets),
                    "tracked_positions_count": len(self._positions),
                    "tracked_symbols": list(tracked_symbols)
                }
        return {
            "status": "SUCCESS",
            "mode": mode,
            "tracked_positions_count": len(self._positions)
        }
