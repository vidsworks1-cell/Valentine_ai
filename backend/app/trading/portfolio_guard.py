import logging
from datetime import datetime, timezone
from typing import Dict, Any, Tuple, Optional
from sqlalchemy.future import select
from sqlalchemy import func
from app.database.session import AsyncSessionLocal
from app.models.trading import Trade
from app.api.schemas import AuditLogCreate
from app.services.audit_service import AuditService

logger = logging.getLogger("lumina.trading.portfolio_guard")

class PortfolioGuard:
    """
    Portfolio Exposure Protection Layer for Lumina.
    Enforces account-wide limits before executing trades:
    - Maximum Open Positions (5)
    - Maximum Portfolio Risk (6%)
    - Maximum Symbol Exposure (2%)
    - Daily Loss Circuit Breaker (-5% PnL)
    """
    _instance: Optional['PortfolioGuard'] = None
    
    MAX_OPEN_POSITIONS = 5
    MAX_PORTFOLIO_RISK = 0.06   # 6% Portfolio Heat Limit
    MAX_SYMBOL_EXPOSURE = 0.02  # 2% Max Per-Symbol Exposure
    MAX_DAILY_LOSS = 0.05       # 5% Daily Loss Circuit Breaker
    MAX_CLUSTER_POSITIONS = 2   # Max 2 simultaneous positions per correlation cluster

    # Correlation Clusters Mapping
    CORRELATION_CLUSTERS = {
        "MAJOR_L1": ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT"]
    }
    
    def __init__(self):
        self._circuit_breaker_active = False
        self._circuit_breaker_date = None

    @classmethod
    def get_instance(cls) -> 'PortfolioGuard':
        if cls._instance is None:
            cls._instance = PortfolioGuard()
        return cls._instance

    def _check_circuit_breaker_reset(self):
        today = datetime.now(timezone.utc).date()
        if self._circuit_breaker_active and self._circuit_breaker_date:
            if today > self._circuit_breaker_date:
                logger.info(f"[DAILY LOSS CIRCUIT BREAKER] Resetting circuit breaker for new trading day ({today})")
                self._circuit_breaker_active = False
                self._circuit_breaker_date = None

    async def evaluate_trade(self, opportunity: Dict[str, Any], incoming_risk_pct: float = 0.02, balance: float = 100000.0) -> Tuple[bool, str]:
        """
        Evaluates portfolio-wide rules before executing a proposed trade.
        Returns (passed: bool, reason: str).
        Does NOT throw exceptions; returns gracefully.
        """
        try:
            self._check_circuit_breaker_reset()
            symbol = opportunity.get("symbol", "UNKNOWN")
            
            # 1. Daily Loss Protection Circuit Breaker Check
            today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
            daily_realized_pnl = 0.0
            
            async with AsyncSessionLocal() as db:
                try:
                    query = select(func.sum(Trade.profit_loss)).where(
                        Trade.exit_price.isnot(None),
                        Trade.exit_time >= today_start
                    )
                    res = await db.execute(query)
                    daily_realized_pnl = res.scalar() or 0.0
                except Exception as e:
                    logger.error(f"Error querying daily PnL: {e}")

            starting_daily_balance = balance - daily_realized_pnl if (balance - daily_realized_pnl) > 0 else balance
            daily_realized_pnl_pct = (daily_realized_pnl / starting_daily_balance) * 100.0 if starting_daily_balance > 0 else 0.0
            
            if daily_realized_pnl_pct <= -5.0 or self._circuit_breaker_active:
                if not self._circuit_breaker_active:
                    self._circuit_breaker_active = True
                    self._circuit_breaker_date = datetime.now(timezone.utc).date()
                
                log_msg = (
                    "[DAILY LOSS CIRCUIT BREAKER]\n"
                    "Daily loss limit reached.\n"
                    "Trading paused until next trading day."
                )
                logger.warning(log_msg)
                
                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message="[DAILY LOSS CIRCUIT BREAKER] Daily loss limit reached",
                            details="Daily loss exceeded 5%. Trading paused until next trading day."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to create audit log for daily loss circuit breaker: {ae}")
                
                return False, log_msg

            # 2. Maximum Open Positions Guard
            from app.trading.position_manager import PositionManager
            active_positions = PositionManager.get_instance().get_active_positions()
            open_count = len(active_positions)
            
            if open_count == 0:
                try:
                    async with AsyncSessionLocal() as db:
                        query = select(func.count(Trade.id)).where(Trade.exit_price.is_(None))
                        res = await db.execute(query)
                        open_count = res.scalar() or 0
                except Exception as e:
                    logger.error(f"Error querying open trade count: {e}")

            if open_count >= self.MAX_OPEN_POSITIONS:
                log_msg = (
                    "[PORTFOLIO GUARD]\n"
                    f"Maximum open positions reached ({self.MAX_OPEN_POSITIONS}).\n"
                    "Trade rejected."
                )
                logger.warning(log_msg)
                
                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"[PORTFOLIO GUARD] Maximum open positions reached ({self.MAX_OPEN_POSITIONS})",
                            details=f"Attempted to open trade for {symbol}, but max open positions ({self.MAX_OPEN_POSITIONS}) reached."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to create audit log for max open positions: {ae}")

                return False, log_msg

            # 3. Symbol Concentration Guard
            if incoming_risk_pct > self.MAX_SYMBOL_EXPOSURE + 0.0001:
                log_msg = (
                    "[PORTFOLIO GUARD]\n"
                    f"Symbol exposure limit exceeded for {symbol}.\n"
                    f"Current: {incoming_risk_pct * 100:.1f}%\n"
                    f"Maximum: {self.MAX_SYMBOL_EXPOSURE * 100:.0f}%\n"
                    "Trade rejected."
                )
                logger.warning(log_msg)
                
                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message=f"[PORTFOLIO GUARD] Symbol exposure limit exceeded for {symbol}",
                            details=f"Incoming risk {incoming_risk_pct * 100:.1f}% exceeds max symbol limit {self.MAX_SYMBOL_EXPOSURE * 100:.0f}%."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to create audit log for symbol concentration: {ae}")

                return False, log_msg

            # 3b. Correlation Guard (Sector / Cluster Limits)
            for cluster_name, cluster_symbols in self.CORRELATION_CLUSTERS.items():
                if symbol in cluster_symbols:
                    active_cluster_positions = [
                        p.get("symbol") for p in active_positions 
                        if p.get("symbol") in cluster_symbols
                    ]
                    if len(active_cluster_positions) >= self.MAX_CLUSTER_POSITIONS:
                        log_msg = (
                            f"[CORRELATION GUARD]\n"
                            f"Rejected {symbol} trade.\n"
                            f"Reason: Correlation cluster '{cluster_name}' already has {len(active_cluster_positions)} active position(s) "
                            f"({', '.join(active_cluster_positions)}). Limit: {self.MAX_CLUSTER_POSITIONS}."
                        )
                        logger.warning(log_msg)
                        try:
                            async with AsyncSessionLocal() as db:
                                await AuditService.create_audit_log(db, AuditLogCreate(
                                    level="WARNING",
                                    category="RISK",
                                    message=f"[CORRELATION GUARD] Cluster limit reached for {cluster_name}",
                                    details=log_msg
                                ))
                        except Exception as ae:
                            logger.error(f"Failed to log correlation guard audit event: {ae}")

                        return False, log_msg

            # 4. Maximum Portfolio Risk Guard
            current_portfolio_risk_pct = 0.0
            for pos in active_positions:
                p_risk = pos.get("risk_pct", 2.0)
                if p_risk <= 0.0:
                    p_risk = 2.0
                if p_risk > 1.0:
                    p_risk = p_risk / 100.0
                current_portfolio_risk_pct += p_risk

            total_proposed_risk = current_portfolio_risk_pct + incoming_risk_pct

            if total_proposed_risk > self.MAX_PORTFOLIO_RISK + 0.0001:
                curr_disp = f"{round(current_portfolio_risk_pct * 100)}%"
                inc_disp = f"{round(incoming_risk_pct * 100)}%"
                max_disp = f"{round(self.MAX_PORTFOLIO_RISK * 100)}%"
                
                log_msg = (
                    "[PORTFOLIO GUARD]\n"
                    "Portfolio risk limit exceeded.\n"
                    f"Current: {curr_disp}\n"
                    f"Incoming: {inc_disp}\n"
                    f"Maximum: {max_disp}\n"
                    "Trade rejected."
                )
                logger.warning(log_msg)

                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="RISK",
                            message="[PORTFOLIO GUARD] Portfolio risk limit exceeded",
                            details=f"Current: {curr_disp}, Incoming: {inc_disp}, Maximum: {max_disp}. Trade rejected."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to create audit log for max portfolio risk: {ae}")

                return False, log_msg

            return True, "PASSED"

        except Exception as e:
            logger.error(f"Error evaluating portfolio exposure guard: {e}")
            return True, "PASSED_ON_ERROR"

    async def get_dashboard_metrics(self, balance: float = 100000.0) -> Dict[str, Any]:
        """Returns metrics required for Android dashboard & UI integration."""
        try:
            self._check_circuit_breaker_reset()
            from app.trading.position_manager import PositionManager
            active_positions = PositionManager.get_instance().get_active_positions()
            open_positions_count = len(active_positions)
            
            current_portfolio_risk_pct = 0.0
            for pos in active_positions:
                p_risk = pos.get("risk_pct", 2.0)
                if p_risk <= 0.0:
                    p_risk = 2.0
                if p_risk > 1.0:
                    p_risk = p_risk / 100.0
                current_portfolio_risk_pct += p_risk
                
            portfolio_risk_pct = round(current_portfolio_risk_pct * 100.0, 2)
            remaining_risk_capacity_pct = round(max(0.0, (self.MAX_PORTFOLIO_RISK * 100.0) - portfolio_risk_pct), 2)
            
            today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
            daily_realized_pnl = 0.0
            async with AsyncSessionLocal() as db:
                try:
                    query = select(func.sum(Trade.profit_loss)).where(
                        Trade.exit_price.isnot(None),
                        Trade.exit_time >= today_start
                    )
                    res = await db.execute(query)
                    daily_realized_pnl = res.scalar() or 0.0
                except Exception as e:
                    logger.error(f"Error querying daily PnL for dashboard metrics: {e}")

            starting_daily_balance = balance - daily_realized_pnl if (balance - daily_realized_pnl) > 0 else balance
            daily_realized_pnl_pct = round((daily_realized_pnl / starting_daily_balance) * 100.0, 2) if starting_daily_balance > 0 else 0.0

            cb_status = "ACTIVE" if self._circuit_breaker_active or daily_realized_pnl_pct <= -5.0 else "INACTIVE"

            return {
                "open_positions": open_positions_count,
                "max_open_positions": self.MAX_OPEN_POSITIONS,
                "portfolio_risk_pct": portfolio_risk_pct,
                "max_portfolio_risk_pct": self.MAX_PORTFOLIO_RISK * 100.0,
                "daily_realized_pnl_pct": daily_realized_pnl_pct,
                "daily_loss_limit_pct": self.MAX_DAILY_LOSS * 100.0,
                "remaining_risk_capacity": remaining_risk_capacity_pct,
                "circuit_breaker_status": cb_status
            }
        except Exception as e:
            logger.error(f"Error building portfolio guard dashboard metrics: {e}")
            return {
                "open_positions": 0,
                "max_open_positions": 5,
                "portfolio_risk_pct": 0.0,
                "max_portfolio_risk_pct": 6.0,
                "daily_realized_pnl_pct": 0.0,
                "daily_loss_limit_pct": 5.0,
                "remaining_risk_capacity": 6.0,
                "circuit_breaker_status": "INACTIVE"
            }
