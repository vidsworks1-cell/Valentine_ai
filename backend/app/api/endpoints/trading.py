from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.api.schemas import TradeCreate, TradeResponse, TradeUpdate
from app.services.trading_service import TradingService

router = APIRouter()

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
