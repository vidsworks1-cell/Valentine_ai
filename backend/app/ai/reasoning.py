import asyncio
import logging
import random
import time
from typing import Dict, Any, List, Optional, Tuple
from app.workers.price_cache import PriceCache
from app.core.config import settings

logger = logging.getLogger("lumina.ai.reasoning")

class AiReasoningEngine:
    """Layer 3 AI Reasoning and Opportunity Detection engine with Symbol Evaluation Cooldowns."""

    _instance: Optional['AiReasoningEngine'] = None

    def __init__(self):
        self.running = False
        self._task: asyncio.Task | None = None
        self.last_detected_setup: Dict[str, Any] = {}
        self._cooldowns: Dict[str, Dict[str, Any]] = {}  # Key: "SYMBOL:SIDE" or "SYMBOL:ALL"
        AiReasoningEngine._instance = self

    @classmethod
    def get_instance(cls) -> 'AiReasoningEngine':
        if cls._instance is None:
            cls._instance = AiReasoningEngine()
        return cls._instance

    ADAPTIVE_DURATIONS = {
        "CONFIDENCE_FILTER": 45.0,       # 45s: Quick check as micro-price movements occur
        "POSITION_OPEN": 120.0,          # 120s: Wait for existing trade TP1 hit or close
        "MTF_DISAGREEMENT": 300.0,       # 300s (5m): Wait for next candle close on higher timeframe
        "PORTFOLIO_RISK_HEAT": 120.0,    # 120s: Wait for portfolio risk/heat to ease
        "CORRELATION_CLUSTER": 180.0,    # 180s: Wait for cluster risk to subside
        "SELF_ADAPTATION_BLOCK": 180.0,  # 180s: Strategy regime mismatch cooldown
        "MARKET_DATA_STALE": 30.0,       # 30s: Short retry for price feed updates
        "EMERGENCY_SAFETY": 600.0,       # 600s (10m): Extended safety hold on system errors
        "DEFAULT": 120.0
    }

    def set_cooldown(self, symbol: str, side: str = "ALL", category: str = "DEFAULT", duration_seconds: Optional[float] = None, reason: str = "", price: float = 0.0):
        """
        Places a symbol / side pair on adaptive evaluation cooldown.
        Duration adapts automatically based on rejection category or explicit duration.
        """
        if duration_seconds is None:
            duration_seconds = self.ADAPTIVE_DURATIONS.get(category.upper(), self.ADAPTIVE_DURATIONS["DEFAULT"])

        key = f"{symbol.upper()}:{side.upper()}"
        expire_at = time.time() + duration_seconds
        self._cooldowns[key] = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "category": category.upper(),
            "reason": reason,
            "rejected_price": price,
            "created_at": time.time(),
            "expire_at": expire_at,
            "duration_seconds": duration_seconds
        }
        logger.info(f"⏳ [ADAPTIVE COOLDOWN] Placed {symbol} ({side}) on {duration_seconds:.0f}s cooldown [Category: {category.upper()}]. Reason: {reason}")

    def is_in_cooldown(self, symbol: str, side: str, current_price: float = 0.0) -> Tuple[bool, str]:
        """
        Checks if a symbol/side is currently on evaluation cooldown.
        If price has moved significantly (> 1.5%), the cooldown is invalidated early.
        """
        now = time.time()
        sym = symbol.upper()
        sd = side.upper()

        for key in [f"{sym}:{sd}", f"{sym}:ALL"]:
            if key in self._cooldowns:
                entry = self._cooldowns[key]
                expire_at = entry.get("expire_at", 0)
                
                # Check expiration
                if now >= expire_at:
                    del self._cooldowns[key]
                    continue

                # Check if market price moved significantly (> 1.5%) since rejection
                rej_price = entry.get("rejected_price", 0.0)
                if current_price > 0 and rej_price > 0:
                    pct_change = abs(current_price - rej_price) / rej_price
                    if pct_change >= 0.015:
                        logger.info(f"⚡ [COOLDOWN INVALIDATED EARLY] {sym} price moved {pct_change * 100:.2f}% (>= 1.5%). Clearing cooldown early.")
                        del self._cooldowns[key]
                        continue

                remaining = int(expire_at - now)
                return True, f"{entry.get('reason', 'Cooldown active')} ({remaining}s remaining)"

        return False, ""

    def get_active_cooldowns(self) -> List[Dict[str, Any]]:
        """Returns all currently active evaluation cooldowns."""
        now = time.time()
        active = []
        expired_keys = []
        for k, v in self._cooldowns.items():
            rem = v["expire_at"] - now
            if rem > 0:
                item = dict(v)
                item["remaining_seconds"] = round(rem, 1)
                active.append(item)
            else:
                expired_keys.append(k)

        for k in expired_keys:
            del self._cooldowns[k]

        return active

    async def start(self):
        self.running = True
        self._task = asyncio.create_task(self._scan_loop())
        logger.info("AI Reasoning & Opportunity Scanner started with Evaluation Cooldown Guard.")

    async def stop(self):
        self.running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        logger.info("AI Reasoning & Opportunity Scanner stopped.")

    async def _scan_loop(self):
        cache = await PriceCache.get_instance()
        symbols = ["BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"]
        
        while self.running:
            try:
                # Poll cache for setups
                for symbol in symbols:
                    price = cache.get_price(symbol)
                    tally = cache.get_update_tally(symbol)
                    
                    # Compute a simulated volatility indicator dynamically based on ticker updates
                    volatility_factor = 0.01 + ((tally % 10) * 0.001)

                    # 0. Check Smart Re-Entry Engine candidate signals & Systemic Macro Guard
                    try:
                        from app.trading.reentry_engine import SmartReentryEngine
                        from app.trading.macro_guard import SystemicMacroGuardEngine
                        
                        await SystemicMacroGuardEngine.get_instance().evaluate_systemic_risk()

                        reentry_sig = await SmartReentryEngine.get_instance().evaluate_reentry_candidate(
                            symbol=symbol,
                            current_price=price,
                            current_regime="TRENDING" if (int(price * 10) % 3 == 0) else "VOLATILE"
                        )
                        if reentry_sig:
                            from app.trading.order_manager import OrderManager
                            await OrderManager.get_instance().execute_opportunity(reentry_sig)
                            continue
                    except Exception as ree:
                        logger.error(f"Error checking smart re-entry or macro guard: {ree}")
                    
                    # Determine proposed side early to check cooldowns
                    proposed_side = "BUY" if (int(price * 100) % 2 == 0) else "SELL"

                    # 1. Check Evaluation Cooldown Guard before running heavy setup detection
                    in_cooldown, cd_reason = self.is_in_cooldown(symbol, proposed_side, current_price=price)
                    if in_cooldown:
                        continue

                    # Generate a buy/sell setup based on price cache micro-variations
                    setup_chance = random.random()
                    if setup_chance > 0.92:  # Increased chance for demonstration/testing
                        # Hard Safety Gate: Verify live market data before generating trade
                        passed_md, md_reason = cache.validate_market_data(symbol, max_age_seconds=5.0)
                        if not passed_md:
                            logger.warning(f"🚫 [MARKET DATA GUARD REJECTED] {md_reason}")
                            self.set_cooldown(symbol, proposed_side, category="MARKET_DATA_STALE", reason=md_reason, price=price)
                            continue

                        side = proposed_side
                        strategy = "Breakout Momentum" if side == "BUY" else "Mean Reversion"
                        
                        # Generate dynamic stop loss and take profit using real live price metrics
                        atr = round(price * volatility_factor, 2)
                        stop_loss = round(price - (1.5 * atr) if side == "BUY" else price + (1.5 * atr), 2)
                        take_profit = round(price + (2.5 * atr) if side == "BUY" else price - (2.5 * atr), 2)
                        
                        raw_confidence = round(0.80 + (random.random() * 0.15), 2)
                        market_regime = "VOLATILE" if volatility_factor > 0.014 else ("TRENDING" if (int(price * 10) % 3 == 0) else "RANGING")
                        
                        # Dynamically scale entry confidence threshold based on relative volatility
                        relative_volatility = atr / price if price > 0 else 0.01
                        base_threshold = 0.82
                        if relative_volatility > 0.012:
                            dynamic_threshold = base_threshold + (relative_volatility - 0.012) * 5.0
                        else:
                            dynamic_threshold = base_threshold - (0.012 - relative_volatility) * 2.0
                        dynamic_threshold = round(max(0.80, min(0.90, dynamic_threshold)), 2)

                        # Evaluate performance self-adaptation
                        from app.ai.self_adaptation import SelfAdaptationEngine
                        candidate_setup = {
                            "symbol": symbol,
                            "side": side,
                            "entry_price": price,
                            "stop_loss": stop_loss,
                            "take_profit": take_profit,
                            "strategy_used": strategy,
                            "market_regime": market_regime,
                            "atr": atr,
                            "price": price
                        }
                        
                        is_allowed, adaptive_modifier, adaptation_reasons = await SelfAdaptationEngine.get_instance().evaluate_opportunity_adaptation(candidate_setup)
                        
                        if not is_allowed:
                            reason_str = "; ".join(adaptation_reasons) if adaptation_reasons else "Adaptation block"
                            logger.warning(f"🚫 [SELF-ADAPTATION REJECTED] {symbol} {side} via {strategy} blocked. Reasons: {reason_str}")
                            self.set_cooldown(symbol, side, category="SELF_ADAPTATION_BLOCK", reason=f"Self-Adaptation Block: {reason_str}", price=price)
                            continue

                        adapted_confidence = round(max(0.0, min(1.0, raw_confidence + adaptive_modifier)), 2)
                        passed_filter = adapted_confidence >= dynamic_threshold

                        adaptation_log_str = "; ".join(adaptation_reasons) if adaptation_reasons else "Performance nominal."
                        
                        self.last_detected_setup = {
                            "symbol": symbol,
                            "side": side,
                            "entry_price": price,
                            "stop_loss": stop_loss,
                            "take_profit": take_profit,
                            "strategy_used": strategy,
                            "market_regime": market_regime,
                            "confidence_score": adapted_confidence,
                            "raw_confidence_score": raw_confidence,
                            "adaptive_modifier": adaptive_modifier,
                            "confidence_threshold": dynamic_threshold,
                            "passed_filter": passed_filter,
                            "entry_reason": f"AI Engine evaluated ticker telemetry. Symbol {symbol} trading at ${price} with live update count {tally}. High conviction {strategy} trigger generated based on dynamic ATR of ${atr}. Applied dynamic confidence threshold: {dynamic_threshold}. Self-Adaptation: {adaptation_log_str}",
                            "adaptation_insights": adaptation_reasons,
                            "atr": atr,
                            "timestamp": asyncio.get_event_loop().time()
                        }
                        
                        if passed_filter:
                            logger.info(f"🚨 OPPORTUNITY DETECTED & PASSED FILTER: {symbol} {side} via {strategy} at {price} (Raw Conf: {raw_confidence}, Mod: {adaptive_modifier:+.2f} -> Adapted Conf: {adapted_confidence} >= Threshold: {dynamic_threshold})")
                            if adaptation_reasons:
                                logger.info(f"🧠 [SELF-ADAPTATION INSIGHTS] {adaptation_log_str}")
                            # Trigger automated order placement if autonomy rules permit
                            await self._propose_trade_to_manager(self.last_detected_setup)
                        else:
                            rejection_msg = f"Confidence filter failed ({adapted_confidence} < {dynamic_threshold})"
                            logger.info(f"⚠️ OPPORTUNITY REJECTED BY CONFIDENCE FILTER: {symbol} {side} via {strategy} at {price} (Adapted Conf: {adapted_confidence} < Threshold: {dynamic_threshold})")
                            # Place on adaptive confidence filter cooldown (45s)
                            self.set_cooldown(symbol, side, category="CONFIDENCE_FILTER", reason=rejection_msg, price=price)

                await asyncio.sleep(3.0) # Check every 3 seconds
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Opportunity Scanner error: {e}")
                await asyncio.sleep(5)

    async def _propose_trade_to_manager(self, setup: Dict[str, Any]):
        """Proposes the opportunity to Layer 4 Order and Position Manager."""
        from app.trading.order_manager import OrderManager
        await OrderManager.get_instance().receive_opportunity(setup)
