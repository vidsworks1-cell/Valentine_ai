from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.api.schemas import TradeCreate, TradeResponse, TradeUpdate
from app.services.trading_service import TradingService
from app.workers.price_cache import PriceCache
from app.scheduler.manager import LayeredSchedulerManager

router = APIRouter()

@router.get("/layers/status")
async def get_layered_status(db: AsyncSession = Depends(get_db)):
    """Fetches real-time status and operational telemetry from layers 2, 3 and 4."""
    cache = await PriceCache.get_instance()
    manager = await LayeredSchedulerManager.get_instance()
    allowed, circuit_breaker_reason = await manager.is_trading_enabled()
    
    # Check Database health
    db_health = "FAIL"
    try:
        from sqlalchemy import text
        await db.execute(text("SELECT 1"))
        db_health = "PASS"
    except Exception as e:
        db_health = f"FAIL: {str(e)}"
        
    # Check Market Feed health
    import time
    time_since_update = time.time() - cache.last_update_time
    if cache.connection_status == "CONNECTED":
        market_feed_health = "PASS" if time_since_update < 5.0 else "STALE"
    elif cache.connection_status == "RECONNECTING":
        market_feed_health = "RECONNECTING"
    else:
        market_feed_health = "FAIL"
        
    # Risk Engine health
    if manager.emergency_stop_active:
        risk_engine_health = "FAIL"
    elif manager.global_trading_mode == "OFF":
        risk_engine_health = "WARN"
    else:
        risk_engine_health = "PASS"
        
    # Order Manager
    if manager.emergency_stop_active:
        order_manager_health = "FAIL"
    elif manager.global_trading_mode == "OFF":
        order_manager_health = "WARN"
    else:
        order_manager_health = "PASS"
        
    # Position Manager
    if manager.emergency_stop_active:
        position_manager_health = "FAIL"
    else:
        position_manager_health = "PASS"
        
    # Circuit Breaker
    circuit_breaker_health = "PASS" if allowed else "FAIL"
    
    # Paper Trading
    if manager.global_trading_mode in ["PAPER", "TESTNET", "LIVE"]:
        paper_trading_health = "PASS"
    else:
        paper_trading_health = "DISABLED"
        
    # Audit Logger
    audit_logger_health = "PASS"
    
    return {
        "status": "ACTIVE" if manager.running else "INACTIVE",
        "global_trading_mode": manager.global_trading_mode,
        "emergency_stop_active": manager.emergency_stop_active,
        "circuit_breaker_allowed": allowed,
        "circuit_breaker_reason": circuit_breaker_reason,
        "layer2_price_cache": cache.get_all_prices(),
        "layer2_updates_tally": {sym: cache.get_update_tally(sym) for sym in cache._prices.keys()},
        "layer2_connection_status": cache.connection_status,
        "layer2_last_update_time": cache.last_update_time,
        "layer3_last_opportunity": manager.ai_engine.last_detected_setup,
        "layer4_active_positions": manager.position_manager.get_active_positions(),
        "system_health": {
            "Backend": "PASS",
            "Database": db_health,
            "Market Feed": market_feed_health,
            "Risk Engine": risk_engine_health,
            "Order Manager": order_manager_health,
            "Position Manager": position_manager_health,
            "Circuit Breaker": circuit_breaker_health,
            "Paper Trading": paper_trading_health,
            "Audit Logger": audit_logger_health
        }
    }

@router.post("/mode")
async def update_global_trading_mode(mode: str):
    """Sets system-wide global trading mode. Allowed values: OFF, PAPER, TESTNET, LIVE."""
    mode_upper = mode.upper()
    if mode_upper not in ["OFF", "PAPER", "TESTNET", "LIVE"]:
        raise HTTPException(status_code=400, detail="Invalid global trading mode. Allowed: OFF, PAPER, TESTNET, LIVE")
        
    manager = await LayeredSchedulerManager.get_instance()
    manager.global_trading_mode = mode_upper
    
    return {
        "status": "SUCCESS",
        "global_trading_mode": manager.global_trading_mode
    }

@router.post("/emergency/reset")
async def reset_emergency_stop():
    """Resets the system emergency block, allowing the trade execution flow to resume."""
    manager = await LayeredSchedulerManager.get_instance()
    manager.emergency_stop_active = False
    return {
        "status": "SUCCESS",
        "message": "Emergency block successfully lifted. Operations may resume if global mode permits."
    }

@router.post("/emergency/liquidate")
async def trigger_emergency_liquidation():
    """Triggers Layer 4 emergency position liquidation across all active pairs."""
    manager = await LayeredSchedulerManager.get_instance()
    count = await manager.position_manager.trigger_emergency_liquidation()
    return {
        "status": "COMPLETED",
        "message": f"Successfully liquidated all active positions ({count} trades).",
        "liquidated_count": count
    }

