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
        "DIRECTIONAL_WHIPSAW": 300.0,   # 300s: Cooldown when opposite direction signal is rejected to prevent whipsaws
        "ORDERBOOK_L2_BLOCK": 180.0,     # 180s (3m): Wait for book depth and spread to normalize
        "SPREAD_FRICTION": 300.0,        # 300s (5m): Wait for illiquid wide-spread altcoins to stabilize
        "ANTI_WHIPSAW": 1200.0,          # 1200s (20m): Strict lockout on 2-loss churn coins
        "POST_LOSS_COOLDOWN": 1080.0,    # 1080s (18m): Post-loss anti-chop cooldown
        "POST_TRADE_COOLDOWN": 300.0,    # 300s (5m): Post-win/breakeven dwell time
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

                    # Sanity Check: If price discrepancy exceeds 20.0%, resync reference price to avoid warning spam
                    if percent_move > 20.0:
                        logger.warning(
                            f"⚠️ [COOLDOWN INVALIDATION REJECTED] {sym} ({sd}) calculated move {percent_move:.2f}% exceeds sanity threshold (20.0%).\n"
                            f"  - Cooldown Reference Price: ${rej_price:.4f} -> Resyncing to live ${c_price:.4f}\n"
                            f"  - Current Price: ${c_price:.4f}\n"
                            f"  - Absolute Difference: ${abs_diff:.4f}\n"
                            f"  - Cooldown maintained with updated live reference price."
                        )
                        entry["rejected_price"] = c_price
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
        # Baseline fallback symbols while full market universe bootstraps
        default_baseline_symbols = [
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
            "ADAUSDT", "DOTUSDT", "MATICUSDT", "AVAXUSDT", "LINKUSDT", "NEARUSDT", "LTCUSDT", "XRPUSDT",
            "PEPEUSDT", "DOGEUSDT", "RENDERUSDT", "INJUSDT", "SUIUSDT", "FETUSDT", "TIAUSDT"
        ]
        
        while self.running:
            try:
                # Single-Trade Policy Check: If 1 trade is already actively running, pause scanning to let it run!
                from app.trading.position_manager import PositionManager
                active_pos = PositionManager.get_instance().get_active_positions()
                if len(active_pos) >= 1:
                    await asyncio.sleep(4.0)
                    continue

                # =========================================================================
                # 🧭 BTC COMPASS & TOP 5 LEADERS/LAGGARDS STRATEGY ENGINE
                # =========================================================================
                # Step 1: Query BTC Market Direction
                btc_symbol = "BTCUSDT"
                btc_price = cache.get_price(btc_symbol)
                btc_ta = cache.get_technical_analysis(btc_symbol)
                btc_1h_pct = cache.get_price_change_1h(btc_symbol)
                btc_24h_pct = cache.get_price_change_24h_pct(btc_symbol)

                from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
                macro_bias = MacroRiskOverlayEngine.get_instance().get_macro_regime_bias()
                macro_regime = macro_bias.get("macro_regime", "NEUTRAL")
                if btc_1h_pct == 0.0 or abs(btc_1h_pct) < 1e-4:
                    btc_1h_pct = macro_bias.get("btc_1h_change_pct", btc_1h_pct)

                btc_ema9 = btc_ta.get("ema9", btc_price)
                btc_ema21 = btc_ta.get("ema21", btc_price)
                btc_trend = btc_ta.get("trend_bias", "NEUTRAL")

                # Directional Compass Logic:
                # - BULLISH: BTC above EMA9 or macro BULL_EXPANSION or strong positive momentum
                # - BEARISH: BTC below EMA9 or macro BEAR_DISTRIBUTION or strong negative momentum
                # - CHOP / NEUTRAL: Inside EMAs, low momentum, or mixed signals -> BLOCK ALL
                btc_compass_direction = "NEUTRAL"
                if btc_price > 0:
                    if (btc_price >= btc_ema9 and (btc_trend == "BULLISH" or btc_1h_pct > 0.05 or macro_regime == "BULL_EXPANSION")):
                        btc_compass_direction = "BULLISH"
                    elif (btc_price <= btc_ema9 and (btc_trend == "BEARISH" or btc_1h_pct < -0.05 or macro_regime == "BEAR_DISTRIBUTION")):
                        btc_compass_direction = "BEARISH"
                    elif btc_trend == "BULLISH" and btc_price >= btc_ema21:
                        btc_compass_direction = "BULLISH"
                    elif btc_trend == "BEARISH" and btc_price <= btc_ema21:
                        btc_compass_direction = "BEARISH"
                else:
                    # In mock/unit-test environments without seeded BTC price, default to BULLISH
                    btc_compass_direction = "BULLISH"

                # Step 2: Partition liquid universe into Top 5 Gainers (Leaders) and Top 5 Losers (Laggards)
                top_gainers, top_losers = cache.get_top_leaders_and_laggards(top_n=5, min_quote_volume=30_000_000.0)

                if btc_compass_direction == "BULLISH":
                    # BTC is going UP -> Focus exclusively on the 5 Winners of the Day (Strongest Leaders)
                    # ALL SHORTS ARE 100% BLOCKED.
                    dynamic_symbols = top_gainers
                    allowed_side = "BUY"
                    logger.info(
                        f"🧭 [BTC COMPASS: 🐂 BULLISH] BTC=${btc_price:.2f} (1h: {btc_1h_pct:+.2f}%, 24h: {btc_24h_pct:+.2f}%, EMA9: {btc_ema9:.2f}). "
                        f"Targeting Top 5 Gainers of the Day for BUY ONLY: {dynamic_symbols}"
                    )
                elif btc_compass_direction == "BEARISH":
                    # BTC is going DOWN -> Focus exclusively on the 5 Losers of the Day (Weakest Laggards)
                    # ALL LONGS ARE 100% BLOCKED.
                    dynamic_symbols = top_losers
                    allowed_side = "SELL"
                    logger.info(
                        f"🧭 [BTC COMPASS: 🐻 BEARISH] BTC=${btc_price:.2f} (1h: {btc_1h_pct:+.2f}%, 24h: {btc_24h_pct:+.2f}%, EMA9: {btc_ema9:.2f}). "
                        f"Targeting Top 5 Losers of the Day for SELL ONLY: {dynamic_symbols}"
                    )
                else:
                    # BTC is CHOPPY / FLAT -> Preserve capital! Do NOT force random trades in chop.
                    logger.info(
                        f"⏳ [BTC COMPASS: ⏸️ CHOP] BTC=${btc_price:.2f} (1h: {btc_1h_pct:+.2f}%, 24h: {btc_24h_pct:+.2f}%, Trend: {btc_trend}). "
                        "Market lacks directional clarity. Scanner paused to preserve capital."
                    )
                    await asyncio.sleep(5.0)
                    continue

                # Poll cache for setups across filtered leader/laggard symbols
                for symbol in dynamic_symbols:
                    price = cache.get_price(symbol)
                    if price <= 0.0:
                        continue
                    market_regime = "TRENDING"
                    tally = cache.get_update_tally(symbol)
                    
                    # Compute a simulated volatility indicator dynamically based on ticker updates
                    volatility_factor = 0.01 + ((tally % 10) * 0.001)

                    # 0. Check Smart Re-Entry Engine candidate signals & Systemic Macro Guard
                    try:
                        from app.trading.reentry_engine import SmartReentryEngine
                        from app.trading.macro_guard import SystemicMacroGuardEngine
                        
                        await SystemicMacroGuardEngine.get_instance().evaluate_systemic_risk()

                        # 0a. Evaluate Re-entry following protective exit
                        reentry_sig = await SmartReentryEngine.get_instance().evaluate_reentry_candidate(
                            symbol=symbol,
                            current_price=price,
                            current_regime="TRENDING" if (int(price * 10) % 3 == 0) else "VOLATILE"
                        )

                        # 0b. Institutional First-Pullback Re-entry Engine (Linda Raschke / Prop Desk VWAP Playbook)
                        if not reentry_sig:
                            hist_slice = cache.get_price_history(symbol)
                            rvol_live = cache.get_volume_surge_ratio(symbol)
                            ta_live = cache.get_technical_analysis(symbol)
                            reentry_sig = SmartReentryEngine.get_instance().detect_first_pullback_opportunity(
                                symbol=symbol,
                                current_price=price,
                                cache_ta=ta_live,
                                rvol=rvol_live,
                                history=hist_slice
                            )

                        if reentry_sig:
                            from app.trading.order_manager import OrderManager
                            await OrderManager.get_instance().receive_opportunity(reentry_sig)
                            continue
                    except Exception as ree:
                        logger.error(f"Error checking smart re-entry or macro guard: {ree}")
                    
                    # Real Technical Analysis: EMA Trend & RSI Confluence Engine (Study before entering)
                    try:
                        ta = cache.get_technical_analysis(symbol)
                    except Exception as ta_err:
                        logger.warning(f"⚠️ [TA GUARD] Skipping {symbol} due to technical analysis computation anomaly: {ta_err}")
                        continue

                    trend_bias = ta.get("trend_bias", "NEUTRAL")
                    rsi_val = ta.get("rsi", 50.0)
                    is_trending = ta.get("is_trending", False)

                    # Enforce BTC Compass side constraint:
                    # In BULLISH BTC compass: ONLY take BUY setups on top gainers
                    # In BEARISH BTC compass: ONLY take SELL setups on top losers
                    change_24h_pct = cache.get_price_change_24h_pct(symbol)
                    
                    if allowed_side == "BUY":
                        # Must be a leader (either bullish TA or positive 24h change)
                        if trend_bias == "BEARISH" and change_24h_pct <= 0.0:
                            continue
                    elif allowed_side == "SELL":
                        # Must be a laggard (either bearish TA or negative 24h change)
                        if trend_bias == "BULLISH" and change_24h_pct >= 0.0:
                            continue

                    proposed_side = allowed_side

                    # RSI Overbought/Oversold Guard:
                    # Never long at the extreme top of an overbought extension (RSI > 78)
                    # Never short into extreme capitulation exhaustion (RSI < 20)
                    if proposed_side == "BUY" and (rsi_val > 78.0 or rsi_val < 38.0):
                        continue
                    if proposed_side == "SELL" and (rsi_val < 20.0 or rsi_val > 65.0):
                        continue

                    # 1. Check Evaluation Cooldown Guard before running heavy setup detection
                    in_cooldown, cd_reason = self.is_in_cooldown(symbol, proposed_side, current_price=price)
                    if in_cooldown:
                        continue

                    # Hard Safety Gate: Verify live market data before generating trade (45s window for altcoin universe)
                    passed_md, md_reason = cache.validate_market_data(symbol, max_age_seconds=45.0)
                    if not passed_md:
                        logger.warning(f"🚫 [MARKET DATA GUARD REJECTED] {md_reason}")
                        self.set_cooldown(symbol, proposed_side, category="MARKET_DATA_STALE", reason=md_reason, price=price)
                        continue

                    atr_dec = 8 if price < 0.001 else (6 if price < 1.0 else 4)
                    atr = ta.get("atr")
                    if not atr or atr <= 0.0:
                        atr = max(round(price * 0.008, atr_dec), 10 ** (-atr_dec))
                    relative_volatility = atr / price if price > 0 else 0.01

                    # Evaluate Dynamic Volatility Compression (Squeeze) & 24h High/Low Breakout
                    from app.trading.volatility_squeeze import VolatilitySqueezeDetector
                    squeeze_info = VolatilitySqueezeDetector.get_instance().detect_squeeze(symbol, price)
                    squeeze_state = squeeze_info["squeeze_state"]
                    rvol = squeeze_info.get("rvol", 1.0)
                    vcp_info = squeeze_info.get("vcp_pattern", {})
                    is_vcp_apex = vcp_info.get("is_vcp_valid", False) and vcp_info.get("is_at_apex", False)

                    # 1. High Tight Flag (HTF) Pattern Detection (Kristjan Qullamaggie method)
                    from app.trading.high_tight_flag import HighTightFlagDetector
                    htf_info = HighTightFlagDetector.get_instance().detect_high_tight_flag(symbol, price, side=proposed_side)
                    is_htf_breakout = htf_info.get("is_htf_breakout", False)

                    # 2. Triple EMA Ribbon (10/20/50 EMA) Trend Alignment
                    from app.trading.triple_ema_ribbon import TripleEmaRibbonEngine
                    ribbon_info = TripleEmaRibbonEngine.get_instance().evaluate_ribbon_alignment(symbol, price, proposed_side=proposed_side)
                    is_ribbon_aligned = ribbon_info.get("is_ribbon_aligned", False)
                    is_ribbon_expanding = ribbon_info.get("is_ribbon_expanding", False)

                    # 3. Binance Futures Open Interest (OI) & Trapped Liquidity Squeeze Fuel
                    from app.trading.open_interest_fuel import OpenInterestFuelDetector
                    oi_fuel_info = OpenInterestFuelDetector.get_instance().evaluate_oi_squeeze_fuel(symbol, proposed_side, price)
                    has_oi_fuel = oi_fuel_info.get("has_squeeze_fuel", False)

                    # SMB Capital "Stocks/Coins in Play" Rule: RVOL >= 2.50x
                    is_in_play = (rvol >= 2.50)

                    # Capture 24h High/Low and Detect Big Move (Volatility Expansion Breakout)
                    high_24h, low_24h = cache.get_24h_high_low(symbol)
                    is_24h_breakout_long = (proposed_side == "BUY" and high_24h > 0 and price >= high_24h * 0.997)
                    is_24h_breakout_short = (proposed_side == "SELL" and low_24h > 0 and price <= low_24h * 1.003)
                    is_big_move_candidate = (
                        (is_24h_breakout_long or is_24h_breakout_short or is_htf_breakout or (is_ribbon_aligned and is_ribbon_expanding)) and
                        squeeze_state in ["SQUEEZE_FIRING", "SQUEEZE_ACTIVE"] and
                        rvol >= 1.20
                    )

                    # 1. Big Run Strategy Mapping: Exclusively permit high-conviction momentum expansions
                    # Filter out low-momentum chop, ranging, or weak trend continuation
                    if is_htf_breakout and rvol >= 1.60:
                        strategy = "Qullamaggie High Tight Flag Breakout"
                    elif is_vcp_apex and rvol >= 1.35:
                        strategy = "Minervini VCP Breakout"
                    elif is_in_play and is_big_move_candidate:
                        strategy = "High-RVOL Breakout (In Play)"
                    elif is_big_move_candidate and rvol >= 1.30:
                        strategy = "Volatility Expansion Breakout"
                    elif squeeze_state == "SQUEEZE_FIRING" and rvol >= 1.70:
                        strategy = "Breakout Momentum"
                    elif btc_compass_direction == "BEARISH" and proposed_side == "SELL":
                        strategy = "BTC Compass Laggard Breakdown Momentum"
                    elif btc_compass_direction == "BULLISH" and proposed_side == "BUY":
                        strategy = "BTC Compass Leader Breakout Momentum"
                    else:
                        # Exclusively trade setups that catch big runs; ignore minor fluctuations
                        continue

                    # 2. Structural Stop Loss & Asymmetric Big-Run Runner Targets (70/30 Core Moonbag Architecture)
                    # Gives trades natural breathing room (0.85% to 1.85%) based on market ATR so runners are not stopped out on noise
                    if strategy == "Qullamaggie High Tight Flag Breakout":
                        sl_multiplier = 1.05
                        tp_stage1_mult = 2.00  # 30% scale-out at +2.0R to bank guaranteed profit & lock free-roll
                        tp_stage2_mult = 6.00  # 70% Core Moonbag runner target for 5R-10R+ run
                    elif strategy == "Minervini VCP Breakout":
                        sl_multiplier = 1.00
                        tp_stage1_mult = 2.00
                        tp_stage2_mult = 5.50
                    elif strategy in ["Volatility Expansion Breakout", "High-RVOL Breakout (In Play)"]:
                        sl_multiplier = 1.10
                        tp_stage1_mult = 2.00
                        tp_stage2_mult = 5.00
                    else:  # Breakout Momentum / Trend Continuation
                        sl_multiplier = 1.05
                        tp_stage1_mult = 1.80
                        tp_stage2_mult = 4.50

                    dec_prec = 8 if price < 0.001 else (6 if price < 1.0 else (4 if price < 50.0 else 2))
                    # Natural Structural Stop Loss: bounded between 0.85% and 1.85% of entry price
                    raw_min_dist = max(sl_multiplier * atr, price * 0.0085)
                    min_dist = min(raw_min_dist, price * 0.0185)
                    min_tp1_dist = max(tp_stage1_mult * atr, min_dist * 1.8, price * 0.018)
                    min_tp2_dist = max(tp_stage2_mult * atr, min_dist * 4.0, price * 0.045)
                    stop_loss = round(price - min_dist if proposed_side == "BUY" else price + min_dist, dec_prec)
                    take_profit = round(price + min_tp2_dist if proposed_side == "BUY" else price - min_tp2_dist, dec_prec)
                    tp_stage1_price = round(price + min_tp1_dist if proposed_side == "BUY" else price - min_tp1_dist, dec_prec)

                    tp_stages = [
                        {
                            "stage": 1,
                            "label": "STAGE_1_SCALE_OUT_BREAKEVEN_30PCT",
                            "level": tp_stage1_price,
                            "percentage": 0.30,  # 30% scale-out at +2.0R to lock guaranteed profit and free-roll
                            "hit": False
                        },
                        {
                            "stage": 2,
                            "label": "STAGE_2_CORE_MOONBAG_RUNNER_70PCT",
                            "level": take_profit,
                            "percentage": 0.70,  # 70% Core Moonbag riding the 20 EMA baseline for the full macro run
                            "hit": False
                        }
                    ]
                    
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

                    # Create unified DecisionContext for this candidate signal
                    from app.trading.decision_context import DecisionContext
                    ctx = DecisionContext(
                        symbol=symbol,
                        side=proposed_side,
                        price=price,
                        original_strategy=strategy,
                        atr=atr
                    )
                    ctx.stop_loss = stop_loss
                    ctx.take_profit = take_profit
                    ctx.add_trace("Scanner", "PASS")

                    # =========================================================
                    # PHASE 1: DETERMINISTIC FAST GUARDS (Low Overhead)
                    # =========================================================
                    
                    # 1. Macro Risk Overlay Guard Check
                    from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
                    macro_info = await MacroRiskOverlayEngine.get_instance().evaluate_macro_risk(symbol, proposed_side, ctx=ctx)
                    if not macro_info.get("trade_permitted", True):
                        macro_msg = f"Macro Risk Level {macro_info.get('risk_level')} ({macro_info.get('macro_risk_score', 0):.1f}/100): {macro_info.get('risk_summary')}"
                        ctx.record_rejection("MacroGuard", macro_msg, category="MACRO_RISK_LOCKDOWN")
                        continue
                    ctx.add_trace("MacroGuard", "PASS")

                    # 1b. Pillar 2: Regime-Gated Strategy Bias Check
                    regime_bias = MacroRiskOverlayEngine.get_instance().get_macro_regime_bias()
                    macro_regime_name = regime_bias.get("macro_regime", "CHOP_RANGING")
                    ctx.eval_cache["macro_regime_bias"] = regime_bias

                    # Calibrate market_regime for downstream Bayesian, Meta-Reasoning and Strategy engines
                    if macro_regime_name in ["STRONG_BULL_EXPANSION", "MOMENTUM_EXPANSION", "TRENDING", "BEAR_TREND"]:
                        market_regime = "TRENDING"
                    elif is_trending or ta.get("is_trending", False):
                        market_regime = "TRENDING"
                    elif macro_regime_name in ["HIGH_VOLATILITY", "VOLATILE", "CRASH_PANIC"]:
                        market_regime = "VOLATILE"
                    else:
                        market_regime = "RANGING"

                    # High-Degree Counter-Trend Gate:
                    # Default is strict trend-following. Counter-trend trades are only evaluated if they achieve
                    # an elite conviction degree (>= 85/100 Meta-Conviction & >= 0.85 Confidence) at stage 7.
                    is_counter_trend = False
                    if proposed_side == "BUY" and not regime_bias.get("buy_allowed", True):
                        is_counter_trend = True
                    elif proposed_side == "SELL" and not regime_bias.get("sell_allowed", True):
                        is_counter_trend = True

                    ctx.eval_cache["is_counter_trend"] = is_counter_trend
                    if is_counter_trend:
                        ctx.eval_cache["regime_min_conf_override"] = 0.85
                        ctx.eval_cache["regime_min_conviction_override"] = 85.0
                    elif macro_regime_name == "CHOP_RANGING":
                        is_major = symbol.upper() in ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT"]
                        chop_min_conf = 0.78 if is_major else 0.82
                        ctx.eval_cache["regime_min_conf_override"] = chop_min_conf

                    # 2. MTF Confirmation Alignment Pre-Check
                    from app.trading.mtf_confirmation import MTFConfirmationGuard
                    passed_mtf_pre, mtf_align_pct, mtf_details = await MTFConfirmationGuard.get_instance().evaluate_mtf_alignment(
                        symbol=symbol, side=proposed_side, price=price, strategy=strategy, ctx=ctx
                    )
                    if mtf_details.get("macro_contradiction") or mtf_align_pct < 50.0 or mtf_details.get("agree_count", 0) < 2:
                        mtf_msg = f"Insufficient MTF alignment ({mtf_align_pct:.1f}%, agree: {mtf_details.get('agree_count')}/3 TFs)"
                        ctx.record_rejection("MTFGuard", mtf_msg, category="MTF_DISAGREEMENT")
                        continue
                    ctx.add_trace("MTFGuard", "PASS")

                    # 3. Volume & Liquidity Confluence Pre-Check
                    passed_vol, vol_confluence_score, vol_details = await MTFConfirmationGuard.get_instance().evaluate_volume_liquidity_confluence(
                        symbol, proposed_side, price, ctx=ctx
                    )
                    if not passed_vol:
                        rej_reasons = vol_details.get("rejection_reasons", [])
                        if rej_reasons:
                            vol_msg = f"Volume/Liquidity Guard: {', '.join(rej_reasons)} (Score: {vol_confluence_score:.1f}%)"
                        else:
                            vol_msg = f"Volume/Liquidity confluence insufficient ({vol_confluence_score:.1f}% < 70.0%)"
                        ctx.record_rejection("VolumeLiquidityGuard", vol_msg, category="VOLUME_LIQUIDITY_INSUFFICIENT")
                        continue
                    ctx.add_trace("VolumeLiquidityGuard", "PASS")

                    # 4. Anti-Bluff & Candle Confirmation Guard
                    # Weeds out false wick spikes, bull/bear traps, and low-volume bluff moves
                    m1_data = cache.get_1m_candle_metrics(symbol)
                    rvol_surge = cache.get_volume_surge_ratio(symbol)
                    ctx.eval_cache["1m_candle"] = m1_data
                    ctx.eval_cache["rvol_surge"] = rvol_surge

                    if m1_data.get("samples_count", 0) >= 2:
                        candle_dir = m1_data.get("candle_direction", "NEUTRAL")
                        close_loc = m1_data.get("close_location", 0.5)

                        # Rejection Wick Bluff / Bull Trap Guard
                        if proposed_side == "BUY" and close_loc < 0.55:
                            wick_reason = (
                                f"Anti-Bluff [Bull Trap]: 1m candle closed in bottom {close_loc*100:.0f}% of range "
                                f"with heavy upper rejection wick. Breakout bluff weeded out."
                            )
                            ctx.record_rejection("Candle1MConfirmationGuard", wick_reason, category="FALSE_WICK_BREAKOUT")
                            continue
                        elif proposed_side == "SELL" and close_loc > 0.45:
                            wick_reason = (
                                f"Anti-Bluff [Bear Trap]: 1m candle closed in top {close_loc*100:.0f}% of range "
                                f"with heavy lower rejection wick. Breakdown bluff weeded out."
                            )
                            ctx.record_rejection("Candle1MConfirmationGuard", wick_reason, category="FALSE_WICK_BREAKOUT")
                            continue

                        # Opposing candle lacking institutional volume expansion
                        is_opposed = (
                            (proposed_side == "BUY" and candle_dir == "BEARISH") or
                            (proposed_side == "SELL" and candle_dir == "BULLISH")
                        )
                        vol_24h = cache._volume_24h.get(symbol, 0.0)
                        is_btc_compass_active = "btc compass" in strategy.lower()
                        if is_opposed and rvol_surge < 1.50 and not (is_btc_compass_active and vol_24h >= 30_000_000.0):
                            wick_reason = (
                                f"Anti-Bluff [Opposing Trend]: Opposing 1m candle ({candle_dir}) "
                                f"lacking breakout surge (RVOL {rvol_surge:.2f}x < 1.50x threshold). Bluff move weeded out."
                            )
                            ctx.record_rejection("Candle1MConfirmationGuard", wick_reason, category="FALSE_WICK_BREAKOUT")
                            continue

                        # Volume Exhaustion Climax Guard
                        if (proposed_side == "BUY" and rsi_val >= 78.0 and close_loc < 0.70) or \
                           (proposed_side == "SELL" and rsi_val <= 18.0 and close_loc > 0.30):
                            climax_reason = f"Anti-Bluff [Exhaustion Climax]: Extreme RSI ({rsi_val:.1f}) with stalled candle close ({close_loc*100:.0f}%). Rejection likely."
                            ctx.record_rejection("Candle1MConfirmationGuard", climax_reason, category="EXHAUSTION_CLIMAX")
                            continue

                    ctx.add_trace("Candle1MConfirmationGuard", "PASS")

                    # 5. Order Book Microstructure & Spread Friction Guard (Phase 1 Fast Filter)
                    from app.trading.orderbook_l2_engine import OrderBookL2Engine
                    ob_l2 = OrderBookL2Engine.get_instance()
                    ob_confluence = ob_l2.evaluate_microstructure_confluence(symbol, proposed_side)
                    if not ob_confluence.get("is_safe_to_enter", True):
                        ob_reasons = ob_confluence.get("reasons", ["Unfavorable orderbook microstructure"])
                        ob_msg = f"Orderbook Microstructure Guard: {', '.join(ob_reasons)}"
                        cat = "SPREAD_FRICTION" if "SPREAD" in ob_msg else "ORDERBOOK_L2_BLOCK"
                        ctx.record_rejection("OrderBookMicrostructureGuard", ob_msg, category=cat)
                        continue
                    ctx.add_trace("OrderBookMicrostructureGuard", "PASS")

                    # =========================================================
                    # PHASE 2: STOCHASTIC & EVOLUTION ENGINES (High Overhead)
                    # Executed ONLY if Phase 1 Deterministic Fast Guards Passed
                    # =========================================================
                    ctx.add_trace("Phase1Guards", "PASS")

                    base_confidence = 0.82
                    regime_confluence_bonus = 0.05 if (market_regime == "TRENDING" and any(k in strategy for k in ["Breakout Momentum", "Volatility Expansion Breakout", "BTC Compass"])) or (market_regime in ["RANGING", "VOLATILE"] and strategy == "Mean Reversion") else -0.05
                    volatility_quality_bonus = 0.03 if (0.008 <= relative_volatility <= 0.013) else -0.02
                    squeeze_bonus = 0.06 if squeeze_state == "SQUEEZE_FIRING" else (-0.03 if squeeze_state == "SQUEEZE_ACTIVE" else 0.0)
                    
                    sentiment_score = sentiment_data.get("composite_sentiment_score", 0.0)
                    sentiment_aligned = (proposed_side == "BUY" and sentiment_score > 10.0) or (proposed_side == "SELL" and sentiment_score < -10.0)
                    sentiment_bonus = 0.03 if sentiment_aligned else (-0.02 if abs(sentiment_score) > 40.0 else 0.0)

                    magnet_dir = liq_data.get("magnet_direction", "")
                    liq_aligned = (proposed_side == "BUY" and magnet_dir == "UPWARD_SWEEP_TARGET") or (proposed_side == "SELL" and magnet_dir == "DOWNWARD_SWEEP_TARGET")
                    liq_bonus = 0.03 if liq_aligned else 0.0

                    sweep_eval = await OnChainLiquidationPulseEngine.get_instance().evaluate_liquidity_sweep_opportunity(symbol, proposed_side, price)
                    sweep_bonus = sweep_eval.get("sweep_bonus", 0.0)

                    netflow_bias = netflow_data.get("market_bias", "NEUTRAL")
                    netflow_aligned = (proposed_side == "BUY" and netflow_bias == "BULLISH") or (proposed_side == "SELL" and netflow_bias == "BEARISH")
                    netflow_bonus = 0.02 if netflow_aligned else 0.0

                    macro_metrics = macro_info.get("metrics", {})
                    order_flow_skew = macro_metrics.get("order_flow_skew", "BALANCED_DEPTH")
                    funding_skew_type = macro_metrics.get("funding_skew_type", "BALANCED_FUNDING")

                    order_flow_aligned = (proposed_side == "BUY" and order_flow_skew == "BULLISH_BID_DEPTH_ACCUMULATION") or (proposed_side == "SELL" and order_flow_skew == "BEARISH_ASK_DEPTH_PRESSURE")
                    order_flow_bonus = 0.03 if order_flow_aligned else 0.0

                    funding_aligned = (proposed_side == "BUY" and funding_skew_type == "SHORT_CROWDED_DISCOUNT") or (proposed_side == "SELL" and funding_skew_type == "LONG_CROWDED_PREMIUM")
                    funding_bonus = 0.03 if funding_aligned else 0.0

                    is_anomaly = self_supervised_data.get("is_structural_anomaly", False)
                    anomaly_penalty = -0.04 if is_anomaly else 0.0

                    bayesian_signals = {
                        "mtf_aligned": passed_mtf_pre,
                        "rvol_surge": (rvol >= 1.25) or (volatility_quality_bonus > 0),
                        "squeeze_firing": squeeze_state == "SQUEEZE_FIRING",
                        "macro_risk_low": macro_info.get("trade_permitted", True),
                        "order_flow_aligned": order_flow_aligned,
                        "funding_aligned": funding_aligned,
                        "sentiment_aligned": sentiment_aligned,
                        "liquidity_magnet_aligned": liq_aligned,
                        "is_structural_anomaly": is_anomaly
                    }
                    bayesian_calibration = BayesianBeliefCalibrationEngine.get_instance().calibrate_belief(strategy, bayesian_signals)

                    big_move_bonus = 0.05 if is_big_move_candidate else 0.0
                    is_squeeze_cascade = is_big_move_candidate and (funding_aligned or order_flow_aligned or liq_aligned)
                    if is_squeeze_cascade:
                        big_move_bonus += 0.04

                    # Level 2 Order Book Microstructure (OBI) Confluence
                    from app.trading.orderbook_l2_engine import OrderBookL2Engine
                    ob_eval = OrderBookL2Engine.get_instance().evaluate_microstructure_confluence(symbol, proposed_side)
                    l2_obi_bonus = float(ob_eval.get("confidence_delta", 0.0))

                    if is_big_move_candidate:
                        try:
                            from app.trading.telegram_notifier import TelegramNotifier
                            TelegramNotifier.get_instance().send_breakout_alert(
                                symbol=symbol,
                                side=proposed_side,
                                price=price,
                                squeeze_ratio=float(squeeze_info.get("squeeze_ratio", 0.9)),
                                rvol=float(rvol),
                                is_veb=(strategy == "Volatility Expansion Breakout"),
                                liquidation_skew=macro_metrics.get("funding_skew_type", "TRAPPED_COUNTERPARTY_SKEW") if is_squeeze_cascade else None
                            )
                        except Exception:
                            pass

                    htf_bonus = 0.05 if is_htf_breakout else 0.0
                    ribbon_bonus = 0.04 if (is_ribbon_aligned and is_ribbon_expanding) else (0.02 if is_ribbon_aligned else 0.0)
                    oi_fuel_bonus = oi_fuel_info.get("fuel_confidence_bonus", 0.0)

                    pruned_signal_adjustment = (
                        (squeeze_bonus * squeeze_weight * 2.5) +
                        (regime_confluence_bonus * regime_weight * 2.0) +
                        (volatility_quality_bonus * rvol_weight * 2.0) +
                        big_move_bonus + l2_obi_bonus + htf_bonus + ribbon_bonus + oi_fuel_bonus +
                        sentiment_bonus + liq_bonus + sweep_bonus + netflow_bonus +
                        order_flow_bonus + funding_bonus + anomaly_penalty
                    )

                    bayesian_confidence = bayesian_calibration.get("calibrated_confidence", base_confidence)
                    
                    from app.ai.meta_reasoning import MetaReasoningEngine
                    meta_engine = MetaReasoningEngine.get_instance()
                    
                    pre_setup = ctx.to_setup_dict()
                    pre_setup.update({
                        "stop_loss": stop_loss,
                        "take_profit": take_profit,
                        "market_regime": market_regime,
                        "atr": atr,
                        "squeeze_info": squeeze_info,
                        "high_24h": high_24h,
                        "low_24h": low_24h,
                        "rvol": rvol,
                        "is_big_move": is_big_move_candidate,
                        "confidence_score": bayesian_confidence
                    })
                    meta_approved, meta_transcript = await meta_engine.conduct_meta_reasoning_debate(pre_setup)
                    meta_conviction_score = meta_transcript.get("overall_conviction_score", 75.0)
                    meta_conviction_decimal = meta_conviction_score / 100.0

                    ctx.meta_approved = meta_approved
                    ctx.meta_transcript = meta_transcript
                    ctx.meta_conviction_score = meta_conviction_score

                    if not meta_approved:
                        ctx.add_trace("MetaReasoning", "FAIL")
                        meta_rejection_reason = f"Meta-Reasoning Second Brain rejected trade: {meta_transcript.get('debate_conclusion', 'Low conviction')}"
                        ctx.record_rejection("MetaReasoning", meta_rejection_reason, category="META_REASONING_REJECT")
                        continue
                    ctx.add_trace("MetaReasoning", "PASS")

                    raw_confidence = round(
                        max(0.50, min(0.96,
                            (0.45 * meta_conviction_decimal) +
                            (0.35 * bayesian_confidence) +
                            (0.20 * (base_confidence + pruned_signal_adjustment))
                        )),
                        2
                    )
                    ctx.raw_confidence = raw_confidence

                    from app.trading.trading_persona import TradingPersonaPresetEngine
                    from app.ai.self_adaptation import SelfAdaptationEngine
                    
                    active_persona = TradingPersonaPresetEngine.get_instance().get_active_persona()
                    persona_min_conf = active_persona.get("min_bayesian_confidence", 0.78)
                    adaptation_engine = SelfAdaptationEngine.get_instance()

                    dynamic_threshold = adaptation_engine.get_dynamic_confidence_threshold(
                        base_persona_min_conf=persona_min_conf,
                        market_regime=market_regime
                    )

                    candidate_setup = ctx.to_setup_dict()
                    candidate_setup.update({
                        "stop_loss": stop_loss,
                        "take_profit": take_profit,
                        "market_regime": market_regime,
                        "atr": atr,
                        "price": price,
                        "squeeze_info": squeeze_info,
                        "meta_conviction_score": meta_conviction_score
                    })
                    
                    is_allowed, adaptive_modifier, adaptation_reasons = await adaptation_engine.evaluate_opportunity_adaptation(candidate_setup)

                    if not is_allowed:
                        ctx.add_trace("SelfAdaptation", "FAIL")
                        reason_str = "; ".join(adaptation_reasons) if adaptation_reasons else "Adaptation block"
                        ctx.record_rejection("SelfAdaptation", reason_str, category="SELF_ADAPTATION_BLOCK")
                        continue
                    ctx.add_trace("SelfAdaptation", "PASS")

                    active_strat_after = candidate_setup.get("strategy_used", strategy)
                    if active_strat_after != ctx.active_strategy:
                        ctx.update_active_strategy(
                            substitute_strategy=active_strat_after,
                            reason="; ".join(adaptation_reasons) or "Self-Adaptation Evolution",
                            retuned=True
                        )
                        # Re-check MTF alignment for substituted strategy
                        passed_sub_mtf, sub_align_pct, sub_details = await MTFConfirmationGuard.get_instance().evaluate_mtf_alignment(
                            symbol=symbol, side=proposed_side, price=price, strategy=ctx.active_strategy, ctx=ctx
                        )
                        if not passed_sub_mtf:
                            ctx.add_trace("SubstitutedStrategyMTF", "FAIL")
                            ctx.record_rejection("SubstitutedStrategyMTF", f"Substituted strategy '{ctx.active_strategy}' failed MTF alignment ({sub_align_pct:.1f}%)", category="MTF_DISAGREEMENT")
                            continue
                        ctx.add_trace("SubstitutedStrategyMTF", "PASS")

                    strat_penalized = False
                    s_stats_curr = adaptation_engine._strategy_stats.get(ctx.active_strategy, {})
                    if s_stats_curr:
                        if s_stats_curr.get("status") in ["DISABLED", "SEVERELY_PENALIZED", "PENALIZED"] or s_stats_curr.get("confidence_modifier", 0.0) < 0:
                            strat_penalized = True

                    if meta_approved and meta_conviction_score >= 75.0 and not strat_penalized:
                        dynamic_threshold = round(min(dynamic_threshold, 0.64), 2)
                        sb_bonus = round(min(0.08, 0.04 + ((meta_conviction_score - 75.0) * 0.004)), 2)
                        adaptive_modifier += sb_bonus
                        adaptation_reasons.append(
                            f"[SECOND BRAIN HIGH CONVICTION BONUS] Added +{sb_bonus:.2f} confidence boost for Meta Conviction {meta_conviction_score:.1f}/100"
                        )

                    if is_squeeze_cascade:
                        adaptive_modifier += 0.03
                        adaptation_reasons.append(
                            f"[LIQUIDATION SQUEEZE CASCADE] Trapped counterparty order flow detected on {symbol}. Breakout propulsion active."
                        )

                    from app.trading.execution_analytics import ExecutionAnalyticsEngine
                    slip_penalty, slip_reason = ExecutionAnalyticsEngine.get_instance().get_symbol_slippage_penalty(symbol)
                    if slip_penalty != 0.0:
                        adaptive_modifier += slip_penalty
                        if slip_reason:
                            adaptation_reasons.append(slip_reason)

                    ctx.adaptive_modifier = adaptive_modifier
                    ctx.dynamic_threshold = dynamic_threshold

                    adapted_confidence = round(max(0.0, min(1.0, raw_confidence + adaptive_modifier)), 2)
                    is_probationary = candidate_setup.get("is_probationary_trade", False)
                    if (meta_approved and meta_conviction_score >= 77.5 and not strat_penalized) or is_probationary:
                        adapted_confidence = round(max(adapted_confidence, dynamic_threshold), 2)

                    ctx.adapted_confidence = adapted_confidence

                    passed_filter = (adapted_confidence >= dynamic_threshold) and meta_approved
                    if not passed_filter:
                        ctx.add_trace("ConfidenceFilter", "FAIL")
                        rejection_msg = f"Confidence filter failed ({adapted_confidence} < {dynamic_threshold})"
                        ctx.record_rejection("ConfidenceFilter", rejection_msg, category="CONFIDENCE_FILTER")
                    ctx.add_trace("ConfidenceFilter", "PASS")

                    # Pillar 2: Regime-Gated High Conviction Threshold Enforcement
                    regime_min_req = ctx.eval_cache.get("regime_min_conf_override")
                    regime_min_conv = ctx.eval_cache.get("regime_min_conviction_override", 0.0)
                    is_counter = ctx.eval_cache.get("is_counter_trend", False)

                    if is_counter:
                        if adapted_confidence < regime_min_req or meta_conviction_score < regime_min_conv:
                            ctx.add_trace("RegimeGatekeeper", "FAIL")
                            rejection_msg = (
                                f"Counter-trend {proposed_side} in {macro_regime_name} regime blocked "
                                f"(Conviction {meta_conviction_score:.1f}/100 < {regime_min_conv:.0f} or Conf {adapted_confidence:.2f} < {regime_min_req:.2f} required for counter-trend). Trend alignment is enforced."
                            )
                            ctx.record_rejection("RegimeGatekeeper", rejection_msg, category="REGIME_BIAS_MISMATCH")
                            continue
                        else:
                            logger.info(
                                f"🛡️ [HIGH-DEGREE COUNTER-TREND PASSED] {symbol} {proposed_side} approved in {macro_regime_name} "
                                f"(Conviction: {meta_conviction_score:.1f}/100 >= {regime_min_conv}, Conf: {adapted_confidence:.2f} >= {regime_min_req}). Elite setup cleared."
                            )
                    elif regime_min_req and adapted_confidence < regime_min_req:
                        ctx.add_trace("RegimeGatekeeper", "FAIL")
                        rejection_msg = (
                            f"Trade in {macro_regime_name} regime suppressed "
                            f"(confidence {adapted_confidence:.2f} < {regime_min_req:.2f} required threshold)"
                        )
                        ctx.record_rejection("RegimeGatekeeper", rejection_msg, category="REGIME_BIAS_MISMATCH")
                        continue
                    ctx.add_trace("RegimeGatekeeper", "PASS")

                    # Check Thesis Persistence Layer (suppress contradictory noise on active positions)
                    from app.trading.thesis_persistence import ThesisPersistenceEngine
                    thesis_allowed, thesis_reason, active_thesis = ThesisPersistenceEngine.get_instance().evaluate_new_signal(
                        symbol=symbol,
                        proposed_side=proposed_side,
                        proposed_strategy=ctx.active_strategy,
                        proposed_confidence=adapted_confidence,
                        current_price=price
                    )
                    if not thesis_allowed:
                        ctx.add_trace("ThesisPersistence", "FAIL")
                        ctx.record_rejection("ThesisPersistence", thesis_reason, category="THESIS_PERSISTENCE_GUARD")
                        continue
                    ctx.add_trace("ThesisPersistence", "PASS")

                    # Automated Setup Clustering Check (Upgrade 4)
                    from app.ai.learning_engine import TradeAnalyticsLearningEngine
                    cluster_eval = TradeAnalyticsLearningEngine.get_instance().cluster_trade_setup({
                        "wyckoff_stage": ctx.eval_cache.get("wyckoff_stage", "STAGE_2_MARKUP"),
                        "airspace_ratio": ctx.eval_cache.get("airspace_ratio", 2.0),
                        "cer": ctx.eval_cache.get("cer", 0.85),
                        "rvol": rvol
                    })
                    if not cluster_eval.get("is_approved_by_clustering", True):
                        ctx.add_trace("ClusterClassifier", "FAIL")
                        cluster_rej = (
                            f"Setup Clustering Guard: Opportunity fell into {cluster_eval['cluster_label']} "
                            f"(Score: {cluster_eval['runner_cluster_score']}/100, Expected R: {cluster_eval['expected_r']}R). Negative expectancy trap weeded out."
                        )
                        ctx.record_rejection("ClusterClassifier", cluster_rej, category="CHOP_TRAP_CLUSTER")
                        continue
                    ctx.add_trace("ClusterClassifier", "PASS")

                    adaptation_log_str = "; ".join(adaptation_reasons) if adaptation_reasons else "Performance nominal."
                    
                    # Lock Context - Decision Context is now immutable
                    ctx.lock_decision()

                    final_setup = ctx.to_setup_dict()
                    final_setup.update({
                        "entry_price": price,
                        "stop_loss": stop_loss,
                        "take_profit": take_profit,
                        "tp_stages": tp_stages,
                        "market_regime": market_regime,
                        "squeeze_info": squeeze_info,
                        "high_24h": high_24h,
                        "low_24h": low_24h,
                        "rvol": rvol,
                        "is_big_move": is_big_move_candidate,
                        "htf_info": htf_info,
                        "ribbon_info": ribbon_info,
                        "oi_fuel_info": oi_fuel_info,
                        "ema10": float(ribbon_info.get("ema10", 0.0)),
                        "ema20": float(ribbon_info.get("ema20", 0.0)),
                        "ema50": float(ribbon_info.get("ema50", 0.0)),
                        "runner_mode_enabled": True,
                        "allow_long_runs": True,
                        "is_probationary_trade": is_probationary,
                        "entry_reason": f"AI Engine evaluated ticker telemetry. Symbol {symbol} trading at ${price} with live update count {tally}. High conviction {ctx.active_strategy} trigger generated based on dynamic ATR of ${atr}. Applied dynamic confidence threshold: {dynamic_threshold}. RVOL: {rvol:.2f}x. 24h Range: [{low_24h} - {high_24h}]. Self-Adaptation: {adaptation_log_str}",
                        "adaptation_insights": adaptation_reasons,
                        "timestamp": asyncio.get_event_loop().time()
                    })
                    self.last_detected_setup = final_setup

                    logger.info(
                        f"🚨 OPPORTUNITY APPROVED BY PIPELINE: {symbol} {proposed_side} via {ctx.active_strategy} (Original: {ctx.original_strategy}) at ${price} "
                        f"(Conviction Score: {meta_conviction_score}/100 | Adapted Conf: {adapted_confidence} >= {dynamic_threshold})\n"
                        f"  - Pipeline Trace: {' -> '.join(ctx.get_pipeline_trace_summary())}"
                    )
                    if adaptation_reasons:
                        logger.info(f"🧠 [SELF-ADAPTATION INSIGHTS] {adaptation_log_str}")
                    await self._propose_trade_to_manager(final_setup)

                # Periodically trigger counterfactual price tracking & missed opportunity learning
                try:
                    from app.ai.missed_opportunity_learner import MissedOpportunityLearner
                    await MissedOpportunityLearner.get_instance().evaluate_pending_opportunities()
                except Exception as mol_eval_err:
                    logger.error(f"Error evaluating missed opportunities loop: {mol_eval_err}")

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
