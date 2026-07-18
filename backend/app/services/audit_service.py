from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.models.audit import AuditLog
from app.api.schemas import AuditLogCreate

class AuditService:
    """Service handling system, indicator, and risk mitigation event logs."""

    @staticmethod
    async def get_logs(db: AsyncSession, skip: int = 0, limit: int = 100):
        """Fetch audit trail events chronologically descending."""
        query = select(AuditLog).order_by(AuditLog.timestamp.desc()).offset(skip).limit(limit)
        result = await db.execute(query)
        return result.scalars().all()

    @staticmethod
    async def log_event(
        db: AsyncSession, 
        message: str, 
        category: str = "SYSTEM", 
        level: str = "INFO", 
        details: str = "{}", 
        trade_id: int = None
    ):
        """Persist a new audit log record asynchronously."""
        db_log = AuditLog(
            message=message,
            category=category,
            level=level,
            details=details,
            trade_id=trade_id
        )
        db.add(db_log)
        await db.commit()
        await db.refresh(db_log)
        return db_log
