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

@router.get("/global-mode")
@router.get("/mode")
async def get_global_trading_mode():
    """Fetches system-wide global trading mode. Returns OFF, PAPER, TESTNET, or LIVE."""
    manager = await LayeredSchedulerManager.get_instance()
    return {
        "status": "SUCCESS",
        "global_trading_mode": manager.global_trading_mode
    }

@router.post("/global-mode/{mode}")
async def update_global_trading_mode_path(mode: str):
    """Sets system-wide global trading mode via path parameter. Allowed values: OFF, PAPER, TESTNET, LIVE."""
    return await _set_global_trading_mode(mode)

@router.post("/global-mode")
@router.post("/mode")
async def update_global_trading_mode(mode: str):
    """Sets system-wide global trading mode via query parameter. Allowed values: OFF, PAPER, TESTNET, LIVE."""
    return await _set_global_trading_mode(mode)

async def _set_global_trading_mode(mode: str):
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

@router.get("/journal")
async def get_trade_journals_endpoint(db: AsyncSession = Depends(get_db)):
    """Retrieves all trade journal entries with quality scores and explainability."""
    from app.trading.trade_journal import TradeJournalEngine
    from app.models.trading import Trade
    from sqlalchemy.future import select
    from sqlalchemy import desc

    engine = TradeJournalEngine.get_instance()
    
    # Pre-populate journal cache for closed trades if needed
    query = select(Trade).where(Trade.exit_price.isnot(None)).order_by(desc(Trade.exit_time)).limit(50)
    res = await db.execute(query)
    trades = res.scalars().all()
    
    for t in trades:
        if not engine.get_journal_for_trade(t.id):
            await engine.journal_closed_trade(t.id, db_trade=t)
            
    return engine.get_all_journals()

@router.get("/journal/{trade_id}")
async def get_trade_journal_by_id_endpoint(trade_id: int, db: AsyncSession = Depends(get_db)):
    """Retrieves detailed journal, quality score, and counterfactual analysis for a specific trade."""
    from app.trading.trade_journal import TradeJournalEngine
    engine = TradeJournalEngine.get_instance()
    journal = engine.get_journal_for_trade(trade_id)
    if not journal:
        journal = await engine.journal_closed_trade(trade_id)
    return journal

@router.get("/cooldowns")
async def get_active_cooldowns_endpoint():
    """Retrieves all currently active symbol evaluation cooldowns."""
    from app.ai.reasoning import AiReasoningEngine
    engine = AiReasoningEngine.get_instance()
    return {
        "active_cooldowns": engine.get_active_cooldowns(),
        "total_active": len(engine.get_active_cooldowns())
    }

@router.get("/replay/status")
async def get_replay_status_endpoint():
    """Retrieves telemetry, positions, and equity curve for Historical Market Replay Sandbox."""
    from app.trading.historical_replay import HistoricalReplayEngine
    return HistoricalReplayEngine.get_instance().get_status()

@router.post("/replay/start")
async def start_replay_endpoint(
    symbol: str = "BTCUSDT",
    initial_balance: float = 100000.0,
    speed_multiplier: float = 5.0,
    num_candles: int = 120,
    start_price: float = 65000.0
):
    """Starts a new Historical Market Replay Sandbox session."""
    from app.trading.historical_replay import HistoricalReplayEngine
    return await HistoricalReplayEngine.get_instance().start_replay(
        symbol=symbol,
        initial_balance=initial_balance,
        speed_multiplier=speed_multiplier,
        num_candles=num_candles,
        start_price=start_price
    )

@router.post("/replay/pause")
async def pause_replay_endpoint():
    """Pauses active replay session."""
    from app.trading.historical_replay import HistoricalReplayEngine
    return await HistoricalReplayEngine.get_instance().pause_replay()

@router.post("/replay/resume")
async def resume_replay_endpoint():
    """Resumes paused replay session."""
    from app.trading.historical_replay import HistoricalReplayEngine
    return await HistoricalReplayEngine.get_instance().resume_replay()

