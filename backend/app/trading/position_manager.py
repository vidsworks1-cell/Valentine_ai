import asyncio
import logging
import time
import math
from typing import Dict, Any, List, Optional
from datetime import datetime
from app.workers.price_cache import PriceCache
from app.database.session import AsyncSessionLocal
from app.api.schemas import TradeUpdate, AuditLogCreate

logger = logging.getLogger("lumina.trading.position_manager")

def _round_price(price: float) -> float:
    """Rounds price to appropriate significant precision to avoid catastrophic sub-dollar truncation."""
    if price <= 0.0:
        return 0.0
    if price < 0.00001:
        return round(price, 10)
    elif price < 0.001:
        return round(price, 8)
    elif price < 1.0:
        return round(price, 6)
    elif price < 10.0:
        return round(price, 4)
    else:
        return round(price, 2)

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
        # Restore open positions from database upon startup
        try:
            from app.models.trading import Trade
            from sqlalchemy.future import select
            async with AsyncSessionLocal() as db:
                query = select(Trade).where(Trade.exit_time.is_(None), Trade.exit_price.is_(None))
                result = await db.execute(query)
                open_trades = result.scalars().all()
                for trade in open_trades:
                    if trade.id not in self._positions:
                        await self.track_position(trade.id, {
                            "id": trade.id,
                            "symbol": trade.symbol,
                            "side": trade.side,
                            "entry_price": trade.entry_price,
                            "position_size": trade.position_size,
                            "stop_loss": trade.stop_loss,
                            "take_profit": trade.take_profit,
                            "trailing_stop": trade.stop_loss,
                            "highest_price": trade.entry_price,
                            "lowest_price": trade.entry_price,
                            "market_regime": trade.market_regime,
                            "atr": trade.atr,
                            "confidence": trade.confidence_score,
                            "strategy": trade.strategy_used,
                            "risk_pct": trade.risk_pct / 100.0 if trade.risk_pct else 0.01,
                            "trading_mode": trade.trading_mode or "PAPER",
                            "scale_ins": 0,
                            "entry_time": trade.entry_time
                        })
                if open_trades:
                    logger.info(f"🔄 [POSITION RESTORATION] Restored {len(open_trades)} active positions from database into PositionManager.")
        except Exception as e:
            logger.error(f"Failed to restore open positions from DB on startup: {e}")

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

        # Spacing based on final take profit, enforcing a minimum distance (1.5%) to ensure profits exceed fees
        min_tp_dist = entry * 0.015 if entry > 0 else 0.05
        raw_dist = abs(tp - entry) if tp > 0 else (entry * 0.05)
        total_dist = max(raw_dist, min_tp_dist)
        
        if side == "BUY":
            position_details["tp_stages"] = [
                {"level": _round_price(entry + total_dist * 0.35), "percentage": 0.30, "hit": False, "label": "Partial TP1 (30%)"},
                {"level": _round_price(entry + total_dist * 0.70), "percentage": 0.30, "hit": False, "label": "Partial TP2 (30%)"},
                {"level": _round_price(tp if tp > 0 else entry + total_dist), "percentage": 0.40, "hit": False, "label": "Final TP (40%)"}
            ]
        else:
            position_details["tp_stages"] = [
                {"level": _round_price(entry - total_dist * 0.35), "percentage": 0.30, "hit": False, "label": "Partial TP1 (30%)"},
                {"level": _round_price(entry - total_dist * 0.70), "percentage": 0.30, "hit": False, "label": "Partial TP2 (30%)"},
                {"level": _round_price(tp if tp > 0 else entry - total_dist), "percentage": 0.40, "hit": False, "label": "Final TP (40%)"}
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
        position_details["grace_period_sec"] = float(position_details.get("grace_period_sec", 300.0))  # 300s (5m) Grace Period to let trend develop
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
                    try:
                        pos = self._positions.get(trade_id)
                        if not pos:
                            continue
                        
                        symbol = pos.get("symbol", "")
                        side = pos.get("side", "BUY")
                        entry = float(pos.get("entry_price", 0.0))
                        size = float(pos.get("position_size", 0.0))
                        tp = float(pos.get("take_profit", 0.0))
                        sl = float(pos.get("stop_loss", 0.0))
                        if "trailing_stop" not in pos or pos["trailing_stop"] is None:
                            pos["trailing_stop"] = float(sl)
                        if "highest_price" not in pos or pos["highest_price"] is None or float(pos.get("highest_price", 0.0)) <= 0.0:
                            pos["highest_price"] = entry
                        if "lowest_price" not in pos or pos["lowest_price"] is None or float(pos.get("lowest_price", 0.0)) <= 0.0:
                            pos["lowest_price"] = entry
                        if "opened_at" not in pos or not pos["opened_at"]:
                            pos["opened_at"] = time.time()
                        
                        current_price = cache.get_price(symbol)
                        if current_price <= 0.0:
                            logger.debug(f"Skipping position monitoring for Trade {trade_id} ({symbol}): invalid or missing price ({current_price})")
                            continue
                        
                        # 1. Regime-Adaptive Dynamic Trailing & Breakeven Protection Engine
                        regime = pos.get("market_regime", "TRENDING").upper()
                        atr = float(pos.get("atr", entry * 0.01)) if float(pos.get("atr", 0.0)) > 0.0 else entry * 0.01

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
                            be_level = _round_price(entry * 1.0005)
                            if pos.get("trailing_stop", 0.0) < be_level:
                                pos["trailing_stop"] = be_level
                                logger.warning(f"🚨 [CORRELATED MACRO SHOCK GUARD] Rapid BTC selloff ({btc_drop_pct:.2f}% drop)! Tightened trailing stop on {symbol} to breakeven ({be_level}) to prevent systemic drawdown cascade.")
                        
                        # Update peak tracking
                        if side == "BUY":
                            if current_price > float(pos.get("highest_price", entry)):
                                pos["highest_price"] = current_price
                            favorable_move = current_price - entry
                            peak_move = float(pos.get("highest_price", entry)) - entry
                        else:
                            if float(pos.get("lowest_price", entry)) == 0.0 or current_price < float(pos.get("lowest_price", entry)):
                                pos["lowest_price"] = current_price
                            favorable_move = entry - current_price
                            peak_move = entry - float(pos.get("lowest_price", entry))

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

                        # A. Breakeven Protection Guard & Auto-Breakeven Ratchet (+0.20% Profit Lock)
                        # Calculates percentage moves to guarantee micro-account fee protection
                        favorable_move_pct = (favorable_move / entry * 100.0) if entry > 0 else 0.0
                        peak_move_pct = (peak_move / entry * 100.0) if entry > 0 else 0.0

                        # Upgrade 1: Auto-Breakeven Ratchet (+0.20% Profit Lock)
                        # Triggers when favorable move reaches >= +0.80% OR ATR regime trigger is hit
                        is_ratchet_hit = (favorable_move_pct >= 0.80 or peak_move_pct >= 0.80)
                        is_atr_be_hit = (move_in_atr >= be_trigger_atr or peak_in_atr >= be_trigger_atr)

                        if is_ratchet_hit or is_atr_be_hit:
                            # If ATR hit, lock +0.35%; if ratchet hit, lock at least +0.20% net profit
                            pct_buffer = 0.0035 if is_atr_be_hit else 0.0020
                            be_level = _round_price(entry * (1.0 + pct_buffer) if side == "BUY" else entry * (1.0 - pct_buffer))
                            current_sl = float(pos.get("trailing_stop", 0.0))
                            
                            if side == "BUY" and current_sl < be_level:
                                pos["trailing_stop"] = be_level
                                if not pos.get("breakeven_activated", False):
                                    pos["breakeven_activated"] = True
                                    pos["breakeven_price"] = be_level
                                    logger.info(
                                        f"🛡️ [AUTO-BREAKEVEN RATCHET] Trade {trade_id} {symbol} ({side}, Regime: {regime}): "
                                        f"Gain reached +{max(favorable_move_pct, peak_move_pct):.2f}% (>= +0.80% threshold)! "
                                        f"Stop-Loss ratcheted to {be_level} (+{pct_buffer*100:.2f}% locked, exchange fees 100% covered). Free-roll active!"
                                    )
                                    try:
                                        from app.trading.telegram_notifier import TelegramNotifier
                                        TelegramNotifier.get_instance().send_tp_ratchet(
                                            symbol=symbol,
                                            current_price=current_price,
                                            be_level=be_level,
                                            locked_profit_pct=pct_buffer * 100.0,
                                            max_gain_pct=max(favorable_move_pct, peak_move_pct)
                                        )
                                    except Exception:
                                        pass
                            elif side == "SELL" and (current_sl > be_level or current_sl == 0.0):
                                pos["trailing_stop"] = be_level
                                if not pos.get("breakeven_activated", False):
                                    pos["breakeven_activated"] = True
                                    pos["breakeven_price"] = be_level
                                    logger.info(
                                        f"🛡️ [AUTO-BREAKEVEN RATCHET] Trade {trade_id} {symbol} ({side}, Regime: {regime}): "
                                        f"Gain reached +{max(favorable_move_pct, peak_move_pct):.2f}% (>= +0.80% threshold)! "
                                        f"Stop-Loss ratcheted to {be_level} (+{pct_buffer*100:.2f}% locked, exchange fees 100% covered). Free-roll active!"
                                    )
                                    try:
                                        from app.trading.telegram_notifier import TelegramNotifier
                                        TelegramNotifier.get_instance().send_tp_ratchet(
                                            symbol=symbol,
                                            current_price=current_price,
                                            be_level=be_level,
                                            locked_profit_pct=pct_buffer * 100.0,
                                            max_gain_pct=max(favorable_move_pct, peak_move_pct)
                                        )
                                    except Exception:
                                        pass

                        # B. Macro Inversion Defense:
                        # If the 1-hour macro market regime turns abruptly against an active position:
                        # 1. If the position is in positive territory (>= +0.20%), instantly locks breakeven.
                        # 2. If flat or underwater, tightens stop to 0.65x ATR to prevent full -1.0R drawdown during a macro waterfall.
                        try:
                            from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
                            macro_regime_data = MacroRiskOverlayEngine.get_instance().get_macro_regime_bias()
                            macro_reg = macro_regime_data.get("macro_regime", "CHOP_RANGING")
                            
                            if side == "BUY" and macro_reg == "BEAR_DISTRIBUTION":
                                if not pos.get("macro_inversion_defended", False):
                                    pos["macro_inversion_defended"] = True
                                    if favorable_move_pct >= 0.20:
                                        be_level = _round_price(entry * 1.0020)
                                        if float(pos.get("trailing_stop", 0.0)) < be_level:
                                            pos["trailing_stop"] = be_level
                                            pos["breakeven_activated"] = True
                                            logger.warning(
                                                f"🛡️ [MACRO INVERSION DEFENSE] Trade {trade_id} {symbol} BUY: 1H Macro flipped to BEAR_DISTRIBUTION! "
                                                f"Instantly locked Stop at Breakeven {be_level} (+0.20%) to defend profits."
                                            )
                                    else:
                                        tight_sl = _round_price(current_price - (atr * 0.65))
                                        if tight_sl > float(pos.get("stop_loss", 0.0)) and tight_sl > float(pos.get("trailing_stop", 0.0)):
                                            pos["trailing_stop"] = tight_sl
                                            logger.warning(
                                                f"🛡️ [MACRO INVERSION DEFENSE] Trade {trade_id} {symbol} BUY: 1H Macro flipped to BEAR_DISTRIBUTION! "
                                                f"Tightened Stop to {tight_sl} (-0.65x ATR) to cap macro waterfall risk."
                                            )
                            elif side == "SELL" and macro_reg == "BULL_EXPANSION":
                                if not pos.get("macro_inversion_defended", False):
                                    pos["macro_inversion_defended"] = True
                                    if favorable_move_pct >= 0.20:
                                        be_level = _round_price(entry * 0.9980)
                                        if float(pos.get("trailing_stop", 0.0)) > be_level or float(pos.get("trailing_stop", 0.0)) == 0.0:
                                            pos["trailing_stop"] = be_level
                                            pos["breakeven_activated"] = True
                                            logger.warning(
                                                f"🛡️ [MACRO INVERSION DEFENSE] Trade {trade_id} {symbol} SELL: 1H Macro flipped to BULL_EXPANSION! "
                                                f"Instantly locked Stop at Breakeven {be_level} (+0.20%) to defend profits."
                                            )
                                    else:
                                        tight_sl = _round_price(current_price + (atr * 0.65))
                                        if tight_sl < float(pos.get("stop_loss", 0.0)) and (float(pos.get("trailing_stop", 0.0)) == 0.0 or tight_sl < float(pos.get("trailing_stop", 0.0))):
                                            pos["trailing_stop"] = tight_sl
                                            logger.warning(
                                                f"🛡️ [MACRO INVERSION DEFENSE] Trade {trade_id} {symbol} SELL: 1H Macro flipped to BULL_EXPANSION! "
                                                f"Tightened Stop to {tight_sl} (+0.65x ATR) to cap macro short squeeze risk."
                                            )
                        except Exception as macro_err:
                            logger.debug(f"Macro Inversion Defense check failed: {macro_err}")

                        # C. Wish 2: Adaptive Volatility-Clustered Trailing & Chandelier Engine
                        try:
                            from app.trading.adaptive_trailing_engine import AdaptiveTrailingEngine
                            from app.trading.orderbook_l2_engine import OrderBookL2Engine
                            
                            ta_live_trail = cache.get_technical_analysis(symbol)
                            rvol_trail = cache.get_volume_surge_ratio(symbol)
                            rsi_trail = float(ta_live_trail.get("rsi", 50.0))
                            ob_metrics = OrderBookL2Engine.get_instance().get_orderbook_metrics(symbol)
                            live_obi = float(ob_metrics.get("obi_top5", 0.0))
                            
                            chandelier_eval = AdaptiveTrailingEngine.get_instance().evaluate_trailing_update(
                                position=pos,
                                current_price=current_price,
                                live_rvol=rvol_trail,
                                live_rsi=rsi_trail,
                                orderbook_obi=live_obi
                            )
                            
                            if chandelier_eval.get("should_update"):
                                new_sl = chandelier_eval["new_stop"]
                                old_sl = chandelier_eval["old_stop"]
                                locked_r = chandelier_eval["locked_r"]
                                peak_r = chandelier_eval["peak_r"]
                                vol_reg = chandelier_eval["volatility_regime"]
                                reason_txt = chandelier_eval["reason"]
                                
                                pos["trailing_stop"] = new_sl
                                pos["trailing_active"] = True
                                pos["chandelier_regime"] = vol_reg
                                pos["chandelier_locked_r"] = locked_r
                                
                                logger.info(
                                    f"⚡ [CHANDELIER RATCHET] Trade {trade_id} {symbol} ({side}, Regime: {vol_reg}): "
                                    f"SL updated from {old_sl} to {new_sl} (+{locked_r:.2f}R locked, Peak: +{peak_r:.2f}R). {reason_txt}"
                                )
                                
                                # Send Telegram telemetry if meaningful lock or ratchet step
                                if locked_r >= 0.5:
                                    try:
                                        from app.trading.telegram_notifier import TelegramNotifier
                                        watermark = float(pos.get("highest_price", entry) if side == "BUY" else pos.get("lowest_price", entry))
                                        TelegramNotifier.get_instance().send_chandelier_ratchet(
                                            symbol=symbol,
                                            side=side,
                                            highest_or_lowest=watermark,
                                            new_sl=new_sl,
                                            locked_r=locked_r,
                                            peak_r=peak_r,
                                            regime=vol_reg,
                                            reason=reason_txt
                                        )
                                    except Exception as tel_err:
                                        logger.debug(f"Chandelier telegram alert failed: {tel_err}")
                        except Exception as trail_err:
                            logger.error(f"Error in AdaptiveTrailingEngine evaluation for {symbol}: {trail_err}")
                        # 1b. Module 6: Adaptive Profit Optimization (TP Extension & Dynamic Trajectory)
                        try:
                            from app.trading.adaptive_profit_optimizer import AdaptiveProfitOptimizer
                            ta_live = cache.get_technical_analysis(symbol)
                            live_rvol = cache.get_volume_surge_ratio(symbol)
                            live_rsi = float(ta_live.get("rsi", 55.0))
                            ema9 = float(ta_live.get("ema9", current_price))
                            ema21 = float(ta_live.get("ema21", current_price))
                            momentum_accel = (current_price >= ema9 and ema9 >= ema21) if side == "BUY" else (current_price <= ema9 and ema9 <= ema21)

                            apo_decision = AdaptiveProfitOptimizer.get_instance().evaluate_trajectory(
                                position=pos,
                                current_price=current_price,
                                rvol=live_rvol,
                                rsi=live_rsi,
                                momentum_accelerating=momentum_accel
                            )
                            if apo_decision.action == "EXTEND_TP" and apo_decision.new_take_profit:
                                pos["take_profit"] = apo_decision.new_take_profit
                                pos["tp_extensions_count"] = int(pos.get("tp_extensions_count", 0)) + 1
                                # Update final stage level so runner is not prematurely closed at previous target
                                stages = pos.get("tp_stages", [])
                                if stages:
                                    stages[-1]["level"] = apo_decision.new_take_profit
                                if apo_decision.new_trailing_stop:
                                    pos["trailing_stop"] = apo_decision.new_trailing_stop
                                    pos["trailing_active"] = True
                                try:
                                    from app.trading.telegram_notifier import TelegramNotifier
                                    gain_pct = (current_price - entry) / entry * 100.0 if side == "BUY" else (entry - current_price) / entry * 100.0
                                    TelegramNotifier.get_instance().send_runner_extension(
                                        symbol=symbol,
                                        stage=pos["tp_extensions_count"],
                                        new_tp=apo_decision.new_take_profit,
                                        trailing_stop=apo_decision.new_trailing_stop or pos.get("trailing_stop", 0.0),
                                        current_gain_pct=gain_pct
                                    )
                                except Exception:
                                    pass
                            elif apo_decision.action == "TIGHTEN_TRAIL" and apo_decision.new_trailing_stop:
                                pos["trailing_stop"] = apo_decision.new_trailing_stop
                                pos["trailing_active"] = True
                            elif apo_decision.action == "EARLY_EXIT_HARVEST":
                                await self._close_position(trade_id, current_price, "EARLY_EXIT_HARVEST")
                                continue
                        except Exception as apo_err:
                            logger.debug(f"AdaptiveProfitOptimizer check failed: {apo_err}")

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
                                    
                                    # For small accounts (position notional < $17.50), orders cannot be split below Binance's $5.00 MIN_NOTIONAL.
                                    # On TP1, lock in Breakeven (+0.35% net profit); on TP2/Final, close 100% full position in one legal order!
                                    pos_notional = float(pos.get("position_size", 0.0)) * current_price
                                    if pos_notional < 17.50:
                                        if stage == stages[0]:
                                            be_level = _round_price(entry * 1.0035 if side == "BUY" else entry * 0.9965)
                                            if side == "BUY" and float(pos.get("trailing_stop", 0.0)) < be_level:
                                                pos["trailing_stop"] = be_level
                                                pos["breakeven_activated"] = True
                                                logger.info(f"🛡️ [MICRO-ACCOUNT TP1] Position notional (${pos_notional:.2f} < $17.50). Locked Breakeven at {be_level} (+0.35% net) without splitting below Binance $5 limit.")
                                        elif len(stages) > 2 and stage == stages[1]:
                                            # TP2 reached: Ratchet trailing stop to bank guaranteed +0.7R profit (+0.70% to +1.0% net)
                                            # and allow runner to reach Final TP (+2.2R to +3.5R asymmetric payoff)!
                                            bank_profit_dist = total_dist * 0.40
                                            bank_level = _round_price(entry + bank_profit_dist if side == "BUY" else entry - bank_profit_dist)
                                            if side == "BUY" and float(pos.get("trailing_stop", 0.0)) < bank_level:
                                                pos["trailing_stop"] = bank_level
                                                logger.info(f"💎 [MICRO-ACCOUNT TP2 PROFIT LOCK] Position notional (${pos_notional:.2f}). Ratcheted Stop to {bank_level} (+{bank_profit_dist/entry*100:.2f}% banked). Runner unleashed for Final TP target!")
                                            elif side == "SELL" and (float(pos.get("trailing_stop", 0.0)) > bank_level or float(pos.get("trailing_stop", 0.0)) == 0.0):
                                                pos["trailing_stop"] = bank_level
                                                logger.info(f"💎 [MICRO-ACCOUNT TP2 PROFIT LOCK] Position notional (${pos_notional:.2f}). Ratcheted Stop to {bank_level} (+{bank_profit_dist/entry*100:.2f}% banked). Runner unleashed for Final TP target!")
                                        else:
                                            # Final TP stage reached: Close 100% full position to bank full asymmetric win!
                                            await self._close_position(trade_id, current_price, stage["label"])
                                            stage_triggered = True
                                            break
                                    else:
                                        # If it's the final stage (or last stage of defined levels), trigger a full exit
                                        if stage == stages[-1]:
                                            await self._close_position(trade_id, current_price, "FINAL_TAKE_PROFIT_STAGE")
                                            stage_triggered = True
                                            break
                                        else:
                                            close_size = round(size * close_pct, 4)
                                            remaining_size = round(float(pos.get("position_size", 0.0)) - close_size, 4)
                                            remaining_notional = remaining_size * current_price

                                            # Dust Prevention Guard: If remaining size is below $5.05 Binance limit,
                                            # close 100% to take full profits and avoid leaving untradeable microscopic fragments
                                            if remaining_notional < 5.05:
                                                logger.info(f"⚡ [CLEAN EXIT] Remaining notional (${remaining_notional:.2f} < $5.05) would leave sub-penny dust. Closing 100% full position cleanly.")
                                                await self._close_position(trade_id, current_price, f"{stage['label']}_FULL_CLEAN")
                                                stage_triggered = True
                                                break

                                            if close_size > 0 and float(pos.get("position_size", 0.0)) > close_size:
                                                await self._partial_close_position(trade_id, current_price, close_size, stage["label"])
                                                # Reduce active position size
                                                pos["position_size"] = remaining_size
                                                
                                                # Lock in profits: raise stop loss to entry (break-even)
                                                if side == "BUY":
                                                    pos["trailing_stop"] = max(float(pos.get("trailing_stop", 0.0)), entry)
                                                else:
                                                    pos["trailing_stop"] = min(float(pos.get("trailing_stop", 0.0)), entry)
                        
                        if stage_triggered:
                            continue
                            
                        # 3. Check General Trailing Stop Loss & Hard Drawdown Circuit Breaker
                        trailing_sl_val = float(pos.get("trailing_stop", 0.0))
                        fixed_sl_val = float(pos.get("stop_loss", 0.0))
                        effective_sl = trailing_sl_val if trailing_sl_val > 0 else fixed_sl_val
                        
                        # Spread Buffer Guard: For initial stop loss, ensure SL is not placed inside the bid-ask spread (min 0.35% below entry).
                        # CRITICAL: If trailing stop or breakeven is already active above entry, NEVER drag it backwards below entry!
                        if entry > 0 and effective_sl > 0:
                            if trailing_sl_val <= 0:
                                # Initial SL only: enforce minimum 0.35% distance from entry
                                if side == "BUY" and (entry - effective_sl) / entry < 0.0035:
                                    effective_sl = round(entry * 0.9965, 6)
                                elif side == "SELL" and (effective_sl - entry) / entry < 0.0035:
                                    effective_sl = round(entry * 1.0035, 6)
                            else:
                                # Trailing stop is active: preserve ratchet level without overriding to a losing level
                                effective_sl = trailing_sl_val

                        holding_sec = time.time() - float(pos.get("opened_at", time.time()))
                        is_sl_hit = False

                        # If position opened under 5s ago, ignore micro-ticks within 0.30% to prevent instant spread stop-outs
                        if holding_sec < 5.0:
                            micro_loss = ((entry - current_price) / entry * 100.0) if side == "BUY" else ((current_price - entry) / entry * 100.0)
                            if micro_loss >= 0.80:
                                is_sl_hit = True
                        else:
                            if side == "BUY" and effective_sl > 0 and current_price <= effective_sl:
                                is_sl_hit = True
                            elif side == "SELL" and effective_sl > 0 and current_price >= effective_sl:
                                is_sl_hit = True

                        # Emergency Drawdown Guard: If position moves >= 1.2% against entry (>= 18% loss at 15x), force emergency close!
                        loss_against_entry_pct = ((entry - current_price) / entry * 100.0) if side == "BUY" else ((current_price - entry) / entry * 100.0)
                        if loss_against_entry_pct >= 1.2:
                            is_sl_hit = True
                            sl_exit_label = "EMERGENCY_DRAWDOWN_CUT"
                        else:
                            sl_exit_label = "STOP_LOSS"

                        if is_sl_hit:
                            await self._close_position(trade_id, current_price, sl_exit_label)
                            continue

                        # 4. Intelligent Early Exit Engine (Thesis Invalidation Check with Grace Period & Gradual Decay)
                        from app.trading.mtf_confirmation import MTFConfirmationGuard
                        holding_time_sec = time.time() - pos.get("opened_at", time.time())
                        grace_period_sec = pos.get("grace_period_sec", 300.0)

                        # Profit Protection: Never early-invalidate positions currently in profit.
                        # Let Trailing Stop and Take-Profit engines manage winning trades to capture full R:R expansions.
                        entry_px = float(pos.get("entry_price", 0.0))
                        is_in_profit = (current_price > entry_px) if side == "BUY" else (current_price < entry_px)
                        if is_in_profit:
                            continue

                        invalidity_strength, mtf_details = await MTFConfirmationGuard.get_instance().evaluate_weighted_mtf_invalidation(symbol, side, current_price)

                        # Trend Persistence Engine: maintain trend history
                        trend_history = pos.setdefault("trend_history", [])
                        trend_history.append(invalidity_strength)
                        if len(trend_history) > 10:
                            trend_history.pop(0)

                        thesis_strength = pos.get("thesis_strength", 0.83)
                        holding_time_min = holding_time_sec / 60.0

                        # Gradual Invalidation Decay: Maintain institutional standard for first 20 mins, then linearly decays to 0.68 by minute 60
                        if holding_time_min <= 20.0:
                            required_invalidity_strength = thesis_strength
                        else:
                            decay_ratio = min(1.0, (holding_time_min - 20.0) / 40.0)
                            required_invalidity_strength = max(0.68, thesis_strength - (0.15 * decay_ratio))

                        strength_succeeded = invalidity_strength >= required_invalidity_strength

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

                        # Reversal must be confirmed by sustained structural pressure (>= 60s and >= 60 cycles)
                        persist_confirmed = (consecutive_cycles >= 60 and persistence_time_sec >= 60.0)

                        if strength_succeeded and persist_confirmed:
                            if holding_time_sec < grace_period_sec:
                                logger.info(
                                    f"🛡️ [GRACE PERIOD PROTECTED] Trade {trade_id} {symbol} ({side}): "
                                    f"Thesis invalidation signal suppressed during Grace Period ({holding_time_sec:.1f}s / {grace_period_sec:.0f}s)."
                                )
                            else:
                                logger.warning(
                                    f"🚪 [INTELLIGENT EARLY EXIT - THESIS INVALIDATED]\n"
                                    f"  - Trade ID: {trade_id} ({symbol} {side})\n"
                                    f"  - Invalidation Strength: {invalidity_strength:.2f} >= Required: {required_invalidity_strength:.2f} (Holding: {holding_time_min:.1f}m)\n"
                                    f"  - Final Decision: CLOSED via THESIS_INVALIDATION"
                                )
                                await self._close_position(trade_id, current_price, "THESIS_INVALIDATION")
                                continue

                        # 5. Strategy-Adaptive Stale Trade Exit Engine (Fast Capital Recycling)
                        opened_at = pos.get("opened_at", time.time())
                        time_open_sec = time.time() - opened_at
                        strat_name = str(pos.get("strategy", pos.get("entry_strategy", pos.get("strategy_used", "Scalping")))).lower()
                        
                        # Strategy-adaptive holding timeouts (Scalp: 25m, Range/Mean-Rev: 45m, Breakout: 75m, Trend: 120m)
                        if "scalp" in strat_name:
                            base_stale_limit_sec = 25 * 60
                        elif "mean_reversion" in strat_name or "ranging" in strat_name or "chop" in strat_name:
                            base_stale_limit_sec = 45 * 60
                        elif "breakout" in strat_name or "squeeze" in strat_name:
                            base_stale_limit_sec = 75 * 60
                        else:
                            base_stale_limit_sec = 120 * 60

                        stale_limit_sec = pos.get("stale_threshold_sec", base_stale_limit_sec)
                        stale_pct_limit = pos.get("stale_price_range_pct", 0.35)
                        price_change_pct = (abs(current_price - entry) / entry * 100.0) if entry > 0 else 0.0
                        # Hard maximum cap: Exceeding 120m (or 60m for scalp) triggers mandatory exit
                        hard_max_time_sec = 60 * 60 if "scalp" in strat_name else 120 * 60
                        is_hard_max_exceeded = time_open_sec >= hard_max_time_sec
                        is_stale_stall = (time_open_sec >= stale_limit_sec and price_change_pct <= stale_pct_limit)

                        # Winner Protection & Timeout Immunity:
                        # Never kill an active, profitable runner or a trade whose trailing stop is locked in profit!
                        # Let the trailing stop protect the capital while allowing 5%-20% expansions to develop.
                        is_pos_in_profit = (current_price > entry) if side == "BUY" else (current_price < entry)
                        has_profit_protection = is_pos_in_profit and (pos.get("trailing_active", False) or pos.get("breakeven_activated", False))

                        if has_profit_protection:
                            # Position is in profit and trailing/breakeven protected - exempt from timeout
                            pass
                        elif is_hard_max_exceeded or is_stale_stall:
                            open_mins = round(time_open_sec / 60.0, 1)
                            reason_tag = "HARD_TIMEOUT_EXIT" if is_hard_max_exceeded else "STALE_MOMENTUM_ROTATE"
                            logger.warning(
                                f"⏳ [{reason_tag}] Trade {trade_id} {symbol} ({side}, Strategy: {strat_name}): "
                                f"Open for {open_mins}m (Hard cap: {hard_max_time_sec/60:.0f}m, Stale threshold: {stale_limit_sec/60:.0f}m). "
                                f"Exiting stagnant position to recycle capital into higher-ranked opportunities."
                            )
                            await self._close_position(trade_id, current_price, reason_tag)
                            continue

                        # 6. Pyramiding Scale-In Evaluation for Winning Trends
                        from app.trading.pyramiding import PyramidingEngine
                        await PyramidingEngine.get_instance().evaluate_scale_in(pos, current_price)
                    except Exception as pe:
                        logger.error(f"Error monitoring position {trade_id}: {pe}", exc_info=True)
                
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
            connector = BinanceConnector.get_instance()
            trading_venue = getattr(scheduler, "trading_venue", "SPOT").upper()

            if trading_venue == "FUTURES":
                filters = await connector.get_futures_symbol_filters(symbol, trading_mode)
                prec = filters.get("precision", 3)
                step_size = float(filters.get("step_size", 0.001))
                min_qty = float(filters.get("min_qty", step_size))

                legal_close_qty = math.floor(close_size / step_size) * step_size
                if legal_close_qty < min_qty:
                    legal_close_qty = min_qty

                futures_close_notional = legal_close_qty * exit_price
                if futures_close_notional < 5.0:
                    logger.warning(
                        f"⚠️ [FUTURES MIN_NOTIONAL GUARD] Partial close value (${futures_close_notional:.2f}) "
                        f"for {symbol} is below Binance Futures $5.00 minimum notional. "
                        f"Escalating to full position close to ensure order execution and cleanly bank profits into USDT."
                    )
                    await self._close_position(trade_id, exit_price, f"{stage_label}_FULL_CLOSE")
                    return

                formatted_close_qty = connector.format_quantity_for_symbol(symbol, legal_close_qty, prec)
                logger.info(f"🚀 [BINANCE FUTURES PARTIAL CLOSE] Placing real Binance Futures partial close for {symbol} ({opposite_side} qty: {formatted_close_qty}, reduce_only=True) in {trading_mode} mode...")
                binance_res = await connector.create_futures_order(
                    mode=trading_mode,
                    symbol=symbol,
                    side=opposite_side,
                    order_type="MARKET",
                    quantity=formatted_close_qty,
                    reduce_only=True
                )
            else:
                filters = await connector.get_symbol_lot_size_filters(symbol, trading_mode)
                prec = filters.get("precision", 4)
                step_size = float(filters.get("step_size", 10 ** (-prec)))
                min_qty = float(filters.get("min_qty", step_size))

                legal_close_qty = math.floor(close_size / step_size) * step_size
                if legal_close_qty < min_qty:
                    legal_close_qty = min_qty

                min_notional = float(filters.get("min_notional", 5.0))
                if (legal_close_qty * exit_price) < min_notional:
                    logger.warning(
                        f"⚠️ [MICRO-ACCOUNT NOTIONAL GUARD] Partial close value (${legal_close_qty * exit_price:.2f}) "
                        f"is below Binance minimum notional (${min_notional:.2f}). "
                        f"Escalating to full position close to prevent order rejection and cleanly recover capital into USDT."
                    )
                    await self._close_position(trade_id, exit_price, f"{stage_label}_FULL_CLOSE")
                    return

                formatted_close_qty = connector.format_quantity_for_symbol(symbol, legal_close_qty, prec)
                logger.info(f"Placing real Binance Spot partial close order for {symbol} ({opposite_side} qty: {formatted_close_qty}, prec: {prec}) in {trading_mode} mode...")
                binance_res = await connector.create_order(
                    mode=trading_mode,
                    symbol=symbol,
                    side=opposite_side,
                    order_type="MARKET",
                    quantity=formatted_close_qty
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
                if not trade:
                    from sqlalchemy.future import select
                    from app.models.trading import Trade
                    q = select(Trade).where(
                        Trade.symbol == symbol,
                        Trade.trading_mode == trading_mode,
                        Trade.exit_time.is_(None)
                    ).order_by(Trade.entry_time.desc())
                    trade = (await db.execute(q)).scalars().first()
                if trade:
                    current_pnl = trade.profit_loss or 0.0
                    new_pnl = current_pnl + pnl
                    new_size = max(0.0, trade.position_size - close_size)
                    
                    trade_update = TradeUpdate(
                        profit_loss=new_pnl,
                        position_size=new_size
                    )
                    await TradingService.update_trade(db, trade.id, trade_update)
                
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
        
        # If in TESTNET or LIVE, place closing order on Binance using Smart Exit Engine
        from app.services.binance_connector import BinanceConnector
        base_asset = symbol.replace("USDT", "")
        if trading_mode in ["TESTNET", "LIVE"]:
            opposite_side = "SELL" if side == "BUY" else "BUY"
            connector = BinanceConnector.get_instance()
            trading_venue = getattr(scheduler, "trading_venue", "SPOT").upper()

            if trading_venue == "FUTURES":
                filters = await connector.get_futures_symbol_filters(symbol, trading_mode)
                prec = filters.get("precision", 3)
                step_size = float(filters.get("step_size", 0.001))
                legal_qty = math.floor(size / step_size) * step_size
                close_qty = round(legal_qty, prec)
                logger.info(f"🚀 [BINANCE FUTURES CLOSE] Placing real Binance Futures close order for {symbol} ({opposite_side} qty: {close_qty}, reduce_only=True, exit: {outcome_type}) in {trading_mode} mode...")
                binance_res = await connector.create_futures_order(
                    mode=trading_mode,
                    symbol=symbol,
                    side=opposite_side,
                    order_type="MARKET",
                    quantity=close_qty,
                    reduce_only=True
                )
                if "error" in binance_res or ("code" in binance_res and isinstance(binance_res["code"], int) and binance_res["code"] < 0) or ("status_code" in binance_res and binance_res.get("status_code", 200) >= 400):
                    logger.error(f"❌ BINANCE FUTURES CLOSE ORDER FAILED for {symbol}: {binance_res}")
                    # Re-insert position so tracking is not permanently lost on network or filter glitch
                    self._positions[trade_id] = pos
                    return
                else:
                    logger.info(f"✅ [BINANCE FUTURES CLOSED] Contract position for {symbol} cleanly liquidated into USDT: {binance_res}")
            else:
                filters = await connector.get_symbol_lot_size_filters(symbol, trading_mode)
                prec = filters.get("precision", 4)
                step_size = float(filters.get("step_size", 10 ** (-prec)))
                min_qty = float(filters.get("min_qty", step_size))

                # Smart Exit Engine: Query Binance for actual free balance before selling
                sell_qty = size
                if opposite_side == "SELL":
                    actual_free = await connector.get_free_asset_balance(base_asset, trading_mode)
                    if actual_free > 0.0:
                        legal_qty = math.floor(actual_free / step_size) * step_size
                        if legal_qty >= min_qty:
                            logger.info(f"🔍 [SMART EXIT ENGINE] Binance reports {actual_free} {base_asset} free. Maximum legal sell qty calculated as {legal_qty} (DB record had {size}).")
                            sell_qty = legal_qty
                        else:
                            logger.warning(f"⚠️ [SMART EXIT ENGINE] Actual free {actual_free} {base_asset} below min_qty {min_qty}. Using internal size {size}.")

                close_qty = connector.format_quantity_for_symbol(symbol, sell_qty, prec)
                logger.info(f"Placing real Binance Spot close order for {symbol} ({opposite_side} qty: {close_qty}, precision: {prec}) in {trading_mode} mode...")
                binance_res = await connector.create_order(
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
                        f"⚠️ Spot market sell blocked for {symbol} ({binance_res.get('msg', 'Filter error')}). "
                        f"Initiating automatic Simple Earn flexible redemption and Binance Convert to USDT..."
                    )
                    convert_success = False
                    try:
                        # Step 1: Preemptively redeem any Soft Staking / Flexible Simple Earn
                        await connector.redeem_flexible_simple_earn(base_asset, trading_mode)
                        await asyncio.sleep(0.5)

                        # Step 2: Attempt zero-fee Binance Convert to USDT (bypasses $5.00 MIN_NOTIONAL)
                        actual_free_after_redeem = await connector.get_free_asset_balance(base_asset, trading_mode)
                        amt_to_convert = actual_free_after_redeem if actual_free_after_redeem > 0 else sell_qty
                        convert_res = await connector.convert_asset_to_usdt(base_asset, amt_to_convert, trading_mode)

                        if isinstance(convert_res, dict) and (convert_res.get("orderStatus") == "SUCCESS" or "quoteId" in convert_res or convert_res.get("status") == "SUCCESS"):
                            logger.info(f"✅ [SMART CONVERT RECOVERY] Successfully redeemed & liquidated {amt_to_convert} {base_asset} to USDT via Binance Convert API: {convert_res}")
                            convert_success = True
                    except Exception as conv_err:
                        logger.error(f"Binance Convert fallback error for {symbol}: {conv_err}")

                    if not convert_success:
                        logger.warning(
                            f"⚠️ BINANCE CLOSE CANNOT EXECUTE ON EXCHANGE ({symbol} qty {size}): {binance_res}. "
                            f"Finalizing internal position tracking to prevent infinite retry loops."
                        )
                    try:
                        from app.services.audit_service import AuditService
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="INFO" if convert_success else "WARNING",
                                category="TRADING",
                                message=f"Binance position liquidated to USDT via {'Convert' if convert_success else 'local close'} for {symbol}",
                                details=f"Size: {size}. Details: {binance_res}"
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
        
        try:
            from app.trading.telegram_notifier import TelegramNotifier
            duration_min = 0.0
            if "entry_time" in pos:
                t_entry = pos["entry_time"]
                if hasattr(t_entry, "timestamp"):
                    duration_min = max(0.1, (time.time() - t_entry.timestamp()) / 60.0)
            pnl_pct = (pnl / (entry * size)) * 100.0 if (entry * size) > 0 else 0.0
            TelegramNotifier.get_instance().send_trade_closed(
                symbol=symbol,
                side=side,
                entry_price=entry,
                exit_price=exit_price,
                pnl_usd=round(pnl, 2),
                pnl_pct=round(pnl_pct, 2),
                duration_mins=round(duration_min, 1),
                outcome_type=outcome_type
            )
        except Exception as tg_close_err:
            logger.error(f"Failed to dispatch Telegram trade closed alert: {tg_close_err}")

        from app.services.trading_service import TradingService
        from app.services.paper_trading_service import PaperTradingService
        from app.services.audit_service import AuditService

        async with AsyncSessionLocal() as db:
            try:
                # Record trade result in InstitutionalRiskEngine for consecutive loss & circuit breaker tracking
                try:
                    from app.trading.institutional_risk import InstitutionalRiskEngine
                    InstitutionalRiskEngine.get_instance().record_trade_result(pnl)
                except Exception as ir_err:
                    logger.debug(f"InstitutionalRiskEngine record trade error: {ir_err}")

                # Record trade result in ModelGovernanceEngine for rolling safety circuit evaluation
                try:
                    from app.trading.model_governance import ModelGovernanceEngine
                    ModelGovernanceEngine.get_instance().record_version_trade_outcome({
                        "symbol": symbol,
                        "side": side,
                        "pnl": pnl,
                        "profit_loss": pnl,
                        "outcome": outcome,
                        "strategy": pos.get("strategy_used", "Breakout Momentum"),
                        "timestamp": time.time()
                    })
                except Exception as mg_err:
                    logger.debug(f"ModelGovernanceEngine record trade error: {mg_err}")

                # 1. Update Trade Record in DB
                trade_update = TradeUpdate(
                    exit_price=exit_price,
                    profit_loss=pnl,
                    exit_time=datetime.utcnow(),
                    outcome=outcome
                )
                updated_trade = await TradingService.update_trade(db, trade_id, trade_update)
                if not updated_trade:
                    # Fallback A: match unclosed Trade by symbol and trading_mode
                    from sqlalchemy.future import select
                    from app.models.trading import Trade
                    q = select(Trade).where(
                        Trade.symbol == symbol,
                        Trade.trading_mode == trading_mode,
                        Trade.exit_time.is_(None)
                    ).order_by(Trade.entry_time.desc())
                    matched_trade = (await db.execute(q)).scalars().first()
                    if matched_trade:
                        await TradingService.update_trade(db, matched_trade.id, trade_update)
                        await db.commit()
                        logger.info(f"💾 [DB RECOVERY] Successfully matched and updated closed Trade #{matched_trade.id} for {symbol} ({outcome}).")
                    else:
                        # Fallback B: insert completed Trade record so no closed trade is ever lost
                        recovered_trade = Trade(
                            symbol=symbol,
                            side=side,
                            entry_price=entry,
                            exit_price=exit_price,
                            position_size=size,
                            profit_loss=pnl,
                            strategy_used=pos.get("strategy", "PositionManager"),
                            market_regime=pos.get("market_regime", "TRENDING"),
                            confidence_score=float(pos.get("confidence", 0.8)),
                            entry_reason="Position closed and persisted by PositionManager",
                            outcome=outcome,
                            trading_mode=trading_mode,
                            exit_time=datetime.utcnow()
                        )
                        db.add(recovered_trade)
                        await db.commit()
                        logger.info(f"💾 [DB PERSISTENCE] Inserted completed trade for {symbol} (PnL: ${pnl:.4f}) into database.")
                
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
                    # Conclude persistent market thesis
                    try:
                        from app.trading.thesis_persistence import ThesisPersistenceEngine
                        ThesisPersistenceEngine.get_instance().close_or_invalidate_thesis(symbol, outcome_type)
                    except Exception as tpe_err:
                        logger.error(f"Failed to conclude thesis in ThesisPersistenceEngine: {tpe_err}")

                    # Enforce post-trade cooldown on closed symbol to prevent rapid churn:
                    # - On LOSS (or consecutive cuts): 18 minutes (1080s) strict cooldown to avoid chop traps.
                    # - On WIN / BREAKEVEN: 5 minutes (300s) cooldown to allow runner re-entries.
                    try:
                        from app.ai.reasoning import AiReasoningEngine
                        is_loss = (outcome_type == "LOSS" or (pnl is not None and float(pnl) < -0.001))
                        cooldown_sec = 1080.0 if is_loss else 300.0
                        reason_msg = (
                            f"Post-loss 18m cooldown on {symbol} to prevent rapid chop re-entry ({outcome})."
                            if is_loss else
                            f"Post-win 5m stabilization cooldown on {symbol} ({outcome})."
                        )
                        AiReasoningEngine.get_instance().set_cooldown(
                            symbol=symbol,
                            side="ALL",
                            category="POST_LOSS_COOLDOWN" if is_loss else "POST_TRADE_COOLDOWN",
                            duration_seconds=cooldown_sec,
                            reason=reason_msg
                        )
                        logger.info(f"⏳ [SYMBOL COOLDOWN] {symbol}: Applied {cooldown_sec/60:.0f}m cooldown after {outcome_type} (PnL: {pnl:+.4f} USDT).")
                    except Exception as cd_err:
                        logger.debug(f"Failed to set post-trade cooldown: {cd_err}")

                    asyncio.create_task(SelfAdaptationEngine.get_instance().analyze_performance())
                    asyncio.create_task(TradeJournalEngine.get_instance().journal_closed_trade(trade_id))
                    ChampionChallengerEngine.get_instance().record_trade_outcome(
                        strategy=pos.get("strategy", "Breakout Momentum"),
                        pnl=pnl,
                        exit_reason=outcome_type
                    )
                    asyncio.create_task(ChampionChallengerEngine.get_instance().evaluate_strategies())

                    # Notify Opportunity Queue to re-evaluate highest-ranked candidate using released capital
                    try:
                        from app.trading.opportunity_queue import OpportunityQueueEngine
                        asyncio.create_task(OpportunityQueueEngine.get_instance().on_position_closed_event(symbol, pnl))
                    except Exception as qe:
                        logger.warning(f"Failed to notify Opportunity Queue: {qe}")

                    # Notify Dust Recovery Engine only for SPOT venue trades (Futures settles in USDT margin)
                    try:
                        from app.services.binance_connector import BinanceConnector
                        if BinanceConnector.get_instance().trading_venue.upper() == "SPOT":
                            from app.trading.dust_recovery_engine import DustRecoveryEngine
                            asyncio.create_task(DustRecoveryEngine.get_instance().on_trade_completed(
                                symbol=symbol,
                                base_asset=base_asset,
                                exit_price=exit_price,
                                trading_mode=trading_mode,
                                trade_size_usdt=exit_price * size
                            ))
                    except Exception as dre_err:
                        logger.warning(f"Failed to notify DustRecoveryEngine: {dre_err}")

                    # Module 1 & 8 & Strategy Portfolio Manager: Record rich snapshot & update allocations
                    try:
                        from app.ai.contextual_trade_memory import ContextualTradeMemory
                        from app.trading.dynamic_risk_budget import DynamicRiskBudgetEngine
                        from app.trading.strategy_portfolio_manager import StrategyPortfolioManager
                        initial_risk = float(pos.get("risk_amount", abs(entry - float(pos.get("stop_loss", entry*0.98))) * size))
                        r_mult = pnl / max(1.0, initial_risk)
                        strat_name = pos.get("strategy", "Breakout Momentum")
                        DynamicRiskBudgetEngine.get_instance().record_trade_completion(pnl, r_mult)
                        StrategyPortfolioManager.get_instance().record_strategy_trade(
                            strategy_name=strat_name,
                            realized_r=r_mult,
                            pnl=pnl
                        )
                        time_open_sec = holding_minutes * 60.0
                        ContextualTradeMemory.get_instance().record_completed_trade(
                            trade_id=str(trade_id),
                            symbol=symbol,
                            strategy=strat_name,
                            regime=pos.get("market_regime", "TRENDING"),
                            atr=float(pos.get("atr", entry * 0.015)),
                            entry_price=entry,
                            pnl=pnl,
                            exit_reason=outcome_type,
                            holding_seconds=time_open_sec,
                            risk_amount=initial_risk,
                            confidence=float(pos.get("confidence", 0.80)),
                            macro_score=40.0
                        )
                    except Exception as ctm_err:
                        logger.warning(f"Failed to record in ContextualTradeMemory / DynamicRiskBudgetEngine / StrategyPortfolioManager: {ctm_err}")
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
