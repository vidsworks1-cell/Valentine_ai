from datetime import datetime
from typing import Optional
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy import String, DateTime, Text, Integer, ForeignKey
from app.database.base import Base

class AuditLog(Base):
    """SQLAlchemy model representing an execution or decision audit trail."""
    __tablename__ = "audit_logs"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, index=True, nullable=False)
    level: Mapped[str] = mapped_column(String(20), default="INFO", nullable=False) # INFO, WARNING, ERROR
    category: Mapped[str] = mapped_column(String(50), default="SYSTEM", index=True, nullable=False) # TRADING, RISK, SIMULATOR, SYSTEM
    message: Mapped[str] = mapped_column(String(500), nullable=False)
    details: Mapped[str] = mapped_column(Text, default="{}", nullable=False) # JSON payload containing state variables, indicators, or inputs
    
    # Optional foreign key linking directly to the trades table
    trade_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey("trades.id", ondelete="SET NULL"), nullable=True)
