import asyncio
import time
from typing import Dict, Optional, Tuple

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

    @classmethod
    def get_instance_sync(cls) -> 'PriceCache':
        if cls._instance is None:
            cls._instance = PriceCache()
        return cls._instance

    @classmethod
    def update_price(cls, symbol: str, price: float):
        """Updates the cached price for a given symbol and increments update tally."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        now = time.time()
        inst._prices[sym] = price
        inst._updates_count[sym] = inst._updates_count.get(sym, 0) + 1
        inst._symbol_last_update_time[sym] = now
        inst._symbol_synchronized[sym] = True
        inst._last_update_time = now

    @classmethod
    def get_price(cls, symbol: str) -> float:
        """Retrieves the latest price from the cache with fallback to standard reference."""
        inst = cls.get_instance_sync()
        return inst._prices.get(symbol.upper(), 92500.0 if "BTC" in symbol.upper() else 3450.0)

    @classmethod
    def get_all_prices(cls) -> Dict[str, float]:
        """Returns the complete map of tracked symbol prices."""
        inst = cls.get_instance_sync()
        return inst._prices.copy()

    @classmethod
    def get_update_tally(cls, symbol: str) -> int:
        """Returns the number of WebSocket ticker updates received since startup."""
        inst = cls.get_instance_sync()
        return inst._updates_count.get(symbol.upper(), 0)

    @classmethod
    def get_symbol_last_update_time(cls, symbol: str) -> float:
        """Returns timestamp of the last ticker update for symbol."""
        inst = cls.get_instance_sync()
        return inst._symbol_last_update_time.get(symbol.upper(), 0.0)

    @classmethod
    def is_symbol_synchronized(cls, symbol: str) -> bool:
        """Checks if symbol has been synchronized with live WebSocket updates."""
        inst = cls.get_instance_sync()
        return inst._symbol_synchronized.get(symbol.upper(), False)

    @classmethod
    def validate_market_data(cls, symbol: str, max_age_seconds: float = 5.0) -> Tuple[bool, str]:
        """
        Hard Safety Gate for Market Data.
        Verifies:
        1. live_update_count > 0
        2. price timestamp is recent (< 5 seconds old)
        3. symbol is marked as synchronized
        4. connection status is CONNECTED
        """
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        tally = inst.get_update_tally(sym)
        if tally == 0:
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: No verified live WebSocket price available."

        if not inst.is_symbol_synchronized(sym):
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: Symbol {sym} is not synchronized with live feed."

        last_update = inst.get_symbol_last_update_time(sym)
        age = time.time() - last_update
        if age > max_age_seconds:
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: Price timestamp is stale ({age:.1f}s old, max allowed {max_age_seconds:.1f}s)."

        if inst._connection_status != "CONNECTED":
            return False, f"[MARKET DATA GUARD]\nRejected {sym} trade.\nReason: WebSocket connection status is {inst._connection_status}."

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
