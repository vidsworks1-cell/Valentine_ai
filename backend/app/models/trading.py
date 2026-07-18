from datetime import datetime
from typing import Optional
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy import String, Float, DateTime
from app.database.base import Base

class Trade(Base):
    """SQLAlchemy 2.0 Model representing a Trade Execution or Paper Trade."""
    __tablename__ = "trades"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    symbol: Mapped[str] = mapped_column(String(20), index=True, nullable=False)
    side: Mapped[str] = mapped_column(String(10), nullable=False) # "BUY" or "SELL"
    entry_price: Mapped[float] = mapped_column(Float, nullable=False)
    exit_price: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    position_size: Mapped[float] = mapped_column(Float, nullable=False)
    stop_loss: Mapped[float] = mapped_column(Float, default=0.0)
    take_profit: Mapped[float] = mapped_column(Float, default=0.0)
    profit_loss: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    strategy_used: Mapped[str] = mapped_column(String(100), nullable=False)
    market_regime: Mapped[str] = mapped_column(String(50), nullable=False) # e.g. TREND, VOLATILE, RANGING, CRASH
    confidence_score: Mapped[float] = mapped_column(Float, default=1.0)
    entry_reason: Mapped[str] = mapped_column(String(500), nullable=False)
    entry_time: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    exit_time: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)

    # Added fields for reproducibility of trade decisions
    indicators: Mapped[str] = mapped_column(String(1000), default="{}", nullable=False) # Serialized JSON of technical indicators e.g. {"rsi": 58, "macd": "bullish"}
    atr: Mapped[float] = mapped_column(Float, default=0.0, nullable=False) # ATR at entry point
    risk_amount: Mapped[float] = mapped_column(Float, default=0.0, nullable=False) # Total cash risk in quote currency
    risk_pct: Mapped[float] = mapped_column(Float, default=0.0, nullable=False) # Percentage risk relative to wallet size
    outcome: Mapped[Optional[str]] = mapped_column(String(50), nullable=True) # WIN, LOSS, STOPPED_OUT, TAKE_PROFIT, BREAKEVEN