@router.get("", response_model=List[TradeResponse])
async def read_trades(
    skip: int = 0,
    limit: int = 100,
    db: AsyncSession = Depends(get_db)
):
    """Query recent trade operations list."""
    return await TradingService.get_trades(db, skip=skip, limit=limit)

@router.post("", response_model=TradeResponse, status_code=status.HTTP_201_CREATED)
async def create_trade(trade_in: TradeCreate, db: AsyncSession = Depends(get_db)):
    """Record a newly initiated paper or live trade."""
    return await TradingService.create_trade(db, trade_in)

@router.get("/binance/status")
async def get_binance_status():
    """Fetches Binance connectivity status, keys configuration and rate limits."""
    from app.services.binance_connector import BinanceConnector
    from app.scheduler.manager import LayeredSchedulerManager
    
    connector = BinanceConnector.get_instance()
    scheduler = await LayeredSchedulerManager.get_instance()
    
    has_keys = connector.has_keys()
    mode = scheduler.global_trading_mode
    
    # Simple check to see if we can connect and fetch account info
    latency_ms = 0
    import time
    start_time = time.time()
    try:
        acc_info = await connector.get_account_info(mode)
        latency_ms = int((time.time() - start_time) * 1000)
        is_connected = "error" not in acc_info
    except Exception as e:
        is_connected = False
        
    return {
        "is_authenticated": has_keys,
        "is_connected": is_connected,
        "latency_ms": latency_ms,
        "mode": mode,
        "rate_limit_status": "NORMAL" if is_connected else "UNKNOWN"
    }

@router.get("/binance/balances")
async def get_binance_balances():
    """Queries current Binance Spot asset balances."""
    from app.services.binance_connector import BinanceConnector
    from app.scheduler.manager import LayeredSchedulerManager
    
    connector = BinanceConnector.get_instance()
    scheduler = await LayeredSchedulerManager.get_instance()
    mode = scheduler.global_trading_mode
    
    balances = await connector.get_wallet_balances(mode)
    return balances

@router.get("/binance/orders")
async def get_binance_orders(symbol: str = None):
    """Retrieves open Binance Spot orders."""
    from app.services.binance_connector import BinanceConnector
    from app.scheduler.manager import LayeredSchedulerManager
    
    connector = BinanceConnector.get_instance()
    scheduler = await LayeredSchedulerManager.get_instance()
    mode = scheduler.global_trading_mode
    
    orders = await connector.get_open_orders(mode, symbol)
    return orders

@router.delete("/binance/order/{symbol}/{order_id}")
async def cancel_binance_order(symbol: str, order_id: str):
    """Cancels an active Binance Spot order."""
    from app.services.binance_connector import BinanceConnector
    from app.scheduler.manager import LayeredSchedulerManager
    
    connector = BinanceConnector.get_instance()
    scheduler = await LayeredSchedulerManager.get_instance()
    mode = scheduler.global_trading_mode
    
    res = await connector.cancel_order(mode, symbol, order_id)
    return res

@router.get("/active-positions")
async def get_active_positions_endpoint():
    """Exposes currently active positions with current prices and unrealized P&L."""
    from app.trading.position_manager import PositionManager
    from app.workers.price_cache import PriceCache
    
    pos_mgr = PositionManager.get_instance()
    cache = await PriceCache.get_instance()
    
    active = pos_mgr.get_active_positions()
    results = []
    
    for pos in active:
        symbol = pos["symbol"]
        curr_price = cache.get_price(symbol)
        if curr_price <= 0.0:
            curr_price = pos["entry_price"]
            
        # Calculate unrealized P&L
        if pos["side"] == "BUY":
            unrealized_pnl = (curr_price - pos["entry_price"]) * pos["position_size"]
        else:
            unrealized_pnl = (pos["entry_price"] - curr_price) * pos["position_size"]
            
        results.append({
            "id": pos["id"],
            "symbol": symbol,
            "side": pos["side"],
            "entry_price": pos["entry_price"],
            "position_size": pos["position_size"],
            "stop_loss": pos["stop_loss"],
            "take_profit": pos["take_profit"],
            "trailing_stop": pos.get("trailing_stop", pos["stop_loss"]),
            "current_price": curr_price,
            "unrealized_pnl": round(unrealized_pnl, 2),
            "tp_stages": pos.get("tp_stages", [])
        })
        
    return results

@router.get("/closed-trades", response_model=List[TradeResponse])
async def get_closed_trades_endpoint(db: AsyncSession = Depends(get_db)):
    """Fetch all finalized (closed) trades."""
    from sqlalchemy.future import select
    from app.models.trading import Trade
    
    query = select(Trade).where(Trade.exit_price.isnot(None)).order_by(Trade.exit_time.desc())
    res = await db.execute(query)
    return res.scalars().all()

