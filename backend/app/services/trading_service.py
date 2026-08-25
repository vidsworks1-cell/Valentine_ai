from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.models.trading import Trade
from app.api.schemas import TradeCreate, TradeUpdate

class TradingService:
    """Service handling trading and transaction audit logs business logic."""
    
    @staticmethod
    async def get_trades(db: AsyncSession, skip: int = 0, limit: int = 100, trading_mode: Optional[str] = None):
        """Fetch multiple trade objects using modern SQLAlchemy 2.0 scalar streams."""
        query = select(Trade)
        if trading_mode:
            query = query.where(Trade.trading_mode == trading_mode.upper())
        query = query.order_by(Trade.entry_time.desc()).offset(skip).limit(limit)
        result = await db.execute(query)
        return result.scalars().all()

    @staticmethod
    async def get_trade_by_id(db: AsyncSession, trade_id: int):
        """Retrieve a unique trade object by its primary key ID."""
        query = select(Trade).where(Trade.id == trade_id)
        result = await db.execute(query)
        return result.scalar_one_or_none()

    @staticmethod
    async def create_trade(db: AsyncSession, trade_in: TradeCreate):
        """Persist a new paper or live trade in the database."""
        # Using Pydantic v2 model_dump()
        db_trade = Trade(**trade_in.model_dump())
        db.add(db_trade)
        await db.commit()
        await db.refresh(db_trade)
        return db_trade

    @staticmethod
    async def update_trade(db: AsyncSession, trade_id: int, trade_in: TradeUpdate):
        """Apply patch-updates to an existing trade (e.g. exit time, exit price, profits)."""
        db_trade = await TradingService.get_trade_by_id(db, trade_id)
        if not db_trade:
            return None
        
        # Using Pydantic v2 model_dump(exclude_unset=True)
        update_data = trade_in.model_dump(exclude_unset=True)
        for key, val in update_data.items():
            setattr(db_trade, key, val)
        
        db.add(db_trade)
        await db.commit()
        await db.refresh(db_trade)
        return db_trade
