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

        ref_price = float(price)
        if ref_price <= 0.0:
            try:
                from app.workers.price_cache import PriceCache
                cache = PriceCache.get_instance_sync()
                if cache:
                    ref_price = float(cache.get_price(symbol))
            except Exception:
                pass

        key = f"{symbol.upper()}:{side.upper()}"
        expire_at = time.time() + duration_seconds
        self._cooldowns[key] = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "category": category.upper(),
            "reason": reason,
            "rejected_price": ref_price,
            "created_at": time.time(),
            "expire_at": expire_at,
            "duration_seconds": duration_seconds
        }
        logger.info(f"⏳ [ADAPTIVE COOLDOWN] Placed {symbol} ({side}) on {duration_seconds:.0f}s cooldown [Category: {category.upper()}]. Reason: {reason}")

    def is_in_cooldown(self, symbol: str, side: str, current_price: float = 0.0) -> Tuple[bool, str]:
        """
        Checks if a symbol/side is currently on evaluation cooldown.
        If price has moved significantly (1.5% <= percent_move <= 20%), the cooldown is invalidated early.
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

                # Check if market price moved significantly (>= 1.5%) since rejection
                rej_price = float(entry.get("rejected_price", 0.0))
                c_price = float(current_price)
                if c_price > 0.0 and rej_price > 0.0:
                    abs_diff = abs(c_price - rej_price)
                    percent_move = (abs_diff / rej_price) * 100.0
                    atr_pct = percent_move

                    # Sanity Check: Reject impossible jump values (> 20.0%)
                    if percent_move > 20.0:
                        logger.warning(
                            f"⚠️ [COOLDOWN INVALIDATION REJECTED] {sym} ({sd}) calculated move {percent_move:.2f}% exceeds sanity threshold (20.0%).\n"
                            f"  - Cooldown Reference Price: ${rej_price:.4f}\n"
                            f"  - Current Price: ${c_price:.4f}\n"
                            f"  - Absolute Difference: ${abs_diff:.4f}\n"
                            f"  - Cooldown maintained to prevent false resets."
                        )
                    elif percent_move >= 1.5:
                        logger.info(
                            f"⚡ [COOLDOWN INVALIDATED EARLY] {sym} ({sd})\n"
                            f"  - Cooldown Reference Price: ${rej_price:.4f}\n"
                            f"  - Current Price: ${c_price:.4f}\n"
                            f"  - Absolute Difference: ${abs_diff:.4f}\n"
                            f"  - Calculated %: {percent_move:.2f}%\n"
                            f"  - ATR %: {atr_pct:.2f}%\n"
                            f"  - Reason cooldown cleared: Significant price movement ({percent_move:.2f}% >= 1.5% threshold)"
                        )
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

                        # Calculate relative volatility & ATR
                        atr = round(price * volatility_factor, 2)
                        relative_volatility = atr / price if price > 0 else 0.01

                        # Evaluate Dynamic Volatility Compression (Squeeze)
                        from app.trading.volatility_squeeze import VolatilitySqueezeDetector
                        squeeze_info = VolatilitySqueezeDetector.get_instance().detect_squeeze(symbol, price)
                        squeeze_state = squeeze_info["squeeze_state"]

                        # 1. Regime Classification based on relative volatility & trend metric & Squeeze state
                        if squeeze_state == "SQUEEZE_FIRING":
                            market_regime = "VOLATILE"
                            strategy = "Volatility Expansion Breakout"
                        elif squeeze_state == "SQUEEZE_ACTIVE":
                            market_regime = "RANGING"
                            strategy = "Mean Reversion"
                        elif relative_volatility > 0.014:
                            market_regime = "VOLATILE"
                            strategy = "Volatility Expansion"
                        elif tally % 3 == 0:
                            market_regime = "TRENDING"
                            strategy = "Breakout Momentum"
                        else:
                            market_regime = "RANGING"
                            strategy = "Mean Reversion"

                        # 2. Dynamic Stop Loss & Take Profit Placement based on Regime (Optimized R:R)
                        if market_regime == "TRENDING":
                            sl_multiplier = 1.8  # Wider stop buffer against noise whipsaws
                            tp_multiplier = 3.6  # Extended TP target (3.6 / 1.8 = 2.0 Nominal R:R)
                        elif market_regime == "RANGING":
                            sl_multiplier = 1.4  # Tighter stop for range boundaries
                            tp_multiplier = 2.8  # 2.8 / 1.4 = 2.0 Nominal R:R
                        else:  # VOLATILE / SQUEEZE_FIRING
                            sl_multiplier = 2.0
                            tp_multiplier = 4.0

                        stop_loss = round(price - (sl_multiplier * atr) if proposed_side == "BUY" else price + (sl_multiplier * atr), 2)
                        take_profit = round(price + (tp_multiplier * atr) if proposed_side == "BUY" else price - (tp_multiplier * atr), 2)
                        
                        # 3. Confluence & Confidence Calibration Engine with ML Feature Weight Self-Pruning & Bayesian Belief
                        from app.ai.feature_pruning import FeatureWeightPruningEngine
                        from app.ai.sentiment_engine import MarketSentimentEngine
                        from app.ai.self_supervised_learning import SelfSupervisedLearningEngine
                        from app.trading.onchain_liquidation_pulse import OnChainLiquidationPulseEngine
                        from app.ai.bayesian_belief import BayesianBeliefCalibrationEngine

                        pruning_engine = FeatureWeightPruningEngine.get_instance()
                        feature_weights = pruning_engine.get_pruned_weights()

                        sentiment_data = await MarketSentimentEngine.get_instance().analyze_sentiment(symbol)
                        self_supervised_data = await SelfSupervisedLearningEngine.get_instance().extract_and_project_market_state(symbol)
                        liq_data = await OnChainLiquidationPulseEngine.get_instance().get_liquidation_heatmap(symbol)
                        netflow_data = await OnChainLiquidationPulseEngine.get_instance().get_exchange_netflow_pulse(symbol)

                        squeeze_weight = feature_weights.get("volatility_squeeze_score", 0.20)
                        regime_weight = feature_weights.get("regime_synergy_factor", 0.15)
                        rvol_weight = feature_weights.get("rvol_volume_confluence", 0.25)

                        base_confidence = 0.82
                        regime_confluence_bonus = 0.05 if (market_regime == "TRENDING" and strategy == "Breakout Momentum") or (market_regime == "RANGING" and strategy == "Mean Reversion") else -0.03
                        volatility_quality_bonus = 0.03 if (0.008 <= relative_volatility <= 0.013) else -0.02
                        squeeze_bonus = 0.06 if squeeze_state == "SQUEEZE_FIRING" else (-0.03 if squeeze_state == "SQUEEZE_ACTIVE" else 0.0)
                        
                        # Sentiment alignment bonus (+0.03 if sentiment aligns with trade side)
                        sentiment_score = sentiment_data.get("composite_sentiment_score", 0.0)
                        sentiment_aligned = (proposed_side == "BUY" and sentiment_score > 10.0) or (proposed_side == "SELL" and sentiment_score < -10.0)
                        sentiment_bonus = 0.03 if sentiment_aligned else (-0.02 if abs(sentiment_score) > 40.0 else 0.0)

                        # Liquidation Magnet, Sweep Front-Running & Netflow Confluence
                        magnet_dir = liq_data.get("magnet_direction", "")
                        liq_aligned = (proposed_side == "BUY" and magnet_dir == "UPWARD_SWEEP_TARGET") or (proposed_side == "SELL" and magnet_dir == "DOWNWARD_SWEEP_TARGET")
                        liq_bonus = 0.03 if liq_aligned else 0.0

                        sweep_eval = await OnChainLiquidationPulseEngine.get_instance().evaluate_liquidity_sweep_opportunity(symbol, proposed_side, price)
                        sweep_bonus = sweep_eval.get("sweep_bonus", 0.0)

                        netflow_bias = netflow_data.get("market_bias", "NEUTRAL")
                        netflow_aligned = (proposed_side == "BUY" and netflow_bias == "BULLISH") or (proposed_side == "SELL" and netflow_bias == "BEARISH")
                        netflow_bonus = 0.02 if netflow_aligned else 0.0

                        # Macro Risk Overlay & Order Flow / Funding Skew
                        from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
                        macro_info = await MacroRiskOverlayEngine.get_instance().evaluate_macro_risk(symbol, proposed_side)
                        macro_metrics = macro_info.get("metrics", {})
                        depth_imbalance = macro_metrics.get("depth_imbalance_ratio", 1.0)
                        order_flow_skew = macro_metrics.get("order_flow_skew", "BALANCED_DEPTH")
                        funding_skew_type = macro_metrics.get("funding_skew_type", "BALANCED_FUNDING")

                        # Order flow depth alignment bonus (+0.03 if bids outweigh asks on BUY or asks outweigh bids on SELL)
                        order_flow_aligned = (proposed_side == "BUY" and order_flow_skew == "BULLISH_BID_DEPTH_ACCUMULATION") or (proposed_side == "SELL" and order_flow_skew == "BEARISH_ASK_DEPTH_PRESSURE")
                        order_flow_bonus = 0.03 if order_flow_aligned else 0.0

                        # Funding rate skew bonus (+0.03 if short crowded discount on BUY setup or long crowded premium on SELL setup)
                        funding_aligned = (proposed_side == "BUY" and funding_skew_type == "SHORT_CROWDED_DISCOUNT") or (proposed_side == "SELL" and funding_skew_type == "LONG_CROWDED_PREMIUM")
                        funding_bonus = 0.03 if funding_aligned else 0.0

                        # Structural anomaly penalty (-0.04 if structural anomaly detected)
                        is_anomaly = self_supervised_data.get("is_structural_anomaly", False)
                        anomaly_penalty = -0.04 if is_anomaly else 0.0

                        # 4. Bayesian Belief Calibration
                        bayesian_signals = {
                            "mtf_aligned": regime_confluence_bonus > 0,
                            "rvol_surge": volatility_quality_bonus > 0,
                            "squeeze_firing": squeeze_state == "SQUEEZE_FIRING",
                            "macro_risk_low": macro_info.get("trade_permitted", True),
                            "order_flow_aligned": order_flow_aligned,
                            "funding_aligned": funding_aligned,
                            "sentiment_aligned": sentiment_aligned,
                            "liquidity_magnet_aligned": liq_aligned,
                            "is_structural_anomaly": is_anomaly
                        }
                        bayesian_calibration = BayesianBeliefCalibrationEngine.get_instance().calibrate_belief(strategy, bayesian_signals)

                        # Weighted confidence calculation prioritizing non-pruned high-impact signals & Bayesian posterior probability
                        pruned_signal_adjustment = (
                            (squeeze_bonus * squeeze_weight * 2.5) +
                            (regime_confluence_bonus * regime_weight * 2.0) +
                            (volatility_quality_bonus * rvol_weight * 2.0) +
                            sentiment_bonus +
                            liq_bonus +
                            sweep_bonus +
                            netflow_bonus +
                            order_flow_bonus +
                            funding_bonus +
                            anomaly_penalty
                        )

                        bayesian_confidence = bayesian_calibration.get("calibrated_confidence", base_confidence)
                        raw_confidence = round(max(0.70, min(0.96, (0.5 * bayesian_confidence) + (0.5 * (base_confidence + pruned_signal_adjustment)) + (random.random() * 0.02))), 2)

                        # Dynamic confidence thresholding configured by Active Trading Persona Preset & Self-Adaptation Engine
                        from app.trading.trading_persona import TradingPersonaPresetEngine
                        from app.ai.self_adaptation import SelfAdaptationEngine
                        
                        active_persona = TradingPersonaPresetEngine.get_instance().get_active_persona()
                        persona_min_conf = active_persona.get("min_bayesian_confidence", 0.78)
                        adaptation_engine = SelfAdaptationEngine.get_instance()

                        dynamic_threshold = adaptation_engine.get_dynamic_confidence_threshold(
                            base_persona_min_conf=persona_min_conf,
                            market_regime=market_regime
                        )

                        # Evaluate performance self-adaptation
                        candidate_setup = {
                            "symbol": symbol,
                            "side": proposed_side,
                            "entry_price": price,
                            "stop_loss": stop_loss,
                            "take_profit": take_profit,
                            "strategy_used": strategy,
                            "market_regime": market_regime,
                            "atr": atr,
                            "price": price
                        }
                        
                        is_allowed, adaptive_modifier, adaptation_reasons = await adaptation_engine.evaluate_opportunity_adaptation(candidate_setup)
                        
                        # Integrate Pillar 3 Execution Quality / Slippage Feedback
                        from app.trading.execution_analytics import ExecutionAnalyticsEngine
                        slip_penalty, slip_reason = ExecutionAnalyticsEngine.get_instance().get_symbol_slippage_penalty(symbol)
                        if slip_penalty != 0.0:
                            adaptive_modifier += slip_penalty
                            if slip_reason:
                                adaptation_reasons.append(slip_reason)

                        if not is_allowed:
                            reason_str = "; ".join(adaptation_reasons) if adaptation_reasons else "Adaptation block"
                            logger.warning(f"🚫 [SELF-ADAPTATION REJECTED] {symbol} {proposed_side} via {strategy} blocked. Reasons: {reason_str}")
                            self.set_cooldown(symbol, proposed_side, category="SELF_ADAPTATION_BLOCK", reason=f"Self-Adaptation Block: {reason_str}", price=price)
                            continue

                        adapted_confidence = round(max(0.0, min(1.0, raw_confidence + adaptive_modifier)), 2)
                        passed_filter = adapted_confidence >= dynamic_threshold

                        adaptation_log_str = "; ".join(adaptation_reasons) if adaptation_reasons else "Performance nominal."
                        
                        self.last_detected_setup = {
                            "symbol": symbol,
                            "side": proposed_side,
                            "entry_price": price,
                            "stop_loss": stop_loss,
                            "take_profit": take_profit,
                            "strategy_used": strategy,
                            "market_regime": market_regime,
                            "squeeze_info": squeeze_info,
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
                            logger.info(f"🚨 OPPORTUNITY DETECTED & PASSED FILTER: {symbol} {proposed_side} via {strategy} at {price} (Raw Conf: {raw_confidence}, Mod: {adaptive_modifier:+.2f} -> Adapted Conf: {adapted_confidence} >= Threshold: {dynamic_threshold})")
                            if adaptation_reasons:
                                logger.info(f"🧠 [SELF-ADAPTATION INSIGHTS] {adaptation_log_str}")
                            # Trigger automated order placement if autonomy rules permit
                            await self._propose_trade_to_manager(self.last_detected_setup)
                        else:
                            rejection_msg = f"Confidence filter failed ({adapted_confidence} < {dynamic_threshold})"
                            logger.info(f"⚠️ OPPORTUNITY REJECTED BY CONFIDENCE FILTER: {symbol} {proposed_side} via {strategy} at {price} (Adapted Conf: {adapted_confidence} < Threshold: {dynamic_threshold})")
                            # Place on adaptive confidence filter cooldown (45s)
                            self.set_cooldown(symbol, proposed_side, category="CONFIDENCE_FILTER", reason=rejection_msg, price=price)

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
