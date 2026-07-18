from fastapi import APIRouter
from app.core.config import settings

router = APIRouter()

@router.get("")
@router.get("/")
async def get_health():
    """Health check endpoint to verify backend service status and version."""
    return {
        "status": "healthy",
        "name": settings.PROJECT_NAME,
        "version": settings.VERSION
    }