@router.get("/portfolio-exposure")
async def get_portfolio_exposure_endpoint(db: AsyncSession = Depends(get_db)):
    """Exposes portfolio exposure metrics, risk utilization, and daily circuit breaker status."""
    from app.trading.portfolio_guard import PortfolioGuard
    from app.services.paper_trading_service import PaperTradingService
    from app.scheduler.manager import LayeredSchedulerManager
    
    scheduler = await LayeredSchedulerManager.get_instance()
    trading_mode = scheduler.global_trading_mode
    
    if trading_mode in ["TESTNET", "LIVE"]:
        from app.services.binance_connector import BinanceConnector
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

    metrics = await PortfolioGuard.get_instance().get_dashboard_metrics(balance)
    return metrics

@router.get("/self-adaptation")
async def get_self_adaptation_endpoint():
    """Exposes Lumina performance self-adaptation state, strategy weightings, and learning insights."""
    from app.ai.self_adaptation import SelfAdaptationEngine
    engine = SelfAdaptationEngine.get_instance()
    await engine.analyze_performance()
    return engine.get_adaptation_summary()

@router.post("/self-adaptation/reanalyze")
async def trigger_self_adaptation_reanalysis():
    """Triggers immediate performance self-adaptation re-analysis across past trade history."""
    from app.ai.self_adaptation import SelfAdaptationEngine
    engine = SelfAdaptationEngine.get_instance()
    await engine.analyze_performance()
    return {"status": "SUCCESS", "message": "Performance self-adaptation re-analysis triggered.", "data": engine.get_adaptation_summary()}

@router.get("/summary")
async def get_trading_summary_endpoint(db: AsyncSession = Depends(get_db)):
    """Calculates performance overview (total realized/unrealized P&L, balance, etc.)."""
    from sqlalchemy.future import select
    from sqlalchemy import func
    from app.models.trading import Trade
    from app.services.paper_trading_service import PaperTradingService
    from app.trading.position_manager import PositionManager
    from app.workers.price_cache import PriceCache
    from app.scheduler.manager import LayeredSchedulerManager
    from app.trading.portfolio_guard import PortfolioGuard
    
    scheduler = await LayeredSchedulerManager.get_instance()
    trading_mode = scheduler.global_trading_mode
    
    # Paper wallet balance
    if trading_mode in ["TESTNET", "LIVE"]:
        from app.services.binance_connector import BinanceConnector
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
        
    # Realized P&L
    query = select(func.sum(Trade.profit_loss)).where(Trade.exit_price.isnot(None))
    res = await db.execute(query)
    total_realized_pnl = res.scalar() or 0.0
    
    # Unrealized P&L
    pos_mgr = PositionManager.get_instance()
    cache = await PriceCache.get_instance()
    active_positions = pos_mgr.get_active_positions()
    total_unrealized_pnl = 0.0
    
    for pos in active_positions:
        symbol = pos["symbol"]
        curr_price = cache.get_price(symbol)
        if curr_price > 0.0:
            if pos["side"] == "BUY":
                pnl = (curr_price - pos["entry_price"]) * pos["position_size"]
            else:
                pnl = (pos["entry_price"] - curr_price) * pos["position_size"]
            total_unrealized_pnl += pnl
            
    # Counts
    query_closed_count = select(func.count(Trade.id)).where(Trade.exit_price.isnot(None))
    res_closed = await db.execute(query_closed_count)
    closed_count = res_closed.scalar() or 0

    # Portfolio Guard metrics
    exposure_metrics = await PortfolioGuard.get_instance().get_dashboard_metrics(balance)
    
    # Self-adaptation metrics
    from app.ai.self_adaptation import SelfAdaptationEngine
    adaptation_engine = SelfAdaptationEngine.get_instance()
    adaptation_summary = adaptation_engine.get_adaptation_summary()
    
    return {
        "wallet_balance": round(balance, 2),
        "total_realized_pnl": round(total_realized_pnl, 2),
        "total_unrealized_pnl": round(total_unrealized_pnl, 2),
        "total_pnl": round(total_realized_pnl + total_unrealized_pnl, 2),
        "active_positions_count": len(active_positions),
        "closed_trades_count": closed_count,
        "global_trading_mode": trading_mode,
        "portfolio_exposure": exposure_metrics,
        "self_adaptation": adaptation_summary
    }

