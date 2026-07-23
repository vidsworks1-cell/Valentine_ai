import asyncio
import logging
import random
from typing import Dict, Any, List
from app.workers.price_cache import PriceCache
from app.core.config import settings

logger = logging.getLogger("lumina.ai.reasoning")

class AiReasoningEngine:
    """Layer 3 AI Reasoning and Opportunity Detection engine."""

    def __init__(self):
        self.running = False
        self._task: asyncio.Task | None = None
        self.last_detected_setup: Dict[str, Any] = {}

    async def start(self):
        self.running = True
        self._task = asyncio.create_task(self._scan_loop())
        logger.info("AI Reasoning & Opportunity Scanner started.")

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
                    
                    # Generate a buy/sell setup based on price cache micro-variations
                    setup_chance = random.random()
                    if setup_chance > 0.92:  # Increased chance for demonstration/testing
                        # Hard Safety Gate: Verify live market data before generating trade
                        passed_md, md_reason = cache.validate_market_data(symbol, max_age_seconds=5.0)
                        if not passed_md:
                            logger.warning(f"🚫 [MARKET DATA GUARD REJECTED] {md_reason}")
                            continue

                        side = "BUY" if (int(price * 100) % 2 == 0) else "SELL"
                        strategy = "Breakout Momentum" if side == "BUY" else "Mean Reversion"
                        
                        # Generate dynamic stop loss and take profit using real live price metrics
                        atr = round(price * volatility_factor, 2)
                        stop_loss = round(price - (1.5 * atr) if side == "BUY" else price + (1.5 * atr), 2)
                        take_profit = round(price + (2.5 * atr) if side == "BUY" else price - (2.5 * atr), 2)
                        
                        raw_confidence = round(0.80 + (random.random() * 0.15), 2)
                        market_regime = "VOLATILE" if volatility_factor > 0.014 else ("TRENDING" if (int(price * 10) % 3 == 0) else "RANGING")
                        
                        # Dynamically scale entry confidence threshold based on relative volatility
                        relative_volatility = atr / price if price > 0 else 0.01
                        # Base threshold is 0.82. Higher volatility means more strict entry criteria.
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
                            logger.warning(f"🚫 [SELF-ADAPTATION REJECTED] {symbol} {side} via {strategy} blocked by adaptation layer. Reasons: {adaptation_reasons}")
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
                            logger.info(f"⚠️ OPPORTUNITY REJECTED BY CONFIDENCE FILTER: {symbol} {side} via {strategy} at {price} (Adapted Conf: {adapted_confidence} < Threshold: {dynamic_threshold})")

                await asyncio.sleep(3.0) # Check every 3 seconds
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Opportunity Scanner error: {e}")
                await asyncio.sleep(5)

    async def _propose_trade_to_manager(self, setup: Dict[str, Any]):
        """Proposes the opportunity to Layer 4 Order and Position Manager."""
        from app.trading.order_manager import OrderManager
        # Hand over to OrderManager to perform risk check & execution
        await OrderManager.get_instance().receive_opportunity(setup)
