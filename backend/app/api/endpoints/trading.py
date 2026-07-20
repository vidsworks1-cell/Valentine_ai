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

@router.post("", response_model=TradeResponse, status_code=status.HTTP_201_CREATED)
async def create_trade(trade_in: TradeCreate, db: AsyncSession = Depends(get_db)):
    """Record a newly initiated paper or live trade."""
    return await TradingService.create_trade(db, trade_in)

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