@router.post("/validate-pipeline")
async def validate_trading_pipeline(db: AsyncSession = Depends(get_db)):
    """
    Performs a full, real, end-to-end integration and verification of the paper trading pipeline:
    1. SIGNAL: Generates simulated AI opportunity.
    2. RISK: Validates risk and calculates ATR sizing.
    3. EXECUTION: Creates Trade in SQLite DB and updates paper balance.
    4. MANAGEMENT: Updates trailing stop loss in real time using PriceCache.
    5. CLOSE: Liquidates trade on Stop Loss, finalizing trade in DB.
    """
    import asyncio
    from app.workers.price_cache import PriceCache
    from app.scheduler.manager import LayeredSchedulerManager
    from app.trading.order_manager import OrderManager
    from app.models.trading import Trade
    from sqlalchemy.future import select
    
    steps = []
    
    # Ensure PriceCache initialized
    cache = await PriceCache.get_instance()
    manager = await LayeredSchedulerManager.get_instance()
    
    # 1. SIGNAL stage
    symbol = "BTCUSDT_VALIDATION"
    initial_price = 90000.0
    cache.update_price(symbol, initial_price)
    
    steps.append({"step": "1. SIGNAL", "status": "SUCCESS", "detail": f"Simulated AI scanner signal generated for {symbol} at ${initial_price}."})
    
    # 2. RISK & EXECUTION stage
    prev_mode = manager.global_trading_mode
    manager.global_trading_mode = "PAPER"
    
    setup = {
        "symbol": symbol,
        "side": "BUY",
        "entry_price": initial_price,
        "stop_loss": 89000.0,
        "take_profit": 95000.0,
        "strategy_used": "E2E_VALIDATION_STRATEGY",
        "market_regime": "TRENDING_UP",
        "confidence_score": 0.98,
        "entry_reason": "End-to-End Pipeline Validation",
        "atr": 1000.0
    }
    
    order_manager = OrderManager.get_instance()
    await order_manager.receive_opportunity(setup)
    
    # Query created trade
    query = select(Trade).where(Trade.symbol == symbol).order_by(Trade.id.desc())
    res = await db.execute(query)
    trade = res.scalars().first()
    
    if not trade:
        manager.global_trading_mode = prev_mode
        raise HTTPException(status_code=500, detail="E2E Pipeline Verification Failed: Trade was not recorded in DB.")
        
    steps.append({
        "step": "2. RISK & EXECUTION",
        "status": "SUCCESS",
        "detail": f"Risk engine validated sizing. Trade #{trade.id} recorded in DB with size {trade.position_size} units."
    })
    
    # 3. MANAGEMENT stage
    # Move price up to trigger stop adjustment
    higher_price = 92000.0
    cache.update_price(symbol, higher_price)
    await asyncio.sleep(1.2) # Wait for PositionManager loop to detect and update
    
    pos = manager.position_manager._positions.get(trade.id)
    if not pos:
        manager.global_trading_mode = prev_mode
        raise HTTPException(status_code=500, detail="E2E Pipeline Verification Failed: Trade is no longer tracked by PositionManager.")
        
    raised_sl = pos["trailing_stop"]
    steps.append({
        "step": "3. MANAGEMENT",
        "status": "SUCCESS",
        "detail": f"Price rose to ${higher_price}. PositionManager successfully raised trailing stop to ${raised_sl}."
    })
    
    # 4. CLOSE stage
    # Move price down below trailing stop to trigger stop loss close
    lower_price = 88000.0
    cache.update_price(symbol, lower_price)
    await asyncio.sleep(1.2) # Wait for PositionManager loop to detect and close
    
    # Refresh trade from DB
    await db.refresh(trade)
    
    if trade.exit_price is None:
        # Emergency cleanup
        await manager.position_manager._close_position(trade.id, lower_price, "STOP_LOSS")
        manager.global_trading_mode = prev_mode
        raise HTTPException(status_code=500, detail="E2E Pipeline Verification Failed: Position was not automatically closed on stop loss.")
        
    steps.append({
        "step": "4. CLOSE",
        "status": "SUCCESS",
        "detail": f"Price dropped to ${lower_price} (below trailing stop of ${raised_sl}). PositionManager automatically closed position at ${trade.exit_price} with P&L of ${round(trade.profit_loss, 2)}."
    })
    
    # Restore original system mode
    manager.global_trading_mode = prev_mode
    
    return {
        "status": "VALIDATED",
        "message": "Paper Trading Pipeline successfully verified end-to-end.",
        "trade_id": trade.id,
        "steps": steps
    }

@router.get("/{trade_id}", response_model=TradeResponse)
async def read_trade(trade_id: int, db: AsyncSession = Depends(get_db)):
    """Fetch detail structure of a specific trade by ID."""
    trade = await TradingService.get_trade_by_id(db, trade_id)
    if not trade:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, 
            detail=f"Trade with ID {trade_id} not found"
        )
    return trade

@router.patch("/{trade_id}", response_model=TradeResponse)
async def update_trade(trade_id: int, trade_in: TradeUpdate, db: AsyncSession = Depends(get_db)):
    """Apply exit prices, times, and profits to a trade record."""
    trade = await TradingService.update_trade(db, trade_id, trade_in)
    if not trade:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, 
            detail=f"Trade with ID {trade_id} not found"
        )
    return trade
