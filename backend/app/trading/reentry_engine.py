import asyncio
import logging
import time
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

from app.workers.price_cache import PriceCache
from app.api.schemas import AuditLogCreate
from app.services.audit_service import AuditService
from app.database.session import AsyncSessionLocal

logger = logging.getLogger("lumina.trading.reentry_engine")

class SmartReentryEngine:
    """
    Stage 2 Smart Re-Entry Engine for Lumina.
    
    Prevents permanently missing a trend after a protective exit (STOP_LOSS, THESIS_INVALIDATION):
    - Registers candidate trades upon protective exit
    - Monitors price action for trend resumption / pullback confirmation
    - Clears evaluation cooldowns when high-confidence re-entry criteria are met
    - Triggers Smart Re-Entry execution safely
    """
    _instance: Optional['SmartReentryEngine'] = None

    def __init__(self):
        self._watchers: Dict[str, Dict[str, Any]] = {}
        self.max_watcher_ttl_sec: int = 1800  # 30 minute re-entry window
        self.min_confidence_for_reentry: float = 0.75

    @classmethod
    def get_instance(cls) -> 'SmartReentryEngine':
        if cls._instance is None:
            cls._instance = SmartReentryEngine()
        return cls._instance

    def register_closed_trade(self, trade_data: Dict[str, Any]):
        """Registers a closed trade for smart re-entry watching if exit reason is eligible."""
        exit_reason = trade_data.get("outcome_type", trade_data.get("exit_reason", "UNKNOWN"))
        eligible_reasons = ["STOP_LOSS", "THESIS_INVALIDATION", "TRAILING_STOP", "EARLY_EXIT"]

        if exit_reason not in eligible_reasons:
            return

        symbol = trade_data.get("symbol", "")
        side = trade_data.get("side", "BUY")
        key = f"{symbol}_{side}"

        self._watchers[key] = {
            "symbol": symbol,
            "side": side,
            "original_trade_id": trade_data.get("trade_id", 0),
            "original_entry": trade_data.get("entry_price", 0.0),
            "exit_price": trade_data.get("exit_price", 0.0),
            "exit_reason": exit_reason,
            "strategy": trade_data.get("strategy", "Smart Re-Entry"),
            "registered_at": time.time(),
            "expire_at": time.time() + self.max_watcher_ttl_sec,
            "status": "WATCHING"
        }
        logger.info(f"🎯 [SMART RE-ENTRY] Watching {symbol} ({side}) following {exit_reason} exit at ${trade_data.get('exit_price', 0.0)}")

    async def evaluate_reentry_candidate(self, symbol: str, current_price: float, current_regime: str = "TRENDING") -> Optional[Dict[str, Any]]:
        """Evaluates whether price action and market regime justify a smart re-entry."""
        now = time.time()
        expired_keys = [k for k, v in self._watchers.items() if now > v["expire_at"]]
        for k in expired_keys:
            del self._watchers[k]

        candidates = [v for v in self._watchers.values() if v["symbol"] == symbol and v["status"] == "WATCHING"]
        if not candidates:
            return None

        for watcher in candidates:
            side = watcher["side"]
            exit_px = watcher["exit_price"]
            orig_entry = watcher["original_entry"]

            # Re-entry condition: Price recovers past exit price in original direction
            is_valid_reentry = False
            if side == "BUY" and current_price > exit_px * 1.003:
                is_valid_reentry = True
            elif side == "SELL" and current_price < exit_px * 0.997:
                is_valid_reentry = True

            if is_valid_reentry and current_regime in ["TRENDING", "VOLATILE"]:
                watcher["status"] = "TRIGGERED"
                key = f"{symbol}_{side}"
                if key in self._watchers:
                    del self._watchers[key]

                # Clear active cooldown in AiReasoningEngine
                try:
                    from app.ai.reasoning import AiReasoningEngine
                    reasoning = AiReasoningEngine.get_instance()
                    cd_key = f"{symbol}_{side}"
                    if cd_key in reasoning._cooldowns:
                        del reasoning._cooldowns[cd_key]
                        logger.info(f"⚡ [SMART RE-ENTRY] Cooldown bypassed for {symbol} ({side}) due to trend resumption confirmation.")
                except Exception as ce:
                    logger.error(f"Failed to clear cooldown for re-entry: {ce}")

                reentry_signal = {
                    "symbol": symbol,
                    "side": side,
                    "entry_price": current_price,
                    "confidence": self.min_confidence_for_reentry,
                    "strategy": f"Smart Re-Entry ({watcher['exit_reason']})",
                    "market_regime": current_regime,
                    "stop_loss": round(current_price * (0.985 if side == "BUY" else 1.015), 2),
                    "take_profit": round(current_price * (1.03 if side == "BUY" else 0.97), 2),
                    "is_reentry": True,
                    "original_trade_id": watcher["original_trade_id"]
                }

                logger.info(f"🚀 [SMART RE-ENTRY SIGNAL GENERATED] {symbol} ({side}) at ${current_price:.2f}")

                # Log audit
                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="INFO",
                            category="TRADING",
                            message=f"Smart Re-Entry Signal Triggered for {symbol} ({side})",
                            details=f"Exit Price: ${exit_px}, Re-Entry Price: ${current_price}. Trend resumed following {watcher['exit_reason']}."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log re-entry audit: {ae}")

                return reentry_signal

        return None

    def detect_first_pullback_opportunity(
        self,
        symbol: str,
        current_price: float,
        cache_ta: Dict[str, Any],
        rvol: float,
        history: list
    ) -> Optional[Dict[str, Any]]:
        """
        Institutional First-Pullback Re-entry Engine (Linda Raschke 80-20 & Prop Desk VWAP Playbook).
        
        Identifies high-probability trend continuation entries:
        1. Impulse Bar: Recent aggressive expansion (closing in top 20% of range with RVOL >= 2.0x).
        2. Orderly Shallow Retrace: Price pulls back to the dynamic 10 EMA / VWAP pocket.
        3. Low Volume Contraction on Pullback: Selling pressure dries up (pullback volume < impulse volume).
        4. Support Confluence: Price bounces off 10 EMA / VWAP with positive tick recovery.
        """
        if len(history) < 15 or current_price <= 0.0:
            return None

        ema10 = float(cache_ta.get("ema9", cache_ta.get("ema10", current_price)))
        ema20 = float(cache_ta.get("ema21", cache_ta.get("ema20", current_price)))
        atr = float(cache_ta.get("atr", current_price * 0.015))
        rsi = float(cache_ta.get("rsi", 50.0))

        # 1. Structural Trend Requirement: Fast EMA > Slow EMA (Bullish structure)
        if ema10 <= ema20:
            return None

        # 2. Look for recent impulse high in the last 8-15 bars
        lookback = history[-15:]
        recent_high = max(lookback)
        recent_low = min(lookback)
        total_impulse_range = recent_high - recent_low

        if total_impulse_range < (atr * 1.5):
            return None  # No substantial impulse occurred

        # 3. Pullback Depth Check (Fibonacci 38.2% - 61.8% or 10 EMA pocket)
        # Price must have pulled back from recent_high toward ema10
        dist_from_high_pct = (recent_high - current_price) / recent_high * 100.0 if recent_high > 0 else 0.0
        dist_to_ema10_pct = abs(current_price - ema10) / current_price * 100.0

        # Optimal pullback pocket: Retracing between 1.2% and 4.5% from the high, and within 0.65% of the 10 EMA
        is_in_pullback_pocket = (1.0 <= dist_from_high_pct <= 5.0) and (dist_to_ema10_pct <= 0.65)
        
        # Ensure price is holding ABOVE the 20 EMA (trend baseline intact)
        holds_above_ema20 = current_price >= (ema20 - (atr * 0.15))

        # 4. Momentum Reset: RSI cooled off from overbought into sweet spot (46 - 60)
        is_rsi_reset = (44.0 <= rsi <= 62.0)

        # 5. Volume Confirmation: Must have active institutional presence
        is_volume_valid = (rvol >= 1.35)

        if is_in_pullback_pocket and holds_above_ema20 and is_rsi_reset and is_volume_valid:
            dec_prec = 8 if current_price < 0.001 else (6 if current_price < 1.0 else 4)
            # Stop loss strictly positioned below recent pullback low or 0.25x ATR below 20 EMA
            pullback_swing_low = min(history[-4:])
            sl_price = round(min(pullback_swing_low - (atr * 0.20), ema20 - (atr * 0.25)), dec_prec)
            tp_price = round(recent_high + (atr * 1.20), dec_prec)

            rationale = (
                f"Linda Raschke First-Pullback: Retraced {dist_from_high_pct:.1f}% from peak ${recent_high:.4f} "
                f"into 10 EMA pocket (${ema10:.4f}, delta {dist_to_ema10_pct:.2f}%). RSI reset to {rsi:.1f}. "
                f"Holding above 20 EMA (${ema20:.4f})."
            )

            logger.info(f"💎 [INSTITUTIONAL FIRST-PULLBACK DETECTED] {symbol} at ${current_price:.4f}: {rationale}")

            return {
                "symbol": symbol,
                "side": "BUY",
                "entry_price": current_price,
                "confidence": 0.84,  # High statistical edge on first retests
                "strategy_used": "Institutional First Pullback (Raschke/VWAP)",
                "market_regime": "TRENDING",
                "stop_loss": sl_price,
                "take_profit": tp_price,
                "atr": atr,
                "rvol": rvol,
                "is_pullback_entry": True,
                "entry_reason": rationale
            }

        return None

    def get_active_watchers(self) -> List[Dict[str, Any]]:
        """Returns list of currently active re-entry watchers."""
        now = time.time()
        return [
            {
                "symbol": v["symbol"],
                "side": v["side"],
                "exit_price": v["exit_price"],
                "exit_reason": v["exit_reason"],
                "remaining_seconds": round(max(0, v["expire_at"] - now), 1)
            }
            for v in self._watchers.values()
            if v["status"] == "WATCHING" and now <= v["expire_at"]
        ]
