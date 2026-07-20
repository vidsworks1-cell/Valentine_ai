from fastapi import APIRouter
from app.workers.price_cache import PriceCache

router = APIRouter()

@router.get("/status")
async def get_market_status():
    """Fetches fast Layer 2 market cache connectivity metrics and cached symbols."""
    cache = await PriceCache.get_instance()
    prices = cache.get_all_prices()
    
    return {
        "last_update_time": cache.last_update_time,
        "connection_status": cache.connection_status,
        "cached_symbols": list(prices.keys())
    }
