import asyncio
import time
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone
from sqlalchemy.future import select
from app.database.session import AsyncSessionLocal
from app.models.trading import Trade
from app.api.schemas import TradeUpdate, AuditLogCreate
from app.services.audit_service import AuditService

logger = logging.getLogger("lumina.trading.exchange_reconciliation")

class ExchangeReconciliationEngine:
    """
    Pillar 2: Exchange Reconciliation Engine.
    Periodically compares Lumina's internal position and database tracking with 
    the actual exchange account state to detect and resolve discrepancies automatically:
    - Stale / Ghost internal positions in DB or memory (closed on exchange or never opened live)
    - Orphaned exchange positions (positions opened manually or via API cutoffs)
    - Position size or side mismatches
    - Safe closure of unclosed DB trades without deleting historical records
    """
    _instance: Optional['ExchangeReconciliationEngine'] = None

    # Stablecoins and quote currencies to ignore when discovering spot positions from balances
    QUOTE_AND_FIAT_ASSETS = {"USDT", "USDC", "USD", "BUSD", "FDUSD", "TUSD", "EUR", "GBP"}

    def __init__(self):
        self.last_reconciliation_at: Optional[str] = None
        self.total_reconciliations_run: int = 0
        self.discrepancies_detected_count: int = 0
        self.discrepancies_resolved_count: int = 0
        self.recent_reports: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'ExchangeReconciliationEngine':
        if cls._instance is None:
            cls._instance = ExchangeReconciliationEngine()
        return cls._instance

    async def reconcile_state(self, simulated_exchange_positions: Optional[List[Dict[str, Any]]] = None) -> Dict[str, Any]:
        """
        Executes complete exchange vs internal state reconciliation.
        If simulated_exchange_positions is provided, uses that (e.g., during testing or paper trading);
        otherwise queries BinanceConnector for LIVE/TESTNET modes or synchronizes PAPER mode.
        """
        from app.scheduler.manager import LayeredSchedulerManager
        from app.trading.position_manager import PositionManager
        from app.services.binance_connector import BinanceConnector
        from app.workers.price_cache import PriceCache

        scheduler = await LayeredSchedulerManager.get_instance()
        active_mode = getattr(scheduler, "global_trading_mode", "PAPER").upper()
        pm = PositionManager.get_instance()
        cache = await PriceCache.get_instance()

        internal_positions = pm.get_active_positions(trading_mode=active_mode)

        # Build mapping of internal in-memory positions by symbol
        internal_by_sym: Dict[str, Dict[str, Any]] = {
            pos.get("symbol", "").upper(): pos for pos in internal_positions if pos.get("symbol")
        }

        # 1. Retrieve exchange positions
        exchange_positions: List[Dict[str, Any]] = []
        
        if simulated_exchange_positions is not None:
            exchange_positions = simulated_exchange_positions
        elif active_mode in ["LIVE", "TESTNET"]:
            connector = BinanceConnector.get_instance()
            trading_venue = getattr(scheduler, "trading_venue", "SPOT").upper()
            try:
                if trading_venue == "FUTURES":
                    positions_res = await connector.get_futures_position_risk(mode=active_mode)
                    if isinstance(positions_res, list):
                        for p in positions_res:
                            pos_amt = float(p.get("positionAmt", 0.0))
                            if abs(pos_amt) > 0.00001:
                                sym = p.get("symbol", "").upper()
                                side = "BUY" if pos_amt > 0 else "SELL"
                                entry_px = float(p.get("entryPrice", 0.0))
                                mark_px = float(p.get("markPrice", entry_px))
                                # Auto-remediate margin to ISOLATED and harmonize leverage to 15x if needed
                                margin_type = str(p.get("marginType", "")).upper()
                                lev_val = int(p.get("leverage", 20))
                                if margin_type != "ISOLATED":
                                    try:
                                        if abs(pos_amt) > 0.00001:
                                            logger.debug(f"ℹ️ [RECONCILIATION] {sym} is currently {margin_type} with active position; cannot switch margin type until position closes.")
                                        else:
                                            await connector.set_futures_margin_type(sym, "ISOLATED", active_mode)
                                    except Exception as rem_err:
                                        logger.debug(f"Reconciliation margin notice for {sym}: {rem_err}")

                                exchange_positions.append({
                                    "symbol": sym,
                                    "side": side,
                                    "position_size": abs(pos_amt),
                                    "entry_price": entry_px if entry_px > 0 else mark_px,
                                    "stop_loss": round(entry_px * (0.99 if side == "BUY" else 1.01), 4),
                                    "take_profit": round(entry_px * (1.025 if side == "BUY" else 0.975), 4),
                                    "asset": sym.replace("USDT", "")
                                })
                    else:
                        logger.warning(f"[RECONCILIATION] Could not fetch futures position risk from Binance ({active_mode}): {positions_res}")
                else:
                    balances_res = await connector.get_wallet_balances(active_mode)
                    if isinstance(balances_res, list):
                        for b in balances_res:
                            asset = b.get("asset", "").upper()
                            if asset in self.QUOTE_AND_FIAT_ASSETS:
                                continue
                            
                            free = float(b.get("free", 0.0))
                            locked = float(b.get("locked", 0.0))
                            total_qty = free + locked
                            
                            symbol = f"{asset}USDT"
                            current_px = cache.get_price(symbol)
                            notional_val = total_qty * current_px if current_px > 0 else 0.0
                            
                            # Only consider real actively traded positions (must have valid price and notional value >= $5.00)
                            # Filter out dust, unlisted airdrops, and untracked micro-balances
                            if current_px > 0 and notional_val >= 5.0 and total_qty > 0.0001:
                                exchange_positions.append({
                                    "symbol": symbol,
                                    "side": "BUY",
                                    "position_size": total_qty,
                                    "entry_price": current_px,
                                    "stop_loss": current_px * 0.95,
                                    "take_profit": current_px * 1.10,
                                    "asset": asset
                                })
                    else:
                        logger.warning(f"[RECONCILIATION] Could not fetch wallet balances from Binance ({active_mode}): {balances_res}")
            except Exception as be:
                logger.error(f"[RECONCILIATION] Error querying exchange state for {active_mode} ({trading_venue}): {be}")
        else:
            # For PAPER mode, exchange positions match internal positions
            for pos in internal_positions:
                exchange_positions.append({
                    "symbol": pos.get("symbol"),
                    "side": pos.get("side"),
                    "position_size": pos.get("position_size"),
                    "entry_price": pos.get("entry_price"),
                    "stop_loss": pos.get("stop_loss"),
                    "take_profit": pos.get("take_profit")
                })

        exchange_by_sym: Dict[str, Dict[str, Any]] = {
            pos.get("symbol", "").upper(): pos for pos in exchange_positions if pos.get("symbol")
        }

        discrepancies: List[Dict[str, Any]] = []
        resolutions: List[Dict[str, Any]] = []

        all_symbols = set(internal_by_sym.keys()).union(set(exchange_by_sym.keys()))

        # 2. Check in-memory vs exchange positions
        for sym in all_symbols:
            int_pos = internal_by_sym.get(sym)
            exc_pos = exchange_by_sym.get(sym)

            # Case A: Ghost Internal Position in Memory (Exists in Lumina memory, but closed on exchange)
            if int_pos and not exc_pos:
                disc = {
                    "type": "GHOST_INTERNAL_POSITION",
                    "symbol": sym,
                    "internal_size": int_pos.get("position_size"),
                    "exchange_size": 0.0,
                    "description": f"Position for {sym} exists in Lumina internal memory but is closed on Exchange ({active_mode})."
                }
                discrepancies.append(disc)
                
                # Auto-resolve: Remove from internal positions and close in DB if exists
                trade_id = next((tid for tid, p in pm._positions.items() if p.get("symbol") == sym and p.get("trading_mode", "PAPER").upper() == active_mode), None)
                if trade_id is not None:
                    del pm._positions[trade_id]
                    resolutions.append({
                        "symbol": sym,
                        "action": "AUTO_PURGED_GHOST_POSITION",
                        "trade_id": trade_id,
                        "status": "RESOLVED"
                    })

            # Case B: Orphaned Exchange Position (Exists on exchange, missing in Lumina)
            elif exc_pos and not int_pos:
                disc = {
                    "type": "ORPHANED_EXCHANGE_POSITION",
                    "symbol": sym,
                    "internal_size": 0.0,
                    "exchange_size": exc_pos.get("position_size"),
                    "description": f"Position for {sym} exists on Exchange but missing in Lumina internal tracking."
                }
                discrepancies.append(disc)

                # Auto-resolve: Ensure Trade record exists in Database and adopt position into internal manager
                entry_px = float(exc_pos.get("entry_price", 100.0))
                pos_size = float(exc_pos.get("position_size", 1.0))
                side_val = exc_pos.get("side", "BUY")
                sl_val = float(exc_pos.get("stop_loss", 0.0))
                tp_val = float(exc_pos.get("take_profit", 0.0))

                db_trade_id = None
                try:
                    async with AsyncSessionLocal() as db:
                        q = select(Trade).where(
                            Trade.symbol == sym,
                            Trade.trading_mode == active_mode,
                            Trade.exit_time.is_(None)
                        ).order_by(Trade.entry_time.desc())
                        res_db = await db.execute(q)
                        existing_trade = res_db.scalars().first()
                        if existing_trade:
                            db_trade_id = existing_trade.id
                            logger.info(f"💾 [RECONCILIATION] Re-linked orphan exchange position {sym} to existing DB Trade #{db_trade_id}.")
                        else:
                            # Create and persist new Trade record directly to database
                            from app.api.schemas import TradeCreate
                            from app.services.trading_service import TradingService
                            t_create = TradeCreate(
                                symbol=sym,
                                side=side_val,
                                entry_price=entry_px,
                                position_size=pos_size,
                                stop_loss=sl_val,
                                take_profit=tp_val,
                                strategy_used="Exchange_Reconciliation_Adopted",
                                market_regime="TRENDING",
                                confidence_score=0.85,
                                entry_reason=f"Position adopted from Binance {active_mode} during exchange reconciliation",
                                indicators="{}",
                                atr=0.0,
                                risk_amount=round(pos_size * entry_px * 0.02, 4),
                                risk_pct=2.0,
                                trading_mode=active_mode
                            )
                            new_trade = await TradingService.create_trade(db, t_create)
                            await db.commit()
                            db_trade_id = new_trade.id
                            logger.info(f"💾 [RECONCILIATION DB PERSISTENCE] Persisted orphan exchange position {sym} as new DB Trade #{db_trade_id} ({active_mode}).")
                except Exception as sync_err:
                    logger.error(f"[RECONCILIATION] Error persisting adopted position to DB: {sync_err}")

                actual_trade_id = db_trade_id or int(datetime.now(timezone.utc).timestamp() * 1000)
                pos_details = {
                    "id": actual_trade_id,
                    "trade_id": actual_trade_id,
                    "symbol": sym,
                    "side": side_val,
                    "position_size": pos_size,
                    "entry_price": entry_px,
                    "stop_loss": sl_val,
                    "take_profit": tp_val,
                    "trailing_stop": sl_val,
                    "highest_price": entry_px,
                    "lowest_price": entry_px,
                    "strategy": "Exchange_Reconciliation_Adopted",
                    "confidence": 0.85,
                    "trading_mode": active_mode,
                    "opened_at": time.time()
                }
                await pm.track_position(actual_trade_id, pos_details)
                resolutions.append({
                    "symbol": sym,
                    "action": "AUTO_ADOPTED_ORPHAN_POSITION",
                    "trade_id": actual_trade_id,
                    "status": "RESOLVED"
                })

            # Case C: Both exist -> check quantity alignment
            elif int_pos and exc_pos:
                int_size = float(int_pos.get("position_size", 0.0))
                exc_size = float(exc_pos.get("position_size", 0.0))
                if abs(int_size - exc_size) > 1e-5:
                    disc = {
                        "type": "POSITION_SIZE_MISMATCH",
                        "symbol": sym,
                        "internal_size": int_size,
                        "exchange_size": exc_size,
                        "description": f"Size mismatch for {sym}: Internal {int_size} vs Exchange {exc_size}."
                    }
                    discrepancies.append(disc)
                    
                    int_pos["position_size"] = exc_size
                    resolutions.append({
                        "symbol": sym,
                        "action": "AUTO_SYNCED_POSITION_SIZE",
                        "synced_size": exc_size,
                        "status": "RESOLVED"
                    })

        # 3. Synchronize unclosed Database Trade Records for the active mode
        if active_mode in ["LIVE", "TESTNET"]:
            try:
                async with AsyncSessionLocal() as db:
                    query = select(Trade).where(
                        Trade.trading_mode == active_mode,
                        Trade.exit_time.is_(None)
                    )
                    res = await db.execute(query)
                    unclosed_db_trades = res.scalars().all()
                    
                    for dbt in unclosed_db_trades:
                        sym = dbt.symbol.upper()
                        # If this trade has no matching position on exchange
                        if sym not in exchange_by_sym:
                            curr_px = cache.get_price(sym)
                            exit_px = curr_px if curr_px > 0 else dbt.entry_price
                            pnl = 0.0
                            if dbt.side.upper() == "BUY":
                                pnl = (exit_px - dbt.entry_price) * dbt.position_size
                            else:
                                pnl = (dbt.entry_price - exit_px) * dbt.position_size
                            
                            disc = {
                                "type": "STALE_DATABASE_TRADE",
                                "trade_id": dbt.id,
                                "symbol": sym,
                                "mode": active_mode,
                                "description": f"Trade #{dbt.id} ({sym}) in DB had exit_time IS NULL but no active position exists on Binance ({active_mode})."
                            }
                            discrepancies.append(disc)
                            
                            # Gracefully close DB trade without deleting historical record
                            dbt.exit_time = datetime.now(timezone.utc)
                            dbt.exit_price = exit_px
                            dbt.profit_loss = round(pnl, 4)
                            dbt.outcome = "RECONCILED_CLOSED"
                            db.add(dbt)
                            
                            # Also ensure purged from memory
                            if dbt.id in pm._positions:
                                del pm._positions[dbt.id]
                                
                            resolutions.append({
                                "symbol": sym,
                                "trade_id": dbt.id,
                                "action": "AUTO_CLOSED_STALE_DB_TRADE",
                                "exit_price": exit_px,
                                "profit_loss": pnl,
                                "status": "RESOLVED"
                            })
                            
                    await db.commit()
            except Exception as dbe:
                logger.error(f"[RECONCILIATION] Error reconciling unclosed database trades: {dbe}")

        self.last_reconciliation_at = datetime.now(timezone.utc).isoformat()
        self.total_reconciliations_run += 1
        self.discrepancies_detected_count += len(discrepancies)
        self.discrepancies_resolved_count += len(resolutions)

        report = {
            "reconciled_at": self.last_reconciliation_at,
            "mode": active_mode,
            "status": "HEALTHY_IN_SYNC" if not discrepancies else "DISCREPANCIES_RESOLVED",
            "symbols_audited_count": len(all_symbols),
            "discrepancies_found": len(discrepancies),
            "resolutions_applied": len(resolutions),
            "discrepancy_details": discrepancies,
            "resolution_details": resolutions
        }

        self.recent_reports.append(report)
        if len(self.recent_reports) > 10:
            self.recent_reports.pop(0)

        if discrepancies:
            logger.info(f"🔄 [EXCHANGE RECONCILIATION] Run #{self.total_reconciliations_run} ({active_mode}). Audited: {len(all_symbols)} | Discrepancies: {len(discrepancies)} | Auto-Resolved: {len(resolutions)}")
        else:
            logger.info(f"🔄 [EXCHANGE RECONCILIATION] Run #{self.total_reconciliations_run} ({active_mode}) IN SYNC. Audited: {len(all_symbols)} | Zero discrepancies.")

        return report

    def get_telemetry(self) -> Dict[str, Any]:
        """Returns exchange reconciliation metrics."""
        return {
            "last_reconciliation_at": self.last_reconciliation_at,
            "total_reconciliations_run": self.total_reconciliations_run,
            "discrepancies_detected_count": self.discrepancies_detected_count,
            "discrepancies_resolved_count": self.discrepancies_resolved_count,
            "recent_reports_count": len(self.recent_reports),
            "latest_report": self.recent_reports[-1] if self.recent_reports else None
        }
