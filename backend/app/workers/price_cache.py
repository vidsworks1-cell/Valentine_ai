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
        self._symbol_last_update_time: Dict[str, float] = {}
        self._symbol_synchronized: Dict[str, bool] = {}
        self._last_update_time: float = time.time()
        self._connection_status: str = "DISCONNECTED"

    @classmethod
    async def get_instance(cls) -> 'PriceCache':
        async with cls._lock:
            if cls._instance is None:
                cls._instance = PriceCache()
            return cls._instance

    def update_price(self, symbol: str, price: float):
        """Updates the cached price for a given symbol and increments update tally."""
        sym = symbol.upper()
        now = time.time()
        self._prices[sym] = price
        self._updates_count[sym] = self._updates_count.get(sym, 0) + 1
        self._symbol_last_update_time[sym] = now
        self._symbol_synchronized[sym] = True
        self._last_update_time = now

    def get_price(self, symbol: str) -> float:
        """Retrieves the latest price from the cache with fallback to standard reference."""
        return self._prices.get(symbol.upper(), 92500.0 if "BTC" in symbol else 3450.0)

    def get_all_prices(self) -> Dict[str, float]:
        """Returns the complete map of tracked symbol prices."""
        return self._prices.copy()

    def get_update_tally(self, symbol: str) -> int:
        """Returns the number of WebSocket ticker updates received since startup."""
        return self._updates_count.get(symbol.upper(), 0)

    def get_symbol_last_update_time(self, symbol: str) -> float:
        """Returns timestamp of the last ticker update for symbol."""
        return self._symbol_last_update_time.get(symbol.upper(), 0.0)

    def is_symbol_synchronized(self, symbol: str) -> bool:
        """Checks if symbol has been synchronized with live WebSocket updates."""
        return self._symbol_synchronized.get(symbol.upper(), False)

    def validate_market_data(self, symbol: str, max_age_seconds: float = 5.0) -> Tuple[bool, str]:
        """
        Hard Safety Gate for Market Data.
        Verifies:
        1. live_update_count > 0
        2. price timestamp is recent (< 5 seconds old)
        3. symbol is marked as synchronized
        4. connection status is CONNECTED
        """
        sym = symbol.upper()
        tally = self.get_update_tally(sym)
        if tally == 0:
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: No verified live WebSocket price available."

        if not self.is_symbol_synchronized(sym):
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: Symbol {sym} is not synchronized with live feed."

        last_update = self.get_symbol_last_update_time(sym)
        age = time.time() - last_update
        if age > max_age_seconds:
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: Price timestamp is stale ({age:.1f}s old, max allowed {max_age_seconds:.1f}s)."

        if self._connection_status != "CONNECTED":
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: WebSocket connection status is {self._connection_status}."

        return True, "PASSED"

    @property
    def last_update_time(self) -> float:
        return self._last_update_time

    @property
    def connection_status(self) -> str:
        return self._connection_status

    @connection_status.setter
    def connection_status(self, status: str):
        self._connection_status = status
