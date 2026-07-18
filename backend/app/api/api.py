from fastapi import APIRouter
from app.api.endpoints import health, trading, paper_trading, risk, audit

api_router = APIRouter()

api_router.include_router(health.router, prefix="/health", tags=["health"])
api_router.include_router(trading.router, prefix="/trading", tags=["trading"])
api_router.include_router(paper_trading.router, prefix="/paper-trading", tags=["paper-trading"])
api_router.include_router(risk.router, prefix="/risk", tags=["risk"])
api_router.include_router(audit.router, prefix="/audit", tags=["audit"])
