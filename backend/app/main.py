from contextlib import asynccontextmanager
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.api.api import api_router
from app.api.endpoints import laptop
from app.database.session import engine
from app.database.base import Base
from app.scheduler.manager import LayeredSchedulerManager

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Performs dynamic base initialization for local environments if using SQLite."""
    if "sqlite" in settings.DATABASE_URL:
        # Create tables automatically for ease of local evaluation / development
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
            
    # Start the Layered VPS Operational Daemon (Layer 2, 3, and 4)
    scheduler_manager = await LayeredSchedulerManager.get_instance()
    await scheduler_manager.start_all()
    
    yield
    
    # Graceful shutdown of daemon threads
    await scheduler_manager.stop_all()

# Initialize FastAPI with metadata and lifespan
app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Scalable trading intelligence, risk mitigation and paper simulation engine for Lumina",
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    lifespan=lifespan
)

# Configure CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_HOSTS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Root-level health endpoint
@app.get("/health", tags=["health"])
async def root_health_check():
    """Application-wide root health check, yielding live operational telemetry."""
    return {
        "status": "healthy",
        "app_name": settings.PROJECT_NAME,
        "version": settings.VERSION
    }

# Include API router at /api/v1, /api, and root level to support all client route patterns
app.include_router(api_router, prefix=settings.API_V1_STR)
app.include_router(api_router, prefix="/api")
app.include_router(api_router)

# Include Laptop Agent Capability router under /api
app.include_router(laptop.router, prefix="/api", tags=["laptop"])

@app.get("/")
async def root_index():
    return {
        "message": f"Welcome to {settings.PROJECT_NAME}",
        "docs_url": "/docs",
        "health_url": "/health"
    }

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
