from datetime import datetime
from typing import Optional, Dict, Any, List
try:
    from pydantic import BaseModel, Field
except ImportError:
    class BaseModel:
        def __init__(self, **kwargs):
            for k, v in kwargs.items():
                setattr(self, k, v)
        def model_dump(self, **kwargs):
            return self.__dict__
        def dict(self, **kwargs):
            return self.__dict__
    def Field(default=None, **kwargs):
        return default

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
    position_size: Optional[float] = None

class TradeResponse(TradeBase):
    id: int
    exit_price: Optional[float] = None
    profit_loss: Optional[float] = None
    entry_time: datetime
    exit_time: Optional[datetime] = None
    outcome: Optional[str] = None

    class Config:
        from_attributes = True

class ManualOrderRequest(BaseModel):
    symbol: str = Field(..., max_length=20, examples=["BTCUSDT"])
    side: str = Field(..., examples=["BUY", "SELL"])
    order_type: str = Field("MARKET", examples=["MARKET", "LIMIT"])
    quantity: Optional[float] = Field(None, gt=0.0, description="Order quantity in base asset units (e.g. 0.0001 BTC)")
    usdt_amount: Optional[float] = Field(None, gt=0.0, description="Order amount in quote asset USD/USDT (e.g. 5.0 USDT). Recommended for market orders with small balances.")
    quote_order_qty: Optional[float] = Field(None, gt=0.0, description="Alias for usdt_amount (Binance quoteOrderQty)")
    quote_qty: Optional[float] = Field(None, gt=0.0, description="Alias for usdt_amount")
    price: Optional[float] = Field(None, ge=0.0, description="Limit order price. Required if order_type is LIMIT")
    stop_loss: Optional[float] = Field(None, ge=0.0, description="Optional stop loss price")
    take_profit: Optional[float] = Field(None, ge=0.0, description="Optional take profit price")
    strategy_used: str = Field("MANUAL_LIVE_EXECUTION", description="Strategy identifier")
    mode: Optional[str] = Field(None, description="Execution mode override: LIVE, TESTNET, or PAPER")

class ManualOrderResponse(BaseModel):
    status: str = Field("EXECUTED", description="Execution status")
    symbol: str
    side: str
    order_type: str
    quantity: float
    executed_price: float
    binance_order_id: Optional[str] = None
    trade_id: Optional[int] = None
    mode: str
    message: str
    raw_binance_response: Dict[str, Any]

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
