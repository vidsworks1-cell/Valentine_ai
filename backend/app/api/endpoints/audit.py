from typing import List
from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.api.schemas import AuditLogResponse, AuditLogCreate
from app.services.audit_service import AuditService

router = APIRouter()

@router.get("", response_model=List[AuditLogResponse])
async def read_audit_logs(
    skip: int = 0,
    limit: int = 100,
    db: AsyncSession = Depends(get_db)
):
    """Retrieve systemic decision and execution log records."""
    return await AuditService.get_logs(db, skip=skip, limit=limit)

@router.post("", response_model=AuditLogResponse, status_code=status.HTTP_201_CREATED)
async def create_audit_log(log_in: AuditLogCreate, db: AsyncSession = Depends(get_db)):
    """Manually persist an external indicator state or telemetry event."""
    return await AuditService.log_event(
        db=db,
        message=log_in.message,
        category=log_in.category,
        level=log_in.level,
        details=log_in.details,
        trade_id=log_in.trade_id
    )
