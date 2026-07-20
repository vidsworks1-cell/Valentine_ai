import asyncio
import time
from typing import Dict, Optional

class PriceCache:
    """Layer 2 fast in-memory price cache."""
    _instance: Optional['PriceCache'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self._prices: Dict[str, float] = {
            "BTCUSDT": 92500.0,
            "ETHUSDT": 3450.0,
            "BNBUSDT": 580.0,
            "SOLUSDT": 175.0
        }
        self._updates_count: Dict[str, int] = {}
        self._last_update_time: float = time.time()
        self._connection_status: str = "CONNECTED"

    @classmethod
    async def get_instance(cls) -> 'PriceCache':
        async with cls._lock:
            if cls._instance is None:
                cls._instance = PriceCache()
            return cls._instance

    def update_price(self, symbol: str, price: float):
        """Updates the cached price for a given symbol and increments update tally."""
        self._prices[symbol] = price
        self._updates_count[symbol] = self._updates_count.get(symbol, 0) + 1
        self._last_update_time = time.time()

    def get_price(self, symbol: str) -> float:
        """Retrieves the latest price from the cache with fallback to standard reference."""
        return self._prices.get(symbol.upper(), 92500.0 if "BTC" in symbol else 3450.0)

    def get_all_prices(self) -> Dict[str, float]:
        """Returns the complete map of tracked symbol prices."""
        return self._prices.copy()

    def get_update_tally(self, symbol: str) -> int:
        """Returns the number of WebSocket ticker updates received since startup."""
        return self._updates_count.get(symbol, 0)

    @property
    def last_update_time(self) -> float:
        return self._last_update_time

    @property
    def connection_status(self) -> str:
        return self._connection_status

    @connection_status.setter
    def connection_status(self, status: str):
        self._connection_status = status
