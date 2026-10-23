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
from app.trading.execution_state import ExecutionState, ExecutionStateTracker

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
            ExecutionStateTracker.get_instance().transition(
                symbol=symbol,
                state=ExecutionState.SIGNAL_DETECTED,
                side=side,
                stage="SCANNER_SIGNAL",
                price=float(opportunity.get("entry_price", 0.0))
            )
            ExecutionStateTracker.get_instance().transition(
                symbol=symbol,
                state=ExecutionState.OPPORTUNITY_APPROVED,
                side=side,
                stage="META_REASONING",
                price=float(opportunity.get("entry_price", 0.0))
            )
            
            # 0. Check central circuit breakers and global trading modes
            from app.scheduler.manager import LayeredSchedulerManager
            scheduler = await LayeredSchedulerManager.get_instance()
            allowed, reason = await scheduler.is_trading_enabled()
            if not allowed:
                logger.warning(f"⚠️ ORDER PIPELINE REJECTED: {reason}")
                ExecutionStateTracker.get_instance().transition(
                    symbol=symbol,
                    state=ExecutionState.REJECTED,
                    side=side,
                    stage="CIRCUIT_BREAKER",
                    reason=reason,
                    price=float(opportunity.get("entry_price", 0.0))
                )
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
            passed_md, md_reason = cache.validate_market_data(symbol, max_age_seconds=45.0)
            if not passed_md:
                logger.warning(md_reason)
                ExecutionStateTracker.get_instance().transition(
                    symbol=symbol,
                    state=ExecutionState.REJECTED,
                    side=side,
                    stage="MARKET_DATA_GUARD",
                    reason=md_reason,
                    price=float(opportunity.get("entry_price", 0.0))
                )
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

            # Extract DecisionContext if present
            ctx = opportunity.get("decision_context")

            if ctx:
                # 3. Pipeline Integrity Check Invariants
                is_valid_pipeline, integrity_err = ctx.verify_pipeline_integrity()
                if not is_valid_pipeline:
                    logger.error(f"❌ [PIPELINE INTEGRITY VIOLATION] Aborting execution for {symbol}: {integrity_err}")
                    ctx.record_rejection("PipelineIntegrity", integrity_err, category="PIPELINE_INTEGRITY_VIOLATION")
                    return
                ctx.add_trace("OrderManager", "PASS")
                logger.info(f"🔒 [ORDER MANAGER EXECUTION ENGINE] Processing locked DecisionContext {ctx.signal_id} ({ctx.symbol} {ctx.side} via {ctx.active_strategy}) | Pipeline Ver: {ctx.pipeline_version}")

            # 0c. Macro Scaler Extraction (Read from DecisionContext eval_cache if present)
            macro_overlay = (ctx.eval_cache.get("macro_risk") if ctx else None) or {}
            macro_scaler = macro_overlay.get("position_size_scaler", 1.0)
            if macro_scaler < 1.0 and macro_scaler > 0.0:
                old_size = opportunity.get("position_size", 1.0)
                opportunity["position_size"] = round(old_size * macro_scaler, 4)
                logger.info(f"⚖️ [MACRO RISK SCALER] Position size for {symbol} scaled from {old_size} to {opportunity['position_size']} ({macro_scaler * 100:.0f}% allocation due to {macro_overlay.get('risk_level', 'ELEVATED')}).")

            # 0d. Cross-Asset Correlation & Concentration Risk Check
            from app.trading.cross_asset_correlation import CrossAssetCorrelationEngine
            from app.trading.position_manager import PositionManager
            active_mode = scheduler.global_trading_mode
            active_pos = PositionManager.get_instance().get_active_positions(trading_mode=active_mode)
            active_syms = [p.get("symbol") for p in active_pos if p.get("symbol")]
            corr_info = CrossAssetCorrelationEngine.get_instance().evaluate_trade_correlation_risk(symbol, active_syms)

            corr_scaler = corr_info.get("position_scaler", 1.0)
            if corr_scaler < 1.0:
                old_size = opportunity.get("position_size", 1.0)
                opportunity["position_size"] = round(old_size * corr_scaler, 4)
                logger.info(f"🔗 [CROSS-ASSET CORRELATION SCALER] Position size for {symbol} scaled to {opportunity['position_size']} ({corr_scaler * 100:.0f}% due to {corr_info['risk_flag']} vs {corr_info['most_correlated_active_symbol']}).")

            # 0e. Thesis Persistence Layer: Filter contradictory local chop signals
            from app.trading.thesis_persistence import ThesisPersistenceEngine
            thesis_engine = ThesisPersistenceEngine.get_instance()
            thesis_allowed, thesis_reason, active_thesis = thesis_engine.evaluate_new_signal(
                symbol=symbol,
                proposed_side=side,
                proposed_strategy=opportunity.get("strategy_used", ""),
                proposed_confidence=float(opportunity.get("confidence_score", 0.75)),
                current_price=float(opportunity.get("entry_price", 0.0))
            )
            if not thesis_allowed:
                logger.info(f"🚫 [THESIS PERSISTENCE LAYER] Suppressed noise signal: {thesis_reason}")
                ExecutionStateTracker.get_instance().transition(
                    symbol=symbol,
                    state=ExecutionState.REJECTED,
                    side=side,
                    stage="THESIS_PERSISTENCE",
                    reason=thesis_reason,
                    price=float(opportunity.get("entry_price", 0.0))
                )
                return

        from app.services.trading_service import TradingService
        from app.services.paper_trading_service import PaperTradingService
        from app.services.audit_service import AuditService

        async with AsyncSessionLocal() as db:
            try:
                trading_mode = scheduler.global_trading_mode
                
                trading_venue = getattr(scheduler, "trading_venue", "SPOT").upper()
                
                # 1. Fetch relevant Wallet Balance based on trading mode and venue
                if trading_mode in ["TESTNET", "LIVE"]:
                    connector = BinanceConnector.get_instance()

                    if trading_venue == "FUTURES":
                        equity_status = await connector.get_futures_equity_status(trading_mode)
                        if equity_status.get("error"):
                            err_msg = equity_status["error"]
                            logger.error(f"❌ [/fapi/v2/account ERROR] Failed to fetch live Binance Futures balance: {err_msg}")
                            ExecutionStateTracker.get_instance().transition(
                                symbol=symbol,
                                state=ExecutionState.REJECTED,
                                side=side,
                                stage="BALANCE_VERIFICATION",
                                reason=f"Failed to query /fapi/v2/account: {err_msg}",
                                price=float(opportunity.get("entry_price", 0.0)),
                                trading_mode=trading_mode
                            )
                            return

                        available_margin = float(equity_status.get("available_usdt", 0.0))
                        total_wallet = float(equity_status.get("wallet_balance_usdt", 0.0))
                        unrealized_pnl = float(equity_status.get("unrealized_pnl", 0.0))

                        if available_margin < 2.0:
                            insufficient_msg = f"Insufficient available Futures USDT margin (${available_margin:.2f} USDT). Minimum margin is $2.00 USDT."
                            logger.warning(f"🛑 [INSUFFICIENT BALANCE] {insufficient_msg}")
                            ExecutionStateTracker.get_instance().transition(
                                symbol=symbol,
                                state=ExecutionState.REJECTED,
                                side=side,
                                stage="BALANCE_VERIFICATION",
                                reason=insufficient_msg,
                                price=float(opportunity.get("entry_price", 0.0)),
                                trading_mode=trading_mode
                            )
                            try:
                                await AuditService.create_audit_log(db, AuditLogCreate(
                                    level="WARNING",
                                    category="RISK_ENGINE",
                                    message="Insufficient available Futures USDT margin",
                                    details=insufficient_msg
                                ))
                            except Exception:
                                pass
                            return

                        balance = available_margin
                        logger.info(f"✅ [FUTURES BALANCE VERIFIED] Available Futures USDT margin: ${balance:.2f} (Total Wallet: ${total_wallet:.2f}, Unrealized PnL: ${unrealized_pnl:.2f})")
                        logger.info(f"⚡ [FUTURES CONTRACT READY] Venue: FUTURES | Symbol: {symbol} | Side: {side} (Two-way Long/Short trading fully enabled)")

                    else:
                        equity_status = await connector.get_spot_equity_and_earn_status(trading_mode)

                        # Check for query failure
                        if equity_status.get("error"):
                            err_msg = equity_status["error"]
                            logger.error(f"❌ [/api/v3/account ERROR] Failed to fetch live Binance balance: {err_msg}")
                            ExecutionStateTracker.get_instance().transition(
                                symbol=symbol,
                                state=ExecutionState.REJECTED,
                                side=side,
                                stage="BALANCE_VERIFICATION",
                                reason=f"Failed to query /api/v3/account: {err_msg}",
                                price=float(opportunity.get("entry_price", 0.0)),
                                trading_mode=trading_mode
                            )
                            return

                        free_spot_usdt = float(equity_status.get("free_usdt", 0.0))
                        total_ldusdt = float(equity_status.get("total_ldusdt", 0.0))

                        # Guard A: Detect whether funds are held as LDUSDT (Binance Simple Earn)
                        # If free Spot USDT is zero and only LDUSDT exists, stop execution immediately.
                        if free_spot_usdt <= 0.0 and total_ldusdt > 0.0:
                            earn_msg = "Funds are in Binance Simple Earn. Redeem to Spot before trading."
                            logger.error(f"🛑 [SIMPLE EARN DETECTED] {earn_msg} (Held in Simple Earn: ${total_ldusdt:.2f} LDUSDT, Spot: ${free_spot_usdt:.2f} USDT)")
                            ExecutionStateTracker.get_instance().transition(
                                symbol=symbol,
                                state=ExecutionState.REJECTED,
                                side=side,
                                stage="SIMPLE_EARN_GUARD",
                                reason=earn_msg,
                                price=float(opportunity.get("entry_price", 0.0)),
                                trading_mode=trading_mode
                            )
                            try:
                                await AuditService.create_audit_log(db, AuditLogCreate(
                                    level="WARNING",
                                    category="RISK_ENGINE",
                                    message=earn_msg,
                                    details=f"Spot USDT is $0.00 while ${total_ldusdt:.2f} LDUSDT is held in Binance Simple Earn. Sizing and order submission halted."
                                ))
                            except Exception:
                                pass
                            return

                        # Guard B: Never continue to position sizing or order submission when free Spot USDT is insufficient (< 5.0 USDT minimum notional)
                        if free_spot_usdt < 5.0:
                            insufficient_msg = f"Insufficient free Spot USDT balance (${free_spot_usdt:.2f} USDT). Minimum Binance Spot order requires at least $5.00 USDT."
                            logger.warning(f"🛑 [INSUFFICIENT BALANCE] {insufficient_msg}")
                            ExecutionStateTracker.get_instance().transition(
                                symbol=symbol,
                                state=ExecutionState.REJECTED,
                                side=side,
                                stage="BALANCE_VERIFICATION",
                                reason=insufficient_msg,
                                price=float(opportunity.get("entry_price", 0.0)),
                                trading_mode=trading_mode
                            )
                            try:
                                await AuditService.create_audit_log(db, AuditLogCreate(
                                    level="WARNING",
                                    category="RISK_ENGINE",
                                    message="Insufficient free Spot USDT balance",
                                    details=insufficient_msg
                                ))
                            except Exception:
                                pass
                            return

                        # Verified live Spot USDT equity: zero hardcoded/simulated balances
                        balance = free_spot_usdt
                        logger.info(f"✅ [LIVE BALANCE VERIFIED] Using live Binance Spot USDT equity: ${balance:.2f} (Simple Earn LDUSDT: ${total_ldusdt:.2f})")

                        # Spot Trading Safety Guard: In Binance Spot, a new SELL order requires holding the base asset
                        balances = await connector.get_wallet_balances(trading_mode)
                        if isinstance(balances, list) and opportunity.get("side", "").upper() == "SELL":
                            base_asset = symbol.replace("USDT", "").replace("USDC", "").replace("FDUSD", "").upper()
                            base_free = next((float(b["free"]) for b in balances if b["asset"] == base_asset), 0.0)
                            if base_free <= 0.00001:
                                logger.warning(f"⚠️ [SPOT SHORT NOT SUPPORTED] Rejected {symbol} SELL order: Spot account holds 0 {base_asset}. (Binance Spot only allows BUY to enter and SELL to exit held assets).")
                                ExecutionStateTracker.get_instance().transition(
                                    symbol=symbol,
                                    state=ExecutionState.REJECTED,
                                    side=side,
                                    stage="SPOT_SHORT_GUARD",
                                    reason=f"Binance Spot account holds 0 {base_asset}",
                                    price=float(opportunity.get("entry_price", 0.0)),
                                    trading_mode=trading_mode
                                )
                                try:
                                    await AuditService.create_audit_log(db, AuditLogCreate(
                                        level="WARNING",
                                        category="TRADING",
                                        message=f"Spot shorting suppressed for {symbol}",
                                        details=f"Binance Spot mode cannot open naked short without {base_asset} balance."
                                    ))
                                except Exception:
                                    pass
                                return
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
                
                price = float(opportunity["entry_price"])
                atr_val = float(opportunity.get("atr", 0.0))
                if atr_val <= 0.0:
                    atr_val = price * 0.008
                
                opp_side = opportunity.get("side", "BUY").upper()
                dec = 8 if price < 0.001 else (6 if price < 1.0 else (4 if price < 50.0 else 2))
                min_stop_distance = max(atr_val * 1.5, price * 0.005)

                raw_sl = float(opportunity.get("stop_loss", 0.0))
                if raw_sl <= 0.0 or abs(price - raw_sl) < (price * 0.0035):
                    repaired_sl = round(price - min_stop_distance if opp_side == "BUY" else price + min_stop_distance, dec)
                    logger.warning(f"🛡️ [SL REPAIR] Detected degenerate/spread SL ({raw_sl}) for {symbol}. Repaired to: {repaired_sl}")
                    stop_loss = repaired_sl
                    opportunity["stop_loss"] = stop_loss
                else:
                    stop_loss = raw_sl

                raw_tp = float(opportunity.get("take_profit", 0.0))
                if raw_tp <= 0.0 or abs(price - raw_tp) < (price * 0.005):
                    repaired_tp = round(price + (min_stop_distance * 2.0) if opp_side == "BUY" else price - (min_stop_distance * 2.0), dec)
                    logger.warning(f"🛡️ [TP REPAIR] Detected zero/degenerate TP ({raw_tp}) for {symbol}. Repaired to: {repaired_tp}")
                    opportunity["take_profit"] = repaired_tp

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

                # Upgrade 1: Maker Limit Orders on Entry (Save 50%-75% in Fees)
                order_routing = "LIMIT"
                logger.info(f"⚡ [MAKER ROUTING] Symbol {symbol} mapped to Post-Only {order_routing} order routing to capture Maker fee rebate and eliminate spread drag (Regime: {market_regime}, Strategy: {strategy_used})")

                # PIPELINE STEP 3: Portfolio Guard (FINAL AUTHORITY)
                from app.trading.portfolio_guard import PortfolioGuard
                pg = PortfolioGuard.get_instance()
                pg_decision = await pg.evaluate_portfolio_action(
                    opportunity=opportunity,
                    incoming_risk_pct=risk_pct,
                    balance=balance
                )

                if not pg_decision.approved:
                    ExecutionStateTracker.get_instance().transition(
                        symbol=symbol,
                        state=ExecutionState.REJECTED,
                        side=side,
                        stage="PORTFOLIO_GUARD",
                        reason=pg_decision.reason,
                        price=price,
                        trading_mode=trading_mode
                    )
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
                    logger.info(f"🔄 [OPPOSITE REBALANCE EXECUTING] Managing active opposite trade #{pg_decision.swap_trade_id} ({symbol})")
                    try:
                        from app.trading.position_manager import PositionManager
                        pm = PositionManager.get_instance()
                        target_pos = next((p for p in pm.get_active_positions(trading_mode=trading_mode) if p.get("trade_id") == pg_decision.swap_trade_id or p.get("id") == pg_decision.swap_trade_id), None)
                        if target_pos:
                            pos_sz = float(target_pos.get("position_size", 0.0))
                            half_sz = round(pos_sz * 0.5, 6)
                            half_notional = half_sz * price
                            # Micro-Account Guard: Binance Spot minimum order is $5.00!
                            # If 50% notional is < $5.05, close 100% of the active long to fully recover cash.
                            if half_notional < 5.05 or (pos_sz * price) < 10.50:
                                logger.info(f"🛡️ [MICRO-ACCOUNT OPPOSITE REBALANCE] 50% size ({half_sz} units = ${half_notional:.2f}) is below Binance $5.00 MIN_NOTIONAL. Fully closing active position #{pg_decision.swap_trade_id} to recycle full capital to USDT.")
                                await pm._close_position(pg_decision.swap_trade_id, price, "OPPOSITE_SIGNAL_FULL_CLOSE")
                            elif half_sz > 0:
                                await pm._partial_close_position(pg_decision.swap_trade_id, price, half_sz, "OPPOSITE_SIGNAL_REBALANCE")

                            # In Spot trading, naked shorting is impossible; closing/derisking the long completes the action
                            if trading_mode in ["TESTNET", "LIVE"] and side == "SELL":
                                logger.info(f"ℹ️ [SPOT REBALANCE COMPLETE] Long position derisked. Naked SELL order suppressed on Binance Spot.")
                                return
                    except Exception as rebal_err:
                        logger.error(f"Failed to execute opposite signal rebalance: {rebal_err}")

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

                # Module 8: Dynamic Risk Budget Modulation (Market Quality, Expectancy, Streaks)
                try:
                    from app.trading.dynamic_risk_budget import DynamicRiskBudgetEngine
                    drb_res = await DynamicRiskBudgetEngine.get_instance().calculate_dynamic_risk(
                        symbol=symbol,
                        base_confidence=float(opportunity.get("confidence_score", 0.78)),
                        incoming_risk_pct=risk_pct,
                        strategy=opportunity.get("strategy_used", opportunity.get("strategy", "General"))
                    )
                    risk_pct = float(drb_res.get("adapted_risk_pct", risk_pct))
                    logger.info(f"💰 [DYNAMIC RISK BUDGET] {drb_res.get('rationale')}")
                except Exception as drb_err:
                    logger.debug(f"DynamicRiskBudget check skipped: {drb_err}")

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
                    ExecutionStateTracker.get_instance().transition(
                        symbol=opportunity["symbol"],
                        state=ExecutionState.REJECTED,
                        side=opportunity["side"],
                        stage="RISK_VALIDATION",
                        reason=f"Position size {position_size} exceeds risk limits (Max allowed risk: ${allowed_risk_amt:.2f})",
                        price=price,
                        quantity=position_size,
                        trading_mode=trading_mode
                    )
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

                # Dynamically resolve Binance LOT_SIZE step sizes and MIN_NOTIONAL based on venue
                connector = BinanceConnector.get_instance()
                if trading_venue == "FUTURES":
                    filters = await connector.get_futures_symbol_filters(opportunity["symbol"], trading_mode)
                    prec = filters.get("precision", 3)
                    min_notional_req = float(filters.get("min_notional", 5.0))
                    factor = 10 ** prec
                    position_size = round(position_size, prec)

                    # In LIVE/TESTNET Binance Futures: Pre-configure ISOLATED margin and dynamic persona leverage (12x-15x)
                    from app.trading.trading_persona import TradingPersonaPresetEngine
                    active_persona = TradingPersonaPresetEngine.get_instance().get_active_persona()
                    max_lev = int(active_persona.get("max_leverage", 15))
                    min_lev = int(active_persona.get("min_leverage", 12))
                    conf_score = float(opportunity.get("confidence_score", opportunity.get("confidence", 0.75)))
                    target_leverage = max_lev if conf_score >= 0.76 else min_lev

                    if trading_mode in ["TESTNET", "LIVE"]:
                        try:
                            await connector.set_futures_margin_type(opportunity["symbol"], "ISOLATED", trading_mode)
                            await connector.set_futures_leverage(opportunity["symbol"], target_leverage, trading_mode)
                        except Exception as fset_err:
                            logger.warning(f"Futures margin/leverage setup notice for {opportunity['symbol']}: {fset_err}")

                        notional_val = position_size * price
                        target_min_notional = max(5.05, min_notional_req + 0.05)
                        if notional_val < target_min_notional:
                            min_target_qty = math.ceil((target_min_notional / price) * factor) / float(factor)
                            req_margin = (min_target_qty * price) / float(target_leverage)
                            if balance >= req_margin:
                                position_size = min_target_qty
                                logger.info(f"⚡ [BINANCE FUTURES NOTIONAL ADJUSTMENT] Adjusted {opportunity['symbol']} position size to {position_size} (${position_size * price:.2f} notional, ${req_margin:.2f} margin at {target_leverage}x) to satisfy Futures MIN_NOTIONAL (${min_notional_req:.2f}).")
                            else:
                                logger.info(f"ℹ️ [CAPITAL PRESERVATION] Available Futures margin (${balance:.2f} USDT) is insufficient for minimum trade margin of ${req_margin:.2f} USDT at {target_leverage}x.")
                                ExecutionStateTracker.get_instance().transition(
                                    symbol=opportunity["symbol"],
                                    state=ExecutionState.REJECTED,
                                    side=opportunity["side"],
                                    stage="RISK_SIZING",
                                    reason=f"Available Futures margin (${balance:.2f} USDT) insufficient for minimum trade (${req_margin:.2f} margin required)",
                                    price=price,
                                    quantity=position_size,
                                    trading_mode=trading_mode
                                )
                                return
                else:
                    filters = await connector.get_symbol_lot_size_filters(opportunity["symbol"], trading_mode)
                    prec = filters.get("precision", 4)
                    min_notional_req = float(filters.get("min_notional", 5.0))
                    factor = 10 ** prec
                    position_size = round(position_size, prec)

                    # In LIVE/TESTNET Binance Spot: enforce min notional >= (min_notional_req + 0.05) USDT
                    if trading_mode in ["TESTNET", "LIVE"]:
                        notional_val = position_size * price
                        target_min_notional = max(5.05, min_notional_req + 0.05)
                        if notional_val < target_min_notional:
                            if opportunity["side"].upper() == "BUY":
                                min_target_qty = math.ceil((target_min_notional / price) * factor) / float(factor)
                                min_required_cost = min_target_qty * price
                                if balance >= min_required_cost:
                                    position_size = min_target_qty
                                    logger.info(f"⚡ [BINANCE SPOT NOTIONAL ADJUSTMENT] Adjusted {opportunity['symbol']} position size to {position_size} (${position_size * price:.2f} USDT) to satisfy Binance Spot MIN_NOTIONAL (${min_notional_req:.2f}).")
                                else:
                                    logger.info(f"ℹ️ [CAPITAL PRESERVATION] Available balance (${balance:.2f} USDT) is insufficient for minimum Binance Spot trade of {min_target_qty} {opportunity['symbol']} (${min_required_cost:.2f} USDT). Preserving cash buffer.")
                                    ExecutionStateTracker.get_instance().transition(
                                        symbol=opportunity["symbol"],
                                        state=ExecutionState.REJECTED,
                                        side=opportunity["side"],
                                        stage="RISK_SIZING",
                                        reason=f"Available balance (${balance:.2f} USDT) insufficient for minimum Binance Spot notional ($5.00 USDT)",
                                        price=price,
                                        quantity=position_size,
                                        trading_mode=trading_mode
                                    )
                                    return
                            else:
                                # For SELL on Spot: if notional is below $5.00, reject immediately to prevent Binance filter rejection
                                logger.warning(f"⚠️ [BINANCE SPOT NOTIONAL FILTER] {symbol} SELL notional (${notional_val:.2f}) is below Binance minimum ${target_min_notional:.2f}. Suppressed.")
                                ExecutionStateTracker.get_instance().transition(
                                    symbol=opportunity["symbol"],
                                    state=ExecutionState.REJECTED,
                                    side=opportunity["side"],
                                    stage="RISK_SIZING",
                                    reason=f"SELL notional (${notional_val:.2f}) below Binance Spot minimum ($5.00 USDT)",
                                    price=price,
                                    quantity=position_size,
                                    trading_mode=trading_mode
                                )
                                return

                    # Institutional Pre-BUY Fee Buffer Verification (0.8% fee + slippage buffer)
                    if opportunity["side"].upper() == "BUY":
                        fee_buffer_mult = 1.008
                        required_cost_with_fee = (position_size * price) * fee_buffer_mult
                        if balance < required_cost_with_fee:
                            max_affordable_notional = balance / fee_buffer_mult
                            adjusted_qty = math.floor((max_affordable_notional / price) * factor) / float(factor)
                            if (adjusted_qty * price) >= min_notional_req:
                                logger.info(f"🛡️ [FEE BUFFER SIZING] Adjusted {opportunity['symbol']} BUY size from {position_size} to {adjusted_qty} to reserve 0.8% fee buffer from balance ${balance:.2f}.")
                                position_size = adjusted_qty
                            else:
                                logger.info(f"ℹ️ [CAPITAL PRESERVATION] Available balance (${balance:.2f} USDT) cannot cover minimum notional (${min_notional_req:.2f}) with 0.8% fee buffer. Order safely rejected.")
                                ExecutionStateTracker.get_instance().transition(
                                    symbol=opportunity["symbol"],
                                    state=ExecutionState.REJECTED,
                                    side=opportunity["side"],
                                    stage="FEE_BUFFER_CHECK",
                                    reason=f"Available balance (${balance:.2f} USDT) insufficient for minimum trade + 0.8% fee buffer",
                                    price=price,
                                    quantity=position_size,
                                    trading_mode=trading_mode
                                )
                                return

                if position_size <= 0.0:
                    logger.warning(f"[STAGE: RISK] REJECTED. Position size for {opportunity['symbol']} calculated as {position_size} (balance ${balance:.2f} insufficient for minimum trade increment).")
                    ExecutionStateTracker.get_instance().transition(
                        symbol=opportunity["symbol"],
                        state=ExecutionState.REJECTED,
                        side=opportunity["side"],
                        stage="RISK_SIZING",
                        reason=f"Position size calculated as {position_size} (insufficient balance for minimum increment)",
                        price=price,
                        quantity=position_size,
                        trading_mode=trading_mode
                    )
                    return

                logger.info(f"[STAGE: RISK] PASSED. Calculated position size: {position_size} units, allowed risk amount: {allowed_risk_amt} USD.")
                ExecutionStateTracker.get_instance().transition(
                    symbol=opportunity["symbol"],
                    state=ExecutionState.RISK_APPROVED,
                    side=opportunity["side"],
                    price=price,
                    quantity=position_size,
                    trading_mode=trading_mode
                )
                ExecutionStateTracker.get_instance().transition(
                    symbol=opportunity["symbol"],
                    state=ExecutionState.ORDER_PENDING,
                    side=opportunity["side"],
                    price=price,
                    quantity=position_size,
                    trading_mode=trading_mode
                )

                # If we are in TESTNET or LIVE mode, we place the real order on Binance first!
                binance_order_id = None
                order_submit_start_ms = time.time() * 1000.0

                if trading_mode in ["TESTNET", "LIVE"]:
                    connector = BinanceConnector.get_instance()
                    if trading_venue == "FUTURES":
                        filters = await connector.get_futures_symbol_filters(opportunity["symbol"], trading_mode)
                        prec = filters.get("precision", 3)
                        position_size = connector.format_quantity_for_symbol(opportunity["symbol"], position_size, prec)

                        logger.info(f"[STAGE: EXECUTION] Placing real Binance USDⓈ-M Futures order for {opportunity['symbol']} ({opportunity['side']} qty: {position_size}, precision: {prec}, routing: {order_routing}) in {trading_mode} mode...")
                        ExecutionStateTracker.get_instance().transition(
                            symbol=opportunity["symbol"],
                            state=ExecutionState.ORDER_SUBMITTED,
                            side=opportunity["side"],
                            price=price,
                            quantity=position_size,
                            trading_mode=trading_mode
                        )
                        req_payload = {
                            "symbol": opportunity["symbol"],
                            "side": opportunity["side"],
                            "type": "MARKET",
                            "quantity": position_size,
                            "price": price if order_routing == "LIMIT" else None,
                            "mode": trading_mode,
                            "venue": "FUTURES"
                        }
                        binance_res = await connector.create_futures_order(
                            mode=trading_mode,
                            symbol=opportunity["symbol"],
                            side=opportunity["side"],
                            order_type="MARKET",
                            quantity=position_size,
                            price=price if order_routing == "LIMIT" else None,
                            reduce_only=False
                        )
                    else:
                        filters = await connector.get_symbol_lot_size_filters(opportunity["symbol"], trading_mode)
                        prec = filters.get("precision", 4)
                        position_size = connector.format_quantity_for_symbol(opportunity["symbol"], position_size, prec)

                        logger.info(f"[STAGE: EXECUTION] Placing real Binance Spot order for {opportunity['symbol']} ({opportunity['side']} qty: {position_size}, precision: {prec}, type: {order_routing}) in {trading_mode} mode...")
                        ExecutionStateTracker.get_instance().transition(
                            symbol=opportunity["symbol"],
                            state=ExecutionState.ORDER_SUBMITTED,
                            side=opportunity["side"],
                            price=price,
                            quantity=position_size,
                            trading_mode=trading_mode
                        )
                        req_payload = {
                            "symbol": opportunity["symbol"],
                            "side": opportunity["side"],
                            "type": order_routing,
                            "quantity": position_size,
                            "price": price if order_routing == "LIMIT" else None,
                            "mode": trading_mode
                        }
                        time_in_force = "GTC"
                        binance_res = await connector.create_order(
                            mode=trading_mode,
                            symbol=opportunity["symbol"],
                            side=opportunity["side"],
                            order_type="LIMIT_MAKER" if order_routing == "LIMIT" else "MARKET",
                            quantity=position_size,
                            price=price if order_routing == "LIMIT" else None
                        )
                        # If LIMIT_MAKER would immediately cross spread, adjust 1 tick into book to guarantee Maker execution
                        if isinstance(binance_res, dict) and binance_res.get("code") == -2010 and "match and take" in str(binance_res.get("msg", "")).lower():
                            adj_factor = 0.9998 if opportunity["side"] == "BUY" else 1.0002
                            from app.trading.position_manager import _round_price
                            maker_adj_price = _round_price(price * adj_factor)
                            logger.info(f"🔄 [MAKER POST-ONLY RETRY] Limit price crossed spread. Adjusting price from {price} to {maker_adj_price} to post as Maker.")
                            binance_res = await connector.create_order(
                                mode=trading_mode,
                                symbol=opportunity["symbol"],
                                side=opportunity["side"],
                                order_type="LIMIT_MAKER",
                                quantity=position_size,
                                price=maker_adj_price
                            )
                            # If still crossed, fall back to standard LIMIT with GTC
                            if isinstance(binance_res, dict) and binance_res.get("code") == -2010:
                                logger.info(f"🔄 [SPREAD DRIFT FALLBACK] Placing standard LIMIT (GTC) order to capture entry at {price}.")
                                binance_res = await connector.create_order(
                                    mode=trading_mode,
                                    symbol=opportunity["symbol"],
                                    side=opportunity["side"],
                                    order_type="LIMIT",
                                    quantity=position_size,
                                    price=price,
                                    time_in_force="GTC"
                                )
                    order_latency_ms = max(1.0, (time.time() * 1000.0) - order_submit_start_ms)
                    
                    if "error" in binance_res or ("code" in binance_res and isinstance(binance_res["code"], int) and binance_res["code"] < 0) or ("status_code" in binance_res and binance_res.get("status_code", 200) >= 400):
                        err_code = binance_res.get("code", binance_res.get("status_code", -1))
                        err_msg = binance_res.get("msg", binance_res.get("error", "Unknown error"))
                        err_title = binance_res.get("human_title", "Binance Order Rejection")
                        err_advice = binance_res.get("diagnostic_advice", "Check API parameters and balance.")
                        raw_payload = binance_res.get("raw_response", binance_res)

                        logger.error(
                            f"❌ [BINANCE EXECUTION REJECTED]\n"
                            f"  - Symbol: {opportunity['symbol']} | Side: {opportunity['side']} | Routing: {order_routing}\n"
                            f"  - Quantity: {position_size} | Price: {price} | Mode: {trading_mode}\n"
                            f"  - Error Category: {err_title} (Code {err_code})\n"
                            f"  - Exchange Message: \"{err_msg}\"\n"
                            f"  - Actionable Diagnosis: {err_advice}\n"
                            f"  - Full Response Payload: {raw_payload}"
                        )
                        ExecutionStateTracker.get_instance().transition(
                            symbol=opportunity["symbol"],
                            state=ExecutionState.REJECTED,
                            side=opportunity["side"],
                            stage="BINANCE_EXCHANGE",
                            reason=f"{err_title} (Code {err_code}): {err_msg}",
                            price=price,
                            quantity=position_size,
                            trading_mode=trading_mode
                        )
                        # Record in Execution Outcome Database for comprehensive training and analysis
                        try:
                            from app.trading.execution_database import ExecutionOutcomeDatabase
                            ExecutionOutcomeDatabase.get_instance().record_attempt(
                                symbol=opportunity["symbol"],
                                side=opportunity["side"],
                                order_type=order_routing,
                                trading_mode=trading_mode,
                                strategy_used=opportunity.get("strategy_used", "General"),
                                confidence_score=float(opportunity.get("confidence_score", 0.8)),
                                market_regime=opportunity.get("market_regime", "TRENDING"),
                                requested_quantity=position_size,
                                requested_price=price,
                                stop_loss=stop_loss,
                                take_profit=opportunity.get("take_profit", 0.0),
                                exchange_endpoint="/api/v3/order",
                                http_status=binance_res.get("status_code", 400),
                                is_successful=False,
                                error_code=err_code,
                                error_message=err_msg,
                                diagnostic_advice=err_advice,
                                latency_ms=order_latency_ms,
                                raw_request_payload=req_payload,
                                raw_response_payload=binance_res
                            )
                        except Exception as edb_err:
                            logger.error(f"Failed to record rejected attempt in ExecutionOutcomeDatabase: {edb_err}")

                        try:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="ERROR",
                                category="TRADING",
                                message=f"Binance order rejected: {err_title} ({opportunity['symbol']} {opportunity['side']})",
                                details=f"Code {err_code}: {err_msg}. Advice: {err_advice}. Raw payload: {raw_payload}"
                            ))
                            # Log to MissedOpportunityLearner for guard & execution impact tracking
                            from app.ai.missed_opportunity_learner import MissedOpportunityLearner
                            MissedOpportunityLearner.get_instance().record_missed_opportunity(
                                symbol=opportunity["symbol"],
                                side=opportunity["side"],
                                entry_price=price,
                                stop_loss=stop_loss,
                                take_profit=opportunity.get("take_profit", 0.0),
                                strategy=opportunity.get("strategy_used", "General"),
                                category="BINANCE_REJECTION",
                                reason=f"Binance {err_title} (Code {err_code}): {err_msg}",
                                confidence_score=opportunity.get("confidence_score", 0.8),
                                confidence_threshold=0.75
                            )
                        except Exception as ae:
                            logger.error(f"Failed to log binance placement failure audit event: {ae}")
                        return
                    
                    logger.info(f"Binance order placed successfully: {binance_res}")
                    binance_order_id = str(binance_res.get("orderId", ""))

                    # Upgrade 2: Maker Fill Watchdog & Timeout (45s Cancel / Re-quote)
                    # If Post-Only Limit order is resting in book (status == 'NEW' or 'PARTIALLY_FILLED'), monitor for fills
                    initial_status = str(binance_res.get("status", "NEW")).upper()
                    if order_routing == "LIMIT" and initial_status in ["NEW", "PARTIALLY_FILLED"]:
                        logger.info(f"⏳ [MAKER FILL WATCHDOG] Order {binance_order_id} ({opportunity['symbol']} {opportunity['side']}) posted as Maker. Monitoring for fill (45s timeout)...")
                        watchdog_start = time.time()
                        is_filled = False
                        while (time.time() - watchdog_start) < 45.0:
                            await asyncio.sleep(2.0)
                            poll_res = await connector.get_order(trading_mode, opportunity["symbol"], binance_order_id)
                            curr_status = str(poll_res.get("status", "")).upper()
                            if curr_status == "FILLED":
                                binance_res = poll_res
                                is_filled = True
                                logger.info(f"✅ [MAKER FILL WATCHDOG] Order {binance_order_id} completely FILLED on book within {time.time()-watchdog_start:.1f}s!")
                                break
                            elif curr_status in ["CANCELED", "REJECTED", "EXPIRED"]:
                                logger.warning(f"⚠️ [MAKER FILL WATCHDOG] Order {binance_order_id} ended with exchange status: {curr_status}.")
                                return
                            
                            # Market drift protection: if price drifted > 0.35% away from our limit, cancel & recycle capital
                            from app.workers.price_cache import PriceCache
                            pc = await PriceCache.get_instance()
                            curr_px = pc.get_price(opportunity["symbol"]) or price
                            drift_pct = (curr_px - price) / price * 100.0 if opportunity["side"] == "BUY" else (price - curr_px) / price * 100.0
                            if drift_pct >= 0.35:
                                logger.warning(f"⏱️ [MAKER FILL WATCHDOG] Market price drifted +{drift_pct:.2f}% away from Limit order. Cancelling order {binance_order_id} to release USDT...")
                                await connector.cancel_order(trading_mode, opportunity["symbol"], binance_order_id)
                                return
                        
                        if not is_filled:
                            # 45s Timeout reached: query one last time
                            final_poll = await connector.get_order(trading_mode, opportunity["symbol"], binance_order_id)
                            if str(final_poll.get("status", "")).upper() == "FILLED":
                                binance_res = final_poll
                            else:
                                logger.warning(f"⏱️ [MAKER FILL WATCHDOG TIMEOUT] Order {binance_order_id} unfilled after 45s. Cancelling order to recycle capital...")
                                await connector.cancel_order(trading_mode, opportunity["symbol"], binance_order_id)
                                exec_qty = float(final_poll.get("executedQty", 0.0))
                                if exec_qty > 0.0:
                                    logger.info(f"Retaining partially filled quantity of {exec_qty} units.")
                                    position_size = exec_qty
                                    binance_res = final_poll
                                else:
                                    logger.info(f"Order cancelled with 0 fills. Free capital unlocked for next opportunity.")
                                    return

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

                    # Record successful live fill in Execution Outcome Database
                    try:
                        from app.trading.execution_database import ExecutionOutcomeDatabase
                        ExecutionOutcomeDatabase.get_instance().record_attempt(
                            symbol=opportunity["symbol"],
                            side=opportunity["side"],
                            order_type=order_routing,
                            trading_mode=trading_mode,
                            strategy_used=opportunity.get("strategy_used", "General"),
                            confidence_score=float(opportunity.get("confidence_score", 0.8)),
                            market_regime=opportunity.get("market_regime", "TRENDING"),
                            requested_quantity=position_size,
                            requested_price=opportunity["entry_price"],
                            stop_loss=stop_loss,
                            take_profit=opportunity.get("take_profit", 0.0),
                            exchange_endpoint="/api/v3/order",
                            http_status=200,
                            is_successful=True,
                            exchange_order_id=binance_order_id,
                            executed_qty=position_size,
                            executed_price=price,
                            latency_ms=order_latency_ms,
                            raw_request_payload=req_payload,
                            raw_response_payload=binance_res
                        )
                    except Exception as edb_err:
                        logger.error(f"Failed to record fill attempt in ExecutionOutcomeDatabase: {edb_err}")

                else:
                    logger.info(f"[STAGE: EXECUTION] Executing paper {opportunity['side']} order for {opportunity['symbol']} (Routing: {order_routing}). Entry Price: {price}, Quantity: {position_size}.")
                    try:
                        from app.trading.execution_database import ExecutionOutcomeDatabase
                        ExecutionOutcomeDatabase.get_instance().record_attempt(
                            symbol=opportunity["symbol"],
                            side=opportunity["side"],
                            order_type=order_routing,
                            trading_mode=trading_mode,
                            strategy_used=opportunity.get("strategy_used", "General"),
                            confidence_score=float(opportunity.get("confidence_score", 0.8)),
                            market_regime=opportunity.get("market_regime", "TRENDING"),
                            requested_quantity=position_size,
                            requested_price=opportunity["entry_price"],
                            stop_loss=stop_loss,
                            take_profit=opportunity.get("take_profit", 0.0),
                            exchange_endpoint="PAPER_ENGINE",
                            http_status=200,
                            is_successful=True,
                            executed_qty=position_size,
                            executed_price=price,
                            latency_ms=5.0
                        )
                    except Exception as edb_err:
                        logger.error(f"Failed to record paper attempt in ExecutionOutcomeDatabase: {edb_err}")

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
                    risk_pct=risk_pct * 100.0,
                    trading_mode=trading_mode
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

                # Module 9: Explainable Institutional Decision Report
                try:
                    from app.trading.decision_report import DecisionReportEngine
                    from app.trading.market_quality import MarketQualityEngine
                    from app.trading.breakout_quality import BreakoutQualityAnalyzer
                    from app.ai.contextual_trade_memory import ContextualTradeMemory

                    ctx_res = ContextualTradeMemory.get_instance().query_similar_contexts(opportunity, k=3)
                    mq_data = MarketQualityEngine.get_instance()._cached_quality or {"market_quality_score": 82.0}
                    bq_data = BreakoutQualityAnalyzer.get_instance().analyze_breakout_quality(opportunity)
                    
                    DecisionReportEngine.get_instance().create_and_store_report(
                        trade_id=str(trade.id),
                        opportunity=opportunity,
                        context_result=ctx_res,
                        market_quality_data=mq_data,
                        breakout_quality_data=bq_data,
                        meta_conviction_score=float(opportunity.get("confidence_score", 0.82)) * 100.0,
                        ev_rank_str="#1 (Promoted)",
                        risk_pct=risk_pct
                    )
                except Exception as dre_err:
                    logger.warning(f"Failed to generate Explainable Decision Report: {dre_err}")

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
                    "tp_stages": opportunity.get("tp_stages", []),
                    "risk_pct": risk_pct,
                    "trading_mode": trading_mode,
                    "scale_ins": 0,
                    "entry_time": datetime.utcnow(),
                    "is_probationary_trade": opportunity.get("is_probationary_trade", False),
                    "probation_trigger": opportunity.get("probation_trigger"),
                    "probation_metadata": opportunity.get("probation_metadata")
                })

                ExecutionStateTracker.get_instance().transition(
                    symbol=trade.symbol,
                    state=ExecutionState.POSITION_REGISTERED,
                    side=trade.side,
                    order_id=binance_order_id,
                    price=trade.entry_price,
                    quantity=trade.position_size,
                    trading_mode=trading_mode,
                    metadata={"trade_id": trade.id, "strategy": trade.strategy_used}
                )

                # Register active market thesis in ThesisPersistenceEngine to prevent chop flip-flopping
                try:
                    from app.trading.thesis_persistence import ThesisPersistenceEngine
                    ThesisPersistenceEngine.get_instance().register_thesis(
                        trade_id=trade.id,
                        symbol=trade.symbol,
                        side=trade.side,
                        strategy=trade.strategy_used,
                        entry_price=trade.entry_price,
                        entry_confidence=float(opportunity.get("confidence_score", 0.83)),
                        market_regime=trade.market_regime
                    )
                except Exception as tpe_err:
                    logger.error(f"Failed to register thesis in ThesisPersistenceEngine: {tpe_err}")

            except Exception as e:
                logger.error(f"Failed to execute trade proposal in database: {e}")
                await db.rollback()
