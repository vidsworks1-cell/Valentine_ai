from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field

class TradeBase(BaseModel):
    symbol: str = Field(..., max_length=20, examples=["BTCUSDT"])
    side: str = Field(..., examples=["BUY"])
    entry_price: float = Field(..., gt=0.0)
    position_size: float = Field(..., gt=0.0)
    stop_loss: float = Field(0.0, ge=0.0)
    take_profit: float = Field(0.0, ge=0.0)
    strategy_used: str = Field("AZA_STRATEGY")
    market_regime: str = Field("TREND")
    confidence_score: float = Field(1.0, ge=0.0, le=1.0)
    entry_reason: str = Field("Breakout momentum confirmation")
    indicators: str = Field("{}", description="Serialized JSON of technical indicators at entry")
    atr: float = Field(0.0, ge=0.0, description="Average True Range at entry")
    risk_amount: float = Field(0.0, ge=0.0, description="Absolute risk size in quote currency")
    risk_pct: float = Field(0.0, ge=0.0, description="Percent of account size risked")

class TradeCreate(TradeBase):
    pass

class TradeUpdate(BaseModel):
    exit_price: Optional[float] = None
    profit_loss: Optional[float] = None
    exit_time: Optional[datetime] = None
    outcome: Optional[str] = None

class TradeResponse(TradeBase):
    id: int
    exit_price: Optional[float] = None
    profit_loss: Optional[float] = None
    entry_time: datetime
    exit_time: Optional[datetime] = None
    outcome: Optional[str] = None

    class Config:
        from_attributes = True

# Pydantic schemas for AuditLog
class AuditLogBase(BaseModel):
    level: str = "INFO"
    category: str = "SYSTEM"
    message: str
    details: str = "{}"
    trade_id: Optional[int] = None

class AuditLogCreate(AuditLogBase):
    pass

class AuditLogResponse(AuditLogBase):
    id: int
    timestamp: datetime

    class Config:
        from_attributes = True

class PaperWalletBase(BaseModel):
    account_id: str
    balance: float = 100000.0

class PaperWalletResponse(PaperWalletBase):
    id: int
    updated_at: datetime

    class Config:
        from_attributes = True

class RiskProfileBase(BaseModel):
    strategy_name: str
    max_drawdown: float = 0.10
    max_risk_per_trade: float = 0.02

class RiskProfileResponse(RiskProfileBase):
    id: int
    updated_at: datetime

    class Config:
        from_attributes = True


# Pydantic schemas for Laptop Agent Capability
class LaptopDocumentBase(BaseModel):
    id: str
    title: str
    content: str
    category: str
    sizeBytes: int
    lastModified: int

class LaptopDocumentResponse(LaptopDocumentBase):
    pass

class DataExchangePayload(BaseModel):
    deviceId: str
    clientVersion: str
    dataPayload: dict
    timestamp: int

class SyncResponse(BaseModel):
    status: str
    timestamp: int
    recordsSynced: int
    deviceName: str