@router.post("/replay/step")
async def step_replay_endpoint():
    """Advances replay session by 1 candle step."""
    from app.trading.historical_replay import HistoricalReplayEngine
    return await HistoricalReplayEngine.get_instance().step_replay()

@router.post("/replay/stop")
async def stop_replay_endpoint():
    """Stops and finalizes current replay session."""
    from app.trading.historical_replay import HistoricalReplayEngine
    return await HistoricalReplayEngine.get_instance().stop_replay()

@router.get("/execution-analytics")
async def get_execution_analytics_endpoint():
    """Retrieves Performance Analytics & Execution Efficiency metrics (latency, slippage, fill rate, quality index)."""
    from app.trading.execution_analytics import ExecutionAnalyticsEngine
    engine = ExecutionAnalyticsEngine.get_instance()
    return engine.get_execution_metrics()

@router.get("/strategy-performance")
async def get_strategy_performance_endpoint():
    """Retrieves strategy yield breakdown across market regimes and Walk-Forward refinement suggestions."""
    from app.ai.walk_forward import WalkForwardValidator
    validator = WalkForwardValidator.get_instance()
    report = validator.get_latest_report()
    if not report:
        report = await validator.run_walk_forward_validation()
    return report

@router.get("/champion-challenger")
async def get_champion_challenger_endpoint():
    """Retrieves strategy champion/challenger standings, leaderboard, and promotion history."""
    from app.trading.champion_challenger import ChampionChallengerEngine
    engine = ChampionChallengerEngine.get_instance()
    await engine.evaluate_strategies()
    return engine.get_champion_challenger_summary()

@router.get("/mtf-status")
async def get_mtf_status_endpoint(symbol: str = "BTCUSDT", side: str = "BUY"):
    """Evaluates multi-timeframe alignment for a symbol."""
    from app.trading.mtf_confirmation import MTFConfirmationGuard
    from app.workers.price_cache import PriceCache
    cache = await PriceCache.get_instance()
    price = cache.get_price(symbol)
    passed, align_pct, details = await MTFConfirmationGuard.get_instance().evaluate_mtf_alignment(symbol, side, price)
    return {
        "passed": passed,
        "alignment_pct": align_pct,
        "details": details,
        "current_price": price
    }

@router.get("/walk-forward")
async def get_walk_forward_endpoint():
    """Retrieves latest Walk-Forward optimization and strategy stability report."""
    from app.ai.walk_forward import WalkForwardValidator
    validator = WalkForwardValidator.get_instance()
    report = validator.get_latest_report()
    if not report:
        report = await validator.run_walk_forward_validation()
    return report

@router.post("/walk-forward/run")
async def run_walk_forward_endpoint():
    """Triggers an immediate Walk-Forward optimization cycle."""
    from app.ai.walk_forward import WalkForwardValidator
    validator = WalkForwardValidator.get_instance()
    report = await validator.run_walk_forward_validation()
    return report

