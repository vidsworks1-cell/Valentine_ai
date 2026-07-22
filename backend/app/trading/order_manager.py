import asyncio
import logging
import json
from typing import Dict, Any, List, Optional
from app.risk.evaluator import RiskEvaluator
from app.database.session import AsyncSessionLocal
from app.services.trading_service import TradingService
from app.services.paper_trading_service import PaperTradingService
from app.services.binance_connector import BinanceConnector
from app.api.schemas import TradeCreate, AuditLogCreate
from app.services.audit_service import AuditService

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

            # 0b. Duplicate Position Protection Guard: check if an active position already exists for symbol and side (status == OPEN)
            from app.trading.position_manager import PositionManager
            active_positions = PositionManager.get_instance().get_active_positions()
            is_duplicate = any(
                p.get("symbol") == symbol and p.get("side") == side
                for p in active_positions
            )

            if not is_duplicate:
                from sqlalchemy.future import select
                from app.models.trading import Trade
                try:
                    async with AsyncSessionLocal() as db:
                        query = select(Trade).where(
                            Trade.symbol == symbol,
                            Trade.side == side,
                            Trade.exit_price.is_(None)
                        )
                        res = await db.execute(query)
                        if res.scalars().first() is not None:
                            is_duplicate = True
                except Exception as de:
                    logger.error(f"Error checking duplicate position in DB: {de}")

            if is_duplicate:
                logger.warning(f"[DUPLICATE GUARD] Existing {side} position already open for {symbol}. Ignoring duplicate AI signal.")
                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="TRADING",
                            message=f"[DUPLICATE GUARD] Rejected duplicate {side} signal for {symbol}",
                            details=f"Existing {side} position already open for {symbol}. Ignoring duplicate AI signal."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log duplicate guard audit event: {ae}")
                return

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

                # 2. Assemble trade schema details
                risk_pct = 0.02 # Default 2% max per trade risk
                price = opportunity["entry_price"]
                stop_loss = opportunity["stop_loss"]
                atr_val = opportunity.get("atr", price * 0.01)
                
                # Dynamic position sizing based on ATR Risk Engine calculation
                position_size = RiskEvaluator.calculate_atr_position_size(balance, price, atr_val, risk_pct)
                allowed_risk_amt = balance * risk_pct

                order_dict = {
                    "entry_price": price,
                    "stop_loss": stop_loss,
                    "position_size": position_size
                }

                logger.info(f"[STAGE: RISK] Evaluating boundaries for {opportunity['symbol']} entry at {price} against wallet balance {balance}. Max allowed risk: {allowed_risk_amt} ({risk_pct*100.0}%)")

                # 3. Perform Layer 1 Risk Engine check
                passed_risk = RiskEvaluator.validate_order(order_dict, balance, risk_pct)
                
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
                    logger.info(f"[STAGE: EXECUTION] Placing real Binance Spot order for {opportunity['symbol']} ({opportunity['side']} qty: {position_size}) in {trading_mode} mode...")
                    binance_res = await BinanceConnector.get_instance().create_order(
                        mode=trading_mode,
                        symbol=opportunity["symbol"],
                        side=opportunity["side"],
                        order_type="MARKET",
                        quantity=position_size
                    )
                    
                    if "error" in binance_res or "msg" in binance_res and binance_res.get("error"):
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
                    logger.info(f"[STAGE: EXECUTION] Executing paper {opportunity['side']} order for {opportunity['symbol']}. Entry Price: {price}, Quantity: {position_size}.")

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
                    "atr": trade.atr,
                    "binance_order_id": binance_order_id
                })

            except Exception as e:
                logger.error(f"Failed to execute trade proposal in database: {e}")
                await db.rollback()
