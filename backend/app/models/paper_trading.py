from datetime import datetime
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy import String, Float, DateTime
from app.database.base import Base

class PaperWallet(Base):
    """SQLAlchemy model representing a paper trading virtual wallet."""
    __tablename__ = "paper_wallets"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    account_id: Mapped[str] = mapped_column(String(50), unique=True, index=True, nullable=False)
    balance: Mapped[float] = mapped_column(Float, default=100000.0, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
