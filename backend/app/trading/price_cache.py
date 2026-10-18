"""PriceCache alias to guarantee compatibility across modules."""
from app.workers.price_cache import PriceCache

__all__ = ["PriceCache"]