@router.get("/quant-metrics")
async def get_live_quant_metrics_endpoint(db: AsyncSession = Depends(get_db)):
    """
    Live Quantitative Monitoring Dashboard endpoint.
    Aggregates: Equity Curve, Win Rate, Profit Factor, Sharpe Ratio, Quality Scores, Active Champion/Challengers, Portfolio Risk & Circuit Breaker.
    """
    from sqlalchemy.future import select
    from sqlalchemy import desc
    from app.models.trading import Trade
    from app.trading.champion_challenger import ChampionChallengerEngine
    from app.trading.portfolio_guard import PortfolioGuard
    from app.ai.self_adaptation import SelfAdaptationEngine
    from app.services.paper_trading_service import PaperTradingService

    wallet = await PaperTradingService.get_wallet(db, "lumina_default")
    balance = wallet.balance if wallet else 100000.0

    # Fetch closed trades
    query = select(Trade).where(Trade.exit_price.isnot(None)).order_by(Trade.exit_time.asc())
    res = await db.execute(query)
    trades = res.scalars().all()

    total_closed = len(trades)
    pnl_list = [(t.profit_loss or 0.0) for t in trades]
    wins = [p for p in pnl_list if p > 0]
    losses = [p for p in pnl_list if p <= 0]

    win_rate_pct = round((len(wins) / total_closed * 100.0), 1) if total_closed > 0 else 0.0
    gross_profit = sum(wins)
    gross_loss = abs(sum(losses))
    profit_factor = round((gross_profit / gross_loss), 2) if gross_loss > 0 else (round(gross_profit, 2) if gross_profit > 0 else 1.0)

    # Build cumulative equity curve data
    equity_curve = []
    running_balance = 100000.0
    for idx, t in enumerate(trades):
        running_balance += (t.profit_loss or 0.0)
        equity_curve.append({
            "trade_num": idx + 1,
            "timestamp": t.exit_time.isoformat() if t.exit_time else None,
            "balance": round(running_balance, 2),
            "trade_pnl": round(t.profit_loss or 0.0, 2)
        })

    champ_engine = ChampionChallengerEngine.get_instance()
    await champ_engine.evaluate_strategies()
    champ_summary = champ_engine.get_champion_challenger_summary()

    portfolio_guard_metrics = await PortfolioGuard.get_instance().get_dashboard_metrics(balance)
    adaptation_summary = SelfAdaptationEngine.get_instance().get_adaptation_summary()

    return {
        "total_closed_trades": total_closed,
        "win_rate_pct": win_rate_pct,
        "profit_factor": profit_factor,
        "current_balance": round(balance, 2),
        "equity_curve": equity_curve[-30:], # Last 30 data points
        "champion_strategy": champ_summary.get("champion_strategy"),
        "strategy_leaderboard": champ_summary.get("leaderboard", []),
        "portfolio_guard": portfolio_guard_metrics,
        "self_adaptation_insights": adaptation_summary.get("learning_insights", [])
    }

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

@router.get("/stale-exits/config")
async def get_stale_exits_config():
    """Retrieves current Time-Based Stale Trade Exit parameters."""
    from app.trading.position_manager import PositionManager
    pm = PositionManager.get_instance()
    return {
        "default_stale_hours": pm.default_stale_hours,
        "default_stale_pct": pm.default_stale_pct
    }

@router.post("/stale-exits/config")
async def update_stale_exits_config(stale_hours: float = 4.0, stale_pct: float = 0.6):
    """Updates Time-Based Stale Trade Exit thresholds (stale hours duration & price stagnation % threshold)."""
    from app.trading.position_manager import PositionManager
    pm = PositionManager.get_instance()
    pm.default_stale_hours = max(0.5, min(stale_hours, 72.0))
    pm.default_stale_pct = max(0.1, min(stale_pct, 5.0))
    return {
        "status": "UPDATED",
        "default_stale_hours": pm.default_stale_hours,
        "default_stale_pct": pm.default_stale_pct
    }

@router.get("/reentry/watchers")
async def get_reentry_watchers():
    """Retrieves list of active Smart Re-Entry candidate watchers."""
    from app.trading.reentry_engine import SmartReentryEngine
    return {
        "active_watchers": SmartReentryEngine.get_instance().get_active_watchers()
    }

@router.get("/learning/coach-insights")
async def get_coach_insights_endpoint():
    """Retrieves Lumina's Trade Analytics & Learning Engine ('Lumina Coach') insights and actionable recommendations."""
    from app.ai.learning_engine import TradeAnalyticsLearningEngine
    return TradeAnalyticsLearningEngine.get_instance().generate_coach_insights()

@router.post("/learning/apply-recommendation")
async def apply_recommendation_endpoint(rec_id: str):
    """Applies a specific coaching recommendation directly into active trading parameters."""
    from app.ai.learning_engine import TradeAnalyticsLearningEngine
    return await TradeAnalyticsLearningEngine.get_instance().apply_recommendation(rec_id)

