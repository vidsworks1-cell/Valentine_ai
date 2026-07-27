try:
    from app.api.api import api_router
except ImportError:
    api_router = None

__all__ = ["api_router"]
