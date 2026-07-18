from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.api.schemas import RiskProfileBase, RiskProfileResponse
from app.services.risk_service import RiskService

router = APIRouter()

@router.get("/{strategy_name}", response_model=RiskProfileResponse)
async def read_risk_profile(strategy_name: str, db: AsyncSession = Depends(get_db)):
    """Retrieve established risk margins and drawdowns by strategy."""
    profile = await RiskService.get_risk_profile(db, strategy_name)
    if not profile:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Risk profile for strategy '{strategy_name}' not found"
        )
    return profile

@router.post("", response_model=RiskProfileResponse, status_code=status.HTTP_201_CREATED)
async def create_or_update_risk_profile(profile_in: RiskProfileBase, db: AsyncSession = Depends(get_db)):
    """Establish or overwrite strategy risk caps."""
    return await RiskService.create_or_update_risk_profile(db, profile_in)