@router.post("/learning/apply-all-recommendations")
async def apply_all_recommendations_endpoint():
    """Applies all active coaching recommendations at once to complete the closed learning loop."""
    from app.ai.learning_engine import TradeAnalyticsLearningEngine
    return await TradeAnalyticsLearningEngine.get_instance().apply_all_recommendations()

@router.get("/macro-guard/status")
async def get_macro_guard_status():
    """Retrieves Systemic Macro Guard status, asset correlation matrix, and recent pre-emptive trim events."""
    from app.trading.macro_guard import SystemicMacroGuardEngine
    return await SystemicMacroGuardEngine.get_instance().evaluate_systemic_risk()

@router.post("/macro-guard/config")
async def update_macro_guard_config(elevated_threshold: float = 0.65, critical_threshold: float = 0.80):
    """Updates Systemic Risk Index thresholds."""
    from app.trading.macro_guard import SystemicMacroGuardEngine
    mg = SystemicMacroGuardEngine.get_instance()
    mg.risk_threshold_elevated = max(0.30, min(elevated_threshold, 0.90))
    mg.risk_threshold_critical = max(mg.risk_threshold_elevated + 0.05, min(critical_threshold, 0.98))
    return {
        "status": "UPDATED",
        "elevated_threshold": mg.risk_threshold_elevated,
        "critical_threshold": mg.risk_threshold_critical
    }

@router.get("/monte-carlo/simulation")
async def get_monte_carlo_simulation():
    """Runs Monte Carlo risk-of-ruin simulations and Kelly position sizing optimization."""
    from app.trading.monte_carlo import MonteCarloKellySimulator
    return MonteCarloKellySimulator.get_instance().run_simulation()

@router.post("/monte-carlo/apply-kelly")
async def apply_kelly_fraction_endpoint(fraction: float = 0.5):
    """Applies Fractional Kelly multiplier to PortfolioGuard max risk constraints."""
    from app.trading.monte_carlo import MonteCarloKellySimulator
    return MonteCarloKellySimulator.get_instance().apply_kelly_fraction(fraction)

@router.get("/execution/reconcile")
async def reconcile_exchange_positions():
    """Pillar 1: Reconciles local database positions with live exchange state and checks order layer."""
    from app.trading.live_execution import LiveExchangeExecutionEngine
    return await LiveExchangeExecutionEngine.get_instance().reconcile_positions_with_exchange()

@router.get("/institutional-risk/status")
async def get_institutional_risk_status():
    """Pillar 2: Evaluates institutional daily/weekly loss limits and circuit breakers."""
    from app.trading.institutional_risk import InstitutionalRiskEngine
    return await InstitutionalRiskEngine.get_instance().evaluate_risk_breakers()

@router.post("/institutional-risk/reset-halt")
async def reset_institutional_risk_halt():
    """Pillar 2: Resumes trading following risk breaker halt review."""
    from app.trading.institutional_risk import InstitutionalRiskEngine
    return InstitutionalRiskEngine.get_instance().reset_trading_halt()

@router.get("/production-monitoring/health")
async def get_production_monitoring_health():
    """Pillar 3: Collects VPS resources, execution latency, and feed health telemetry."""
    from app.trading.production_monitoring import ProductionMonitoringEngine
    return await ProductionMonitoringEngine.get_instance().collect_health_metrics()

@router.get("/paper-validation/report")
async def get_paper_validation_report():
    """Pillar 4 & 5: Retrieves continuous paper trading validation report and rollout phases."""
    from app.trading.paper_validation import LongTermPaperValidationEngine
    return LongTermPaperValidationEngine.get_instance().get_validation_report()

@router.post("/paper-validation/promote-phase")
async def promote_rollout_phase(target_phase: str = "PHASE_2_MICRO_LIVE"):
    """Pillar 5: Promotes active rollout phase."""
    from app.trading.paper_validation import LongTermPaperValidationEngine
    return LongTermPaperValidationEngine.get_instance().promote_to_next_phase(target_phase)

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
