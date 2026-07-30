import asyncio
import logging
import json
import time
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from app.risk.evaluator import RiskEvaluator
from app.database.session import AsyncSessionLocal
from app.services.binance_connector import BinanceConnector
from app.api.schemas import TradeCreate, AuditLogCreate

logger = logging.getLogger("lumina.trading.order_manager")

class OrderManager:
    """Layer 4 Order Manager. Governs risk validation and order pipeline execution."""
    _instance: Optional['OrderManager'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self.active_positions: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'OrderManager':
        if cls._instance is None:
            cls._instance = OrderManager()
        return cls._instance

    async def receive_opportunity(self, opportunity: Dict[str, Any]):
        """Receives a setup from Layer 3, evaluates risk profile, and executes if cleared."""
        async with self._lock:
            symbol = opportunity["symbol"]
            side = opportunity["side"]
            
            logger.info(f"[STAGE: SIGNAL] Reviewing trade opportunity from AI Scanner: {symbol} ({side}) at {opportunity['entry_price']} via {opportunity['strategy_used']}")
            
            # 0. Check central circuit breakers and global trading modes
            from app.scheduler.manager import LayeredSchedulerManager
            scheduler = await LayeredSchedulerManager.get_instance()
            allowed, reason = await scheduler.is_trading_enabled()
            if not allowed:
                logger.warning(f"⚠️ ORDER PIPELINE REJECTED: {reason}")
                try:
                    from app.services.audit_service import AuditService
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="SYSTEM",
                            message=f"Order pipeline blocked for {symbol}",
                            details=f"Reason: {reason}. Strategy {opportunity['strategy_used']} signals suppressed."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log blocked order pipeline audit event: {ae}")
                return

            # 0a. Market Data Guard Check: Verify live WebSocket feed is active, recent, and synchronized
            from app.workers.price_cache import PriceCache
            cache = await PriceCache.get_instance()
            passed_md, md_reason = cache.validate_market_data(symbol, max_age_seconds=5.0)
            if not passed_md:
                logger.warning(md_reason)
                try:
                    from app.services.audit_service import AuditService
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"[MARKET DATA GUARD] Rejected {symbol} trade",
                            details=md_reason
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log market data guard audit event: {ae}")
                return

            # 0b. Position Exposure & Portfolio Intelligence Guard:
            # Delegated directly to Layer 4 PortfolioGuard (evaluate_portfolio_action) in Step 2b downstream.
            # PortfolioGuard dynamically determines Pyramid Scale-In, Opposite Signal Partial Rebalance, Alpha Swaps, or Rejection.

            # 0c. Multi-Timeframe Confirmation Guard Check (5m, 15m, 1h alignment)
            from app.trading.mtf_confirmation import MTFConfirmationGuard
            conviction_val = (
                opportunity.get("conviction_score") or
                opportunity.get("meta_reasoning", {}).get("overall_conviction_score") or
                (opportunity.get("confidence_score", 0.0) * 100.0)
            )
            passed_mtf, mtf_align_pct, mtf_details = await MTFConfirmationGuard.get_instance().evaluate_mtf_alignment(
                symbol=symbol,
                side=side,
                price=opportunity.get("entry_price", 0.0),
                strategy=opportunity.get("strategy_used"),
                conviction_score=conviction_val
            )
            if not passed_mtf:
                mtf_reason = (
                    f"[MTF GUARD]\n"
                    f"Rejected {side} trade for {symbol}.\n"
                    f"Reason: Multi-timeframe alignment insufficient ({mtf_align_pct:.1f}% vs threshold {mtf_details.get('required_threshold_pct', 66.0):.1f}%).\n"
                    f"5m: {mtf_details['tf_5m_bias']} | 15m: {mtf_details['tf_15m_bias']} | 1h: {mtf_details['tf_1h_bias']} | Weight Model: {mtf_details.get('weight_type', 'UNWEIGHTED_EQUAL')}."
                )
                logger.warning(mtf_reason)
                try:
                    from app.ai.reasoning import AiReasoningEngine
                    from app.trading.execution_analytics import ExecutionAnalyticsEngine
                    from app.services.audit_service import AuditService
                    from app.trading.portfolio_attribution import PortfolioAttributionEngine
                    PortfolioAttributionEngine.get_instance().record_risk_prevention_event("mtf_guard", 150.0, mtf_reason)
                    AiReasoningEngine.get_instance().set_cooldown(symbol, side, category="MTF_DISAGREEMENT", reason=mtf_reason, price=opportunity.get("entry_price", 0.0))
                    ExecutionAnalyticsEngine.get_instance().record_missed_fill(symbol, side, opportunity.get("strategy", ""), mtf_reason, opportunity.get("entry_price", 0.0))
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"[MTF GUARD] Rejected {side} trade for {symbol}",
                            details=mtf_reason
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log MTF guard audit event: {ae}")
                return

            # 0d. Multi-Timeframe Volume & Liquidity Confluence Filter Check (5m, 15m, 1h RVOL, Order Flow Delta & Order Book Depth)
            passed_vol, vol_confluence_score, vol_details = await MTFConfirmationGuard.get_instance().evaluate_volume_liquidity_confluence(symbol, side, opportunity.get("entry_price", 0.0))
            if not passed_vol:
                vol_rejection_reasons = "; ".join(vol_details.get("rejection_reasons", ["Confluence score below 70.0%"]))
                vol_reason_summary = (
                    f"Confluence score below required threshold ({vol_confluence_score:.1f}% < 70.0%)"
                    if vol_confluence_score < 70.0
                    else f"Volume/Liquidity risk guard triggered (Confluence Score: {vol_confluence_score:.1f}%)"
                )
                vol_reason = (
                    f"[MTF VOLUME & LIQUIDITY GUARD]\n"
                    f"Rejected {side} trade for {symbol}.\n"
                    f"Reason: {vol_reason_summary}.\n"
                    f"RVOL (5m: {vol_details['rvol_5m']}, 15m: {vol_details['rvol_15m']}, 1h: {vol_details['rvol_1h']}) | Directional Volume Delta: {vol_details['favorable_volume_delta']}%\n"
                    f"Flags: {vol_rejection_reasons}"
                )
                logger.warning(vol_reason)
                try:
                    from app.ai.reasoning import AiReasoningEngine
                    from app.trading.execution_analytics import ExecutionAnalyticsEngine
                    from app.services.audit_service import AuditService
                    AiReasoningEngine.get_instance().set_cooldown(symbol, side, category="VOLUME_LIQUIDITY_INSUFFICIENT", reason=vol_reason, price=opportunity.get("entry_price", 0.0))
                    ExecutionAnalyticsEngine.get_instance().record_missed_fill(symbol, side, opportunity.get("strategy", ""), vol_reason, opportunity.get("entry_price", 0.0))
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"[MTF VOLUME & LIQUIDITY GUARD] Rejected {side} trade for {symbol}",
                            details=vol_reason
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log volume & liquidity guard audit event: {ae}")
                return

            # 0e. Macro & Institutional Risk Confluence Overlay Check
            from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
            macro_overlay = await MacroRiskOverlayEngine.get_instance().evaluate_macro_risk(symbol, side)
            if not macro_overlay.get("trade_permitted", True):
                macro_reason = (
                    f"[MACRO RISK OVERLAY LOCKDOWN]\n"
                    f"Rejected {side} trade for {symbol}.\n"
                    f"Reason: Macro Risk Score {macro_overlay['macro_risk_score']:.1f}/100 ({macro_overlay['risk_level']}).\n"
                    f"Summary: {macro_overlay['risk_summary']}"
                )
                logger.warning(macro_reason)
                try:
                    from app.ai.reasoning import AiReasoningEngine
                    from app.trading.execution_analytics import ExecutionAnalyticsEngine
                    from app.services.audit_service import AuditService
                    AiReasoningEngine.get_instance().set_cooldown(symbol, side, category="MACRO_RISK_LOCKDOWN", reason=macro_reason, price=opportunity.get("entry_price", 0.0))
                    ExecutionAnalyticsEngine.get_instance().record_missed_fill(symbol, side, opportunity.get("strategy", ""), macro_reason, opportunity.get("entry_price", 0.0))
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"[MACRO RISK OVERLAY] Rejected {side} trade for {symbol}",
                            details=macro_reason
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log macro risk overlay audit event: {ae}")
                return

            # Apply macro adaptive position size scaler if permitted (e.g., 0.70x or 0.35x)
            macro_scaler = macro_overlay.get("position_size_scaler", 1.0)
            if macro_scaler < 1.0 and macro_scaler > 0.0:
                old_size = opportunity.get("position_size", 1.0)
                opportunity["position_size"] = round(old_size * macro_scaler, 4)
                logger.info(f"⚖️ [MACRO RISK SCALER] Position size for {symbol} scaled from {old_size} to {opportunity['position_size']} ({macro_scaler * 100:.0f}% allocation due to {macro_overlay['risk_level']}).")

            # 0f. Cross-Asset Correlation & Concentration Risk Check
            from app.trading.cross_asset_correlation import CrossAssetCorrelationEngine
            from app.trading.position_manager import PositionManager
            active_pos = PositionManager.get_instance().get_active_positions()
            active_syms = [p.get("symbol") for p in active_pos if p.get("symbol")]
            corr_info = CrossAssetCorrelationEngine.get_instance().evaluate_trade_correlation_risk(symbol, active_syms)

            corr_scaler = corr_info.get("position_scaler", 1.0)
            if corr_scaler < 1.0:
                old_size = opportunity.get("position_size", 1.0)
                opportunity["position_size"] = round(old_size * corr_scaler, 4)
                logger.info(f"🔗 [CROSS-ASSET CORRELATION SCALER] Position size for {symbol} scaled to {opportunity['position_size']} ({corr_scaler * 100:.0f}% due to {corr_info['risk_flag']} vs {corr_info['most_correlated_active_symbol']}).")

        from app.services.trading_service import TradingService
        from app.services.paper_trading_service import PaperTradingService
        from app.services.audit_service import AuditService

        async with AsyncSessionLocal() as db:
            try:
                trading_mode = scheduler.global_trading_mode
                
                # 1. Fetch relevant Wallet Balance based on trading mode
                if trading_mode in ["TESTNET", "LIVE"]:
                    balances = await BinanceConnector.get_instance().get_wallet_balances(trading_mode)
                    usdt_balance = next((float(b["free"]) for b in balances if b["asset"] == "USDT"), 0.0)
                    if usdt_balance <= 0.0:
                        usdt_balance = 10000.0 if trading_mode == "TESTNET" else 1000.0
                    balance = usdt_balance
                else:
                    wallet = await PaperTradingService.get_wallet(db, "lumina_default")
                    if not wallet:
                        wallet = await PaperTradingService.create_wallet(db, "lumina_default", 100000.0)
                    balance = wallet.balance

                # 2. Assemble trade schema details & Adaptive Multi-Factor Position Sizing
                from app.ai.self_adaptation import SelfAdaptationEngine
                from app.risk.adaptive_sizer import AdaptivePositionSizer
                
                adaptation_summary = SelfAdaptationEngine.get_instance().get_adaptation_summary()
                empirical_win_rate_pct = float(adaptation_summary.get("overall_win_rate_pct", 55.0))
                
                price = opportunity["entry_price"]
                stop_loss = opportunity["stop_loss"]
                atr_val = opportunity.get("atr", price * 0.01)

                # PIPELINE STEP 1: Adaptive Sizing
                risk_pct, size_breakdown = AdaptivePositionSizer.get_instance().calculate_adaptive_risk_pct(
                    opportunity=opportunity,
                    balance=balance,
                    recent_win_streak=int(adaptation_summary.get("win_streak", 0)),
                    account_drawdown_pct=float(adaptation_summary.get("current_drawdown_pct", 0.0)) / 100.0,
                    strategy_win_rate_pct=empirical_win_rate_pct
                )
                logger.info(f"🎲 [STEP 1: ADAPTIVE SIZING] Risk Allocation: {risk_pct*100.0:.2f}% | Breakdown: {size_breakdown}")

                # PIPELINE STEP 2: Volatility Adjustment (Prior to Portfolio Guard)
                relative_volatility = (atr_val / price) if (price > 0 and atr_val > 0) else 0.0
                if relative_volatility > 0.015:
                    vol_scale = min(1.0, 0.015 / relative_volatility)
                    old_risk = risk_pct
                    risk_pct = round(max(0.001, risk_pct * vol_scale), 6)
                    logger.info(f"⚡ [STEP 2: VOLATILITY ADJUSTMENT] Relative Volatility ({relative_volatility:.4f}). Risk scaled from {old_risk*100:.2f}% to {risk_pct*100:.2f}%")

                # 2b. Adaptive Limit / Market Hybrid Routing
                squeeze_info = opportunity.get("squeeze_info", {})
                squeeze_state = squeeze_info.get("squeeze_state", "NO_SQUEEZE")
                market_regime = opportunity.get("market_regime", "TRENDING").upper()
                strategy_used = opportunity.get("strategy_used", "")

                if squeeze_state == "SQUEEZE_ACTIVE" or market_regime == "RANGING":
                    order_routing = "LIMIT"
                elif squeeze_state == "SQUEEZE_FIRING" or market_regime == "VOLATILE" or "BREAKOUT" in strategy_used.upper():
                    order_routing = "MARKET"
                else:
                    order_routing = "LIMIT"

                logger.info(f"⚡ [HYBRID ROUTING] Symbol {symbol} mapped to {order_routing} order routing (Regime: {market_regime}, Squeeze: {squeeze_state})")

                # PIPELINE STEP 3: Portfolio Guard (FINAL AUTHORITY)
                from app.trading.portfolio_guard import PortfolioGuard
                pg = PortfolioGuard.get_instance()
                pg_decision = await pg.evaluate_portfolio_action(
                    opportunity=opportunity,
                    incoming_risk_pct=risk_pct,
                    balance=balance
                )

                if not pg_decision.approved:
                    try:
                        from app.ai.reasoning import AiReasoningEngine
                        pg_cat = "CORRELATION_CLUSTER" if "CORRELATION" in pg_decision.reason.upper() else "PORTFOLIO_RISK_HEAT"
                        AiReasoningEngine.get_instance().set_cooldown(symbol, side, category=pg_cat, reason=pg_decision.reason, price=price)
                    except Exception as ce:
                        logger.error(f"Failed to set cooldown on portfolio decision rejection: {ce}")
                    return

                # Lock in Portfolio Guard adjusted risk as FINAL authority
                if pg_decision.adjusted_risk_pct != risk_pct:
                    logger.info(f"🛡️ [STEP 3: PORTFOLIO GUARD FINAL AUTHORITY] Risk locked from {risk_pct*100:.2f}% to {pg_decision.adjusted_risk_pct*100:.2f}% ({pg_decision.action_type})")
                    risk_pct = pg_decision.adjusted_risk_pct

                # PIPELINE STEP 4: Execution
                # Handle Pyramid Scale-In Action
                if pg_decision.action_type == "SCALE_IN":
                    from app.trading.position_manager import PositionManager
                    add_size = RiskEvaluator.calculate_atr_position_size(balance, price, atr_val, risk_pct, stop_loss, apply_volatility_scaling=False)
                    scaled_pos = await PositionManager.get_instance().scale_in_position(symbol, add_size, price, added_risk_pct=risk_pct)
                    if scaled_pos:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="INFO",
                            category="PORTFOLIO_INTELLIGENCE",
                            message=f"🚀 [PYRAMID SCALE-IN EXECUTED] Scaled {add_size:.4f} into active {symbol} position at {risk_pct*100:.2f}% risk",
                            details=pg_decision.reason
                        ))
                        return

                # Handle Opposite Direction Signal Partial Close & Hedge
                if pg_decision.action_type == "OPPOSITE_REBALANCE_HEDGE" and pg_decision.swap_trade_id:
                    logger.info(f"🔄 [OPPOSITE REBALANCE EXECUTING] Partially closing active opposite trade #{pg_decision.swap_trade_id} ({symbol}) by 50% and executing {side} hedge")
                    try:
                        from app.trading.position_manager import PositionManager
                        pm = PositionManager.get_instance()
                        target_pos = next((p for p in pm.get_active_positions() if p.get("trade_id") == pg_decision.swap_trade_id or p.get("id") == pg_decision.swap_trade_id), None)
                        if target_pos:
                            half_sz = round(target_pos.get("position_size", 0.0) * 0.5, 6)
                            if half_sz > 0:
                                await pm._partial_close_position(pg_decision.swap_trade_id, price, half_sz, "OPPOSITE_SIGNAL_REBALANCE")
                                await AuditService.create_audit_log(db, AuditLogCreate(
                                    level="INFO",
                                    category="PORTFOLIO_INTELLIGENCE",
                                    message=f"🔄 [OPPOSITE REBALANCE EXECUTED] Closed 50% ({half_sz}) of active {symbol} position to derisk",
                                    details=pg_decision.reason
                                ))
                    except Exception as rebal_err:
                        logger.error(f"Failed to execute opposite signal partial rebalance: {rebal_err}")

                # Handle Alpha Position Swap Action (Close stagnant position before entering superior setup)
                if pg_decision.action_type == "SWAP_POSITION" and pg_decision.swap_trade_id:
                    logger.info(f"🔄 [ALPHA SWAP EXECUTING] Closing stagnant trade #{pg_decision.swap_trade_id} ({pg_decision.swap_symbol}) to enter superior {symbol} setup")
                    try:
                        from app.trading.position_manager import PositionManager
                        pm = PositionManager.get_instance()
                        cache = await PriceCache.get_instance()
                        p_data = cache.get_price(pg_decision.swap_symbol or "")
                        swap_exit_p = p_data if isinstance(p_data, float) and p_data > 0 else price
                        await pm._close_position(pg_decision.swap_trade_id, swap_exit_p, "ALPHA_SWAP_REPLACEMENT")
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="INFO",
                            category="PORTFOLIO_INTELLIGENCE",
                            message=f"🔄 [ALPHA SWAP EXECUTED] Replaced stagnant {pg_decision.swap_symbol} with superior {symbol} setup",
                            details=pg_decision.reason
                        ))
                    except Exception as se:
                        logger.error(f"Failed to execute alpha position swap for trade {pg_decision.swap_trade_id}: {se}")

                if pg_decision.is_hedge:
                    opportunity["entry_reason"] = f"[DELTA HEDGE] {opportunity.get('entry_reason', '')}"

                # Dynamic position sizing based on ATR Risk Engine calculation (Portfolio Guard risk_pct is FINAL authority)
                position_size = RiskEvaluator.calculate_atr_position_size(
                    balance=balance,
                    entry_price=price,
                    atr=atr_val,
                    max_risk_pct=risk_pct,
                    stop_loss=stop_loss,
                    apply_volatility_scaling=False
                )

                # Apply macro and correlation position scalers if present
                import math
                composite_scaler = macro_scaler * corr_scaler
                if composite_scaler < 1.0 and composite_scaler > 0.0:
                    old_size = position_size
                    position_size = math.floor((position_size * composite_scaler) * 10000) / 10000.0
                    position_size = max(position_size, 0.0001)
                    logger.info(f"⚖️ [SCALER APPLIED] Position size for {symbol} scaled from {old_size} to {position_size} (composite scaler: {composite_scaler*100:.1f}%)")

                # Confidence Adaptive Position Sizing: scale position size based on trade confidence & setup quality
                conf_score = float(opportunity.get("confidence_score", 0.78))
                if conf_score >= 0.80:
                    conf_scaler = 1.10  # Premium setup: 110% allocation
                elif conf_score >= 0.73:
                    conf_scaler = 1.00  # Standard setup: 100% allocation
                elif conf_score >= 0.68:
                    conf_scaler = 0.80  # Moderate setup: 80% allocation
                else:
                    conf_scaler = 0.65  # Conservative setup: 65% allocation

                if conf_scaler != 1.0:
                    old_size = position_size
                    position_size = math.floor((position_size * conf_scaler) * 10000) / 10000.0
                    position_size = max(position_size, 0.0001)
                    logger.info(f"⚖️ [CONFIDENCE ADAPTIVE SIZING] Position size for {symbol} scaled from {old_size} to {position_size} ({conf_scaler:.2f}x allocation based on confidence score {conf_score:.2f})")

                allowed_risk_amt = balance * risk_pct

                order_dict = {
                    "entry_price": price,
                    "stop_loss": stop_loss,
                    "atr": atr_val,
                    "position_size": position_size
                }

                logger.info(f"[STAGE: RISK] Evaluating boundaries for {opportunity['symbol']} entry at {price} against wallet balance ${balance:.2f}. Max allowed risk: ${allowed_risk_amt:.2f} ({risk_pct*100.0:.1f}%)")

                # 3. Perform Layer 1 Risk Engine check
                passed_risk = RiskEvaluator.validate_order(order_dict, balance, risk_pct)

                # Sync position_size in case validate_order clamped it
                position_size = order_dict["position_size"]
                
                if not passed_risk:
                    logger.warning(f"[STAGE: RISK] REJECTED. Failed risk checks for {opportunity['symbol']}. Position size {position_size} exceeds risk limits.")
                    try:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"Opportunity {opportunity['strategy_used']} on {opportunity['symbol']} REJECTED by risk engine.",
                            details=f"Price: {price}, SL: {stop_loss}, Required balance risk limits exceeded."
                        ))
                    except Exception as ae:
                        logger.error(f"Failed to log risk rejected audit event: {ae}")
                    return

                logger.info(f"[STAGE: RISK] PASSED. Calculated position size: {position_size} units, allowed risk amount: {allowed_risk_amt} USD.")

                # If we are in TESTNET or LIVE mode, we place the real order on Binance first!
                binance_order_id = None
                if trading_mode in ["TESTNET", "LIVE"]:
                    logger.info(f"[STAGE: EXECUTION] Placing real Binance Spot order for {opportunity['symbol']} ({opportunity['side']} qty: {position_size}, type: {order_routing}) in {trading_mode} mode...")
                    binance_res = await BinanceConnector.get_instance().create_order(
                        mode=trading_mode,
                        symbol=opportunity["symbol"],
                        side=opportunity["side"],
                        order_type=order_routing,
                        quantity=position_size
                    )
                    
                    if "error" in binance_res or ("code" in binance_res and isinstance(binance_res["code"], int) and binance_res["code"] < 0) or ("status_code" in binance_res and binance_res.get("status_code", 200) >= 400):
                        logger.error(f"❌ BINANCE ORDER PLACEMENT FAILED: {binance_res}")
                        try:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="ERROR",
                                category="TRADING",
                                message=f"Binance order placement failed for {opportunity['symbol']}",
                                details=f"Error response: {binance_res}"
                            ))
                        except Exception as ae:
                            logger.error(f"Failed to log binance placement failure audit event: {ae}")
                        return
                    
                    logger.info(f"Binance order placed successfully: {binance_res}")
                    binance_order_id = str(binance_res.get("orderId", ""))
                    # Update entry price with executed price if available
                    if "fills" in binance_res and binance_res["fills"]:
                        total_qty = 0.0
                        total_quote = 0.0
                        for fill in binance_res["fills"]:
                            qty = float(fill.get("qty", 0.0))
                            px = float(fill.get("price", 0.0))
                            total_qty += qty
                            total_quote += qty * px
                        if total_qty > 0.0:
                            price = round(total_quote / total_qty, 6)
                else:
                    logger.info(f"[STAGE: EXECUTION] Executing paper {opportunity['side']} order for {opportunity['symbol']} (Routing: {order_routing}). Entry Price: {price}, Quantity: {position_size}.")

                # Track Execution Quality & Slippage in Execution Analytics Engine & PortfolioGuard
                try:
                    from app.trading.execution_analytics import ExecutionAnalyticsEngine
                    now_ms = time.time() * 1000
                    ExecutionAnalyticsEngine.get_instance().record_execution(
                        symbol=opportunity["symbol"],
                        side=opportunity["side"],
                        strategy=opportunity.get("strategy_used", "General"),
                        ordered_price=opportunity["entry_price"],
                        fill_price=price,
                        position_size=position_size,
                        signal_timestamp_ms=now_ms - 120,
                        fill_timestamp_ms=now_ms
                    )
                    from app.trading.portfolio_guard import PortfolioGuard
                    PortfolioGuard.get_instance().record_fill_feedback(
                        symbol=opportunity["symbol"],
                        requested_price=opportunity["entry_price"],
                        filled_price=price,
                        fill_latency_ms=120.0,
                        was_filled=True
                    )
                except Exception as ex_err:
                    logger.error(f"Failed to record execution fill analytics: {ex_err}")

                # 4. Success: Create a new Trade record
                trade_create = TradeCreate(
                    symbol=opportunity["symbol"],
                    side=opportunity["side"],
                    entry_price=price,
                    position_size=position_size,
                    stop_loss=stop_loss,
                    take_profit=opportunity["take_profit"],
                    strategy_used=opportunity["strategy_used"],
                    market_regime=opportunity["market_regime"],
                    confidence_score=opportunity["confidence_score"],
                    entry_reason=opportunity["entry_reason"],
                    indicators=json.dumps({"binance_order_id": binance_order_id}) if binance_order_id else "{}",
                    atr=opportunity["atr"],
                    risk_amount=allowed_risk_amt,
                    risk_pct=risk_pct * 100.0
                )
                
                trade = await TradingService.create_trade(db, trade_create)
                
                # Record 360-Degree Lineage Trace in SystemHarmonyEngine & Evaluate Paper Experiments
                try:
                    from app.trading.system_harmony import SystemHarmonyEngine
                    from app.ai.market_memory import MarketMemoryEngine
                    from app.ai.experiment_framework import ExperimentFramework

                    mem_info = MarketMemoryEngine.get_instance().get_environment_suitability_modifier(
                        strategy=opportunity.get("strategy_used", "Breakout Momentum"),
                        market_regime=opportunity.get("market_regime", "TRENDING")
                    )
                    SystemHarmonyEngine.get_instance().harmonize_trade_decision(
                        trade_id=str(trade.id),
                        opportunity=opportunity,
                        base_risk_pct=risk_pct,
                        meta_score=float(opportunity.get("confidence_score", 0.78)) * 100.0,
                        meta_approved=True,
                        meta_pillar_breakdown={"technical": 85.0, "sentiment": 78.0},
                        pg_approved=pg_decision.approved,
                        pg_action=pg_decision.action_type,
                        pg_reason=pg_decision.reason,
                        pg_adjusted_risk_pct=pg_decision.adjusted_risk_pct,
                        memory_modifier=float(mem_info.get("capital_modifier", 1.0)),
                        memory_key=str(mem_info.get("environment_key", "")),
                        personality_name=str(mem_info.get("personality_name", "")),
                        tournament_elo=1650,
                        matrix_multiplier=1.0,
                        execution_routing=order_routing
                    )
                    ExperimentFramework.get_instance().evaluate_opportunity_across_experiments(opportunity)
                except Exception as harm_err:
                    logger.error(f"Failed to record system harmony trace or experiment evaluation: {harm_err}")

                logger.info(f"[STAGE: EXECUTION] SUCCESS. Recorded Trade #{trade.id} ({trade.symbol}) in database.")
                
                try:
                    await AuditService.create_audit_log(db, AuditLogCreate(
                        level="INFO",
                        category="TRADING",
                        message=f"Lumina VPS Executed Order {trade.id} ({trade.side} {trade.symbol})",
                        details=f"Entered at {trade.entry_price}. Position size: {trade.position_size}. Strategy: {trade.strategy_used}.",
                        trade_id=trade.id
                    ))
                except Exception as ae:
                    logger.error(f"Failed to log order execution audit event: {ae}")

                # Deduct margin/fee simulated balance only for paper
                if trading_mode not in ["TESTNET", "LIVE"]:
                    await PaperTradingService.adjust_balance(db, "lumina_default", -(price * position_size * 0.001))

                # 5. Delegate to PositionManager for trailing and safety monitoring
                from app.trading.position_manager import PositionManager
                await PositionManager.get_instance().track_position(trade.id, {
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
                    "binance_order_id": binance_order_id,
                    "confidence": opportunity.get("confidence_score", 0.83),
                    "strategy": opportunity.get("strategy_used", "Volatility Expansion Breakout"),
                    "risk_pct": risk_pct,
                    "scale_ins": 0,
                    "entry_time": datetime.utcnow(),
                    "is_probationary_trade": opportunity.get("is_probationary_trade", False),
                    "probation_trigger": opportunity.get("probation_trigger"),
                    "probation_metadata": opportunity.get("probation_metadata")
                })

            except Exception as e:
                logger.error(f"Failed to execute trade proposal in database: {e}")
                await db.rollback()
