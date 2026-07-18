from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.api.schemas import PaperWalletResponse
from app.services.paper_trading_service import PaperTradingService

router = APIRouter()

@router.get("/{account_id}", response_model=PaperWalletResponse)
async def read_wallet(account_id: str, db: AsyncSession = Depends(get_db)):
    """Fetch virtual balance sheets by account name."""
    wallet = await PaperTradingService.get_wallet(db, account_id)
    if not wallet:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Wallet for account {account_id} not found"
        )
    return wallet

@router.post("/{account_id}", response_model=PaperWalletResponse, status_code=status.HTTP_201_CREATED)
async def create_wallet(
    account_id: str, 
    initial_balance: float = 100000.0, 
    db: AsyncSession = Depends(get_db)
):
    """Instantiate a new paper wallet configuration or fetch existing."""
    return await PaperTradingService.create_or_get_wallet(db, account_id, initial_balance)

@router.post("/{account_id}/adjust", response_model=PaperWalletResponse)
async def adjust_wallet(
    account_id: str, 
    amount_change: float, 
    db: AsyncSession = Depends(get_db)
):
    """Adjust virtual balance (positive for deposit/profit, negative for loss/withdrawal)."""
    wallet = await PaperTradingService.update_balance(db, account_id, amount_change)
    if not wallet:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Wallet for account {account_id} not found"
        )
    return wallet
