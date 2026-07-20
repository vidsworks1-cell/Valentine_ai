from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.models.paper_trading import PaperWallet

class PaperTradingService:
    """Service handling paper trading simulation and paper balance adjustments."""
    
    @staticmethod
    async def get_wallet(db: AsyncSession, account_id: str):
        """Find virtual wallet info for a specified client account."""
        query = select(PaperWallet).where(PaperWallet.account_id == account_id)
        result = await db.execute(query)
        return result.scalar_one_or_none()

    @staticmethod
    async def create_or_get_wallet(db: AsyncSession, account_id: str, initial_balance: float = 100000.0):
        """Retrieve current wallet, or initialize one if non-existent."""
        wallet = await PaperTradingService.get_wallet(db, account_id)
        if wallet:
            return wallet
        
        db_wallet = PaperWallet(account_id=account_id, balance=initial_balance)
        db.add(db_wallet)
        await db.commit()
        await db.refresh(db_wallet)
        return db_wallet

    @staticmethod
    async def create_wallet(db: AsyncSession, account_id: str, initial_balance: float = 100000.0):
        """Create a new wallet. Legacy alias for create_or_get_wallet."""
        return await PaperTradingService.create_or_get_wallet(db, account_id, initial_balance)

    @staticmethod
    async def adjust_balance(db: AsyncSession, account_id: str, amount_change: float):
        """Alter wallet balance. Legacy alias for update_balance."""
        return await PaperTradingService.update_balance(db, account_id, amount_change)

    @staticmethod
    async def update_balance(db: AsyncSession, account_id: str, amount_change: float):
        """Alter wallet balance (e.g. credits for profits, debits for losses)."""
        wallet = await PaperTradingService.get_wallet(db, account_id)
        if not wallet:
            return None
        
        wallet.balance += amount_change
        db.add(wallet)
        await db.commit()
        await db.refresh(wallet)
        return wallet
