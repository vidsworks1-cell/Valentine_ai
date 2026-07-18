from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.models.risk import RiskProfile
from app.api.schemas import RiskProfileBase

class RiskService:
    """Service to evaluate and persist strategy-specific risk parameters."""
    
    @staticmethod
    async def get_risk_profile(db: AsyncSession, strategy_name: str):
        """Fetch risk limitations by strategy identification."""
        query = select(RiskProfile).where(RiskProfile.strategy_name == strategy_name)
        result = await db.execute(query)
        return result.scalar_one_or_none()

    @staticmethod
    async def create_or_update_risk_profile(db: AsyncSession, profile_in: RiskProfileBase):
        """Update active limit specifications or populate default metrics."""
        profile = await RiskService.get_risk_profile(db, profile_in.strategy_name)
        if not profile:
            profile = RiskProfile(
                strategy_name=profile_in.strategy_name,
                max_drawdown=profile_in.max_drawdown,
                max_risk_per_trade=profile_in.max_risk_per_trade
            )
        else:
            profile.max_drawdown = profile_in.max_drawdown
            profile.max_risk_per_trade = profile_in.max_risk_per_trade
        
        db.add(profile)
        await db.commit()
        await db.refresh(profile)
        return profile
