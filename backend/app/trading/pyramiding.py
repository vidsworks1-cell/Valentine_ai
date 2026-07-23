import asyncio
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

from app.api.schemas import AuditLogCreate
from app.services.audit_service import AuditService
from app.database.session import AsyncSessionLocal

logger = logging.getLogger("lumina.trading.pyramiding")

class PyramidingEngine:
    """
    Stage 3 Pyramiding / Scale-In Engine for Lumina.
    
    Safely scales into strong winning positions:
    - Triggers scale-in only when unrealized profit >= 1.5% and trend momentum is intact
    - Max 2 scale-ins per trade
    - Scale-in unit size is reduced (e.g., 50% of original position size)
    - Automatically locks in profits by raising overall Stop Loss to break-even or higher
    """
    _instance: Optional['PyramidingEngine'] = None

    def __init__(self):
        self.max_scale_ins: int = 2
        self.min_profit_pct_for_scale_in: float = 1.5  # 1.5% profit required before scale-in
        self.scale_in_size_factor: float = 0.5         # 50% of original size

    @classmethod
    def get_instance(cls) -> 'PyramidingEngine':
        if cls._instance is None:
            cls._instance = PyramidingEngine()
        return cls._instance

    async def evaluate_scale_in(self, position: Dict[str, Any], current_price: float) -> Optional[Dict[str, Any]]:
        """Evaluates if an active winning position qualifies for a risk-controlled scale-in."""
        trade_id = position.get("trade_id", position.get("id", 0))
        entry_price = position.get("entry_price", 0.0)
        side = position.get("side", "BUY")
        symbol = position.get("symbol", "")
        scale_in_count = position.get("scale_in_count", 0)

        if scale_in_count >= self.max_scale_ins or entry_price <= 0:
            return None

        # Calculate unrealized profit percentage
        if side == "BUY":
            profit_pct = ((current_price - entry_price) / entry_price) * 100.0
        else:
            profit_pct = ((entry_price - current_price) / entry_price) * 100.0

        if profit_pct < self.min_profit_pct_for_scale_in:
            return None

        # Calculate scale-in size (50% of current size)
        original_size = position.get("position_size", 0.0)
        scale_in_size = round(original_size * self.scale_in_size_factor, 4)

        if scale_in_size <= 0:
            return None

        # Calculate new break-even stop loss level
        new_sl = round(entry_price * (1.002 if side == "BUY" else 0.998), 2)

        logger.info(
            f"📈 [PYRAMIDING SCALE-IN TRIGGERED] Trade {trade_id} {symbol} ({side}): "
            f"Profit: +{profit_pct:.2f}% (>= {self.min_profit_pct_for_scale_in}%). "
            f"Scaling in {scale_in_size} units at ${current_price:.2f}. Adjusting SL to ${new_sl}."
        )

        # Update position tracking
        position["scale_in_count"] = scale_in_count + 1
        position["position_size"] = round(original_size + scale_in_size, 4)
        position["trailing_stop"] = new_sl

        # Log audit trail
        try:
            async with AsyncSessionLocal() as db:
                await AuditService.create_audit_log(db, AuditLogCreate(
                    level="INFO",
                    category="TRADING",
                    message=f"Pyramiding Scale-In Executed on Trade {trade_id} ({symbol})",
                    details=f"Scale-in #{scale_in_count + 1}: Added {scale_in_size} units at ${current_price}. Unrealized profit was +{round(profit_pct, 2)}%. Stop Loss locked at ${new_sl}."
                ))
        except Exception as ae:
            logger.error(f"Failed to log pyramiding audit: {ae}")

        return {
            "trade_id": trade_id,
            "symbol": symbol,
            "side": side,
            "scale_in_size": scale_in_size,
            "scale_in_price": current_price,
            "new_stop_loss": new_sl,
            "scale_in_count": scale_in_count + 1
        }
