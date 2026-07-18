from datetime import datetime
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy import String, Float, DateTime
from app.database.base import Base

class RiskProfile(Base):
    """SQLAlchemy model representing a strategy risk profile/parameters."""
    __tablename__ = "risk_profiles"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    strategy_name: Mapped[str] = mapped_column(String(100), unique=True, index=True, nullable=False)
    max_drawdown: Mapped[float] = mapped_column(Float, default=0.10, nullable=False) # e.g. 0.10 = 10% max drawdown
    max_risk_per_trade: Mapped[float] = mapped_column(Float, default=0.02, nullable=False) # e.g. 0.02 = 2% max per trade
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
