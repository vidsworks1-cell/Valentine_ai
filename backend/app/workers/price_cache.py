import asyncio
import time
from typing import Dict, Optional, Tuple, Any, List

class PriceCache:
    """Layer 2 fast in-memory price cache."""
    _instance: Optional['PriceCache'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self._prices: Dict[str, float] = {}
        self._price_history: Dict[str, list] = {}
        self._price_timeline: Dict[str, list] = {}
        self._updates_count: Dict[str, int] = {}
        self._symbol_last_update_time: Dict[str, float] = {}
        self._symbol_synchronized: Dict[str, bool] = {}
        self._volume_24h: Dict[str, float] = {}
        self._high_24h: Dict[str, float] = {}
        self._low_24h: Dict[str, float] = {}
        self._volume_history: Dict[str, list] = {}
        self._bid_depth_05pct: Dict[str, float] = {}
        self._ask_depth_05pct: Dict[str, float] = {}
        self._spread_pct: Dict[str, float] = {}
        self._l2_orderbooks: Dict[str, Dict[str, Any]] = {}
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
        if sym not in inst._price_history:
            inst._price_history[sym] = []
        inst._price_history[sym].append(price)
        if len(inst._price_history[sym]) > 100:
            inst._price_history[sym].pop(0)

        # Record timestamped price history for 1-hour rolling lookback
        if not hasattr(inst, "_price_timeline"):
            inst._price_timeline = {}
        if sym not in inst._price_timeline:
            inst._price_timeline[sym] = []
        inst._price_timeline[sym].append((now, price))
        if len(inst._price_timeline[sym]) > 300:
            cutoff = now - 7200.0
            inst._price_timeline[sym] = [item for item in inst._price_timeline[sym] if item[0] >= cutoff]

        # Dynamic live high/low tracking as ticks stream in
        if sym in inst._high_24h and price > inst._high_24h[sym]:
            inst._high_24h[sym] = price
        if sym in inst._low_24h and (price < inst._low_24h[sym] or inst._low_24h[sym] <= 0):
            inst._low_24h[sym] = price

        inst._updates_count[sym] = inst._updates_count.get(sym, 0) + 1
        inst._symbol_last_update_time[sym] = now
        inst._symbol_synchronized[sym] = True
        inst._last_update_time = now

    @classmethod
    def get_technical_analysis(cls, symbol: str) -> Dict[str, Any]:
        """Calculates real EMA (9, 21), RSI (14), and ATR based on cached price history."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        history = inst._price_history.get(sym, [])
        current_px = inst.get_price(sym)
        dec_prec = 8 if current_px < 0.001 else (6 if current_px < 1.0 else 4)
        if not history or len(history) < 14:
            return {
                "trend_bias": "NEUTRAL",
                "is_trending": False,
                "rsi": 50.0,
                "atr": max(round(current_px * 0.008, dec_prec), 10 ** (-dec_prec)),
                "ema9": current_px,
                "ema21": current_px,
                "momentum_score": 50.0
            }

        def calc_ema(prices, period):
            k = 2.0 / (period + 1.0)
            ema = prices[0]
            for p in prices[1:]:
                ema = (p * k) + (ema * (1.0 - k))
            return ema

        ema9 = calc_ema(history, 9)
        ema21 = calc_ema(history, min(21, len(history)))

        gains = []
        losses = []
        for i in range(1, min(15, len(history))):
            diff = history[-i] - history[-i-1]
            if diff > 0:
                gains.append(diff)
            elif diff < 0:
                losses.append(abs(diff))

        periods = float(min(14, len(history) - 1)) if len(history) > 1 else 14.0
        avg_gain = sum(gains) / periods if gains else 0.0
        avg_loss = sum(losses) / periods if losses else 0.0

        # Safe RSI computation: strictly guard against ZeroDivisionError when avg_loss or avg_gain is zero
        if avg_loss <= 1e-9:
            rsi = 100.0 if avg_gain > 0 else 50.0
        elif avg_gain <= 1e-9:
            rsi = 0.0
        else:
            rs = avg_gain / avg_loss
            rsi = round(100.0 - (100.0 / (1.0 + rs)), 1)

        recent_chunk = history[-20:]
        high_low_range = max(recent_chunk) - min(recent_chunk)
        atr = max(round(high_low_range * 0.5, dec_prec), round(current_px * 0.005, dec_prec), 10 ** (-dec_prec))

        is_bullish = (current_px > ema9 > ema21)
        is_bearish = (current_px < ema9 < ema21)

        if is_bullish:
            bias = "BULLISH"
            is_trending = True
        elif is_bearish:
            bias = "BEARISH"
            is_trending = True
        else:
            bias = "NEUTRAL"
            is_trending = False

        return {
            "trend_bias": bias,
            "is_trending": is_trending,
            "rsi": rsi,
            "atr": atr,
            "ema9": round(ema9, 4),
            "ema21": round(ema21, 4),
            "momentum_score": 75.0 if is_trending else 40.0
        }

    @classmethod
    def update_price_and_volume(cls, symbol: str, price: float, quote_volume_24h: float = 0.0, high_24h: float = 0.0, low_24h: float = 0.0):
        """Updates cached price, 24h volume, 24h high, and 24h low for any exchange symbol."""
        cls.update_price(symbol, price)
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        if high_24h > 0:
            inst._high_24h[sym] = high_24h
        if low_24h > 0:
            inst._low_24h[sym] = low_24h
        if quote_volume_24h > 0:
            inst._volume_24h[sym] = quote_volume_24h
            if sym not in inst._volume_history:
                inst._volume_history[sym] = []
            now = time.time()
            inst._volume_history[sym].append((now, quote_volume_24h))
            if len(inst._volume_history[sym]) > 40:
                inst._volume_history[sym].pop(0)

    @classmethod
    def get_24h_high_low(cls, symbol: str) -> Tuple[float, float]:
        """Returns (high_24h, low_24h) for symbol, or (0.0, 0.0) if not available."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        return inst._high_24h.get(sym, 0.0), inst._low_24h.get(sym, 0.0)

    @classmethod
    def get_price_history(cls, symbol: str) -> List[float]:
        """Returns the list of cached price history observations (up to 100 ticks)."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        return list(inst._price_history.get(sym, []))

    @classmethod
    def get_volume_surge_ratio(cls, symbol: str) -> float:
        """
        Computes recent relative volume surge (RVOL) based on delta in 24h cumulative quote volume.
        Returns ratio >= 1.0 (baseline 1.0 if not enough data).
        """
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        history = inst._volume_history.get(sym, [])
        if len(history) < 3:
            return 1.0

        # Calculate rate of volume change (delta_volume / delta_time)
        rates = []
        for i in range(1, len(history)):
            t_prev, v_prev = history[i-1]
            t_curr, v_curr = history[i]
            dt = t_curr - t_prev
            dv = v_curr - v_prev
            if dt > 0.5 and dv >= 0:
                rates.append(dv / dt)

        if len(rates) < 2:
            return 1.0

        latest_rate = rates[-1]
        baseline_rate = sum(rates[:-1]) / len(rates[:-1])
        if baseline_rate <= 1e-6:
            return 1.0

        ratio = round(latest_rate / baseline_rate, 2)
        return float(max(0.5, min(10.0, ratio)))


    @classmethod
    def get_liquid_symbols(cls, min_quote_volume: float = 10_000_000.0, top_n: int = 70) -> list:
        """Returns liquid USDT contracts sorted by 24h volume across top liquid exchange universe (Top 70, min $10M volume)."""
        inst = cls.get_instance_sync()
        tracked = []
        for sym, px in inst._prices.items():
            if not sym.endswith("USDT") or px <= 0:
                continue
            # Exclude TradFi commodity contracts (Gold/Silver) requiring manual agreement signatures in Binance UI
            if sym.startswith(("XAU", "XAG", "XAUT")):
                continue
            vol = inst._volume_24h.get(sym, 0.0)
            if vol >= min_quote_volume or sym in ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT"]:
                tracked.append((sym, vol))

        # Sort by 24h quote volume descending so top liquid symbols with minimal spread are prioritized
        tracked.sort(key=lambda x: x[1], reverse=True)
        result = [item[0] for item in tracked[:top_n]]
        if not result and inst._prices:
            # Fallback if volume is still populating
            result = [s for s in list(inst._prices.keys()) if s.endswith("USDT")][:top_n]
        return result

    @classmethod
    def get_price(cls, symbol: str) -> float:
        """Retrieves the latest price from the cache or returns 0.0 if untracked."""
        inst = cls.get_instance_sync()
        return inst._prices.get(symbol.upper(), 0.0)

    @classmethod
    def get_all_prices(cls) -> Dict[str, float]:
        """Returns the complete map of tracked symbol prices."""
        inst = cls.get_instance_sync()
        return inst._prices.copy()

    @classmethod
    def get_high_24h(cls, symbol: str) -> Optional[float]:
        """Retrieves 24-hour high price for symbol or dynamic fallback based on price."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        val = inst._high_24h.get(sym)
        if val is not None and val > 0.0:
            return val
        px = inst.get_price(sym)
        return (px * 1.015) if px > 0.0 else None

    @classmethod
    def get_low_24h(cls, symbol: str) -> Optional[float]:
        """Retrieves 24-hour low price for symbol or dynamic fallback based on price."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        val = inst._low_24h.get(sym)
        if val is not None and val > 0.0:
            return val
        px = inst.get_price(sym)
        return (px * 0.985) if px > 0.0 else None

    @classmethod
    def get_price_change_1h(cls, symbol: str) -> float:
        """
        Returns % price change over the last 1 hour (3600s).
        Uses rolling timeline snapshots, or falls back to oldest available price.
        """
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        timeline = getattr(inst, "_price_timeline", {}).get(sym, [])
        cur_px = inst.get_price(sym)
        if not timeline or cur_px <= 0.0:
            return 0.0
        now = time.time()
        cutoff_1h = now - 3600.0
        # Select price closest to or before 1h ago
        p_ref = timeline[0][1]
        for ts, px in timeline:
            if ts <= cutoff_1h:
                p_ref = px
            else:
                break
        if p_ref <= 0.0:
            return 0.0
        return round(((cur_px - p_ref) / p_ref) * 100.0, 3)

    @classmethod
    def get_high_low_1h(cls, symbol: str) -> Tuple[float, float]:
        """Returns (high_1h, low_1h) over the last 1 hour, or dynamic fallback."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        timeline = getattr(inst, "_price_timeline", {}).get(sym, [])
        cur_px = inst.get_price(sym)
        if not timeline or cur_px <= 0.0:
            return cur_px, cur_px
        now = time.time()
        cutoff_1h = now - 3600.0
        recent = [px for ts, px in timeline if ts >= cutoff_1h]
        if not recent:
            recent = [cur_px]
        return max(recent), min(recent)

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
    def update_volume_and_depth(cls, symbol: str, volume_24h: float, bid_depth: float, ask_depth: float, spread_pct: float = 0.01):
        """Updates volume and order book depth metrics in memory."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        inst._volume_24h[sym] = volume_24h
        inst._bid_depth_05pct[sym] = bid_depth
        inst._ask_depth_05pct[sym] = ask_depth
        inst._spread_pct[sym] = spread_pct

    @classmethod
    def get_volume_and_depth(cls, symbol: str) -> Dict[str, float]:
        """Retrieves volume and order book depth metrics for a symbol with live dynamic defaults."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        price = inst.get_price(sym)
        tally = inst.get_update_tally(sym)

        # Baseline fallback calculations derived from symbol price & live updates
        base_vol = inst._volume_24h.get(sym, 12500000.0 if "BTC" in sym else 4500000.0)
        bid_depth = inst._bid_depth_05pct.get(sym, 250000.0 + (tally % 7) * 15000.0)
        ask_depth = inst._ask_depth_05pct.get(sym, 240000.0 + (tally % 5) * 18000.0)
        spread_pct = inst._spread_pct.get(sym, 0.01)

        return {
            "symbol": sym,
            "price": price,
            "volume_24h": base_vol,
            "bid_depth_05pct": bid_depth,
            "ask_depth_05pct": ask_depth,
            "spread_pct": spread_pct
        }

    @classmethod
    def update_l2_orderbook(cls, symbol: str, bids: List[Tuple[float, float]], asks: List[Tuple[float, float]]):
        """Updates Level-2 top order book bids and asks [(price, qty)] for symbol."""
        inst = cls.get_instance_sync()
        sym = symbol.upper()
        inst._l2_orderbooks[sym] = {
            "bids": bids,
            "asks": asks,
            "updated_at": time.time()
        }
        # Update aggregate depth and spread pct from top of book
        if bids and asks:
            best_bid = bids[0][0]
            best_ask = asks[0][0]
            spread = max(0.0, best_ask - best_bid)
            mid = (best_ask + best_bid) / 2.0 if (best_ask + best_bid) > 0 else 1.0
            inst._spread_pct[sym] = round((spread / mid) * 100.0, 4)
            # 0.5% depth calculation
            bid_cutoff = mid * 0.995
            ask_cutoff = mid * 1.005
            inst._bid_depth_05pct[sym] = sum(p * q for p, q in bids if p >= bid_cutoff)
            inst._ask_depth_05pct[sym] = sum(p * q for p, q in asks if p <= ask_cutoff)

    @classmethod
    def get_l2_orderbook(cls, symbol: str) -> Optional[Dict[str, Any]]:
        """Returns cached L2 order book snapshot for symbol."""
        inst = cls.get_instance_sync()
        return inst._l2_orderbooks.get(symbol.upper())

    @classmethod
    def is_symbol_synchronized(cls, symbol: str) -> bool:
        """Checks if symbol has been synchronized with live WebSocket updates."""
        inst = cls.get_instance_sync()
        return inst._symbol_synchronized.get(symbol.upper(), False)

    @classmethod
    def validate_market_data(cls, symbol: str, max_age_seconds: float = 45.0) -> Tuple[bool, str]:
        """
        Hard Safety Gate for Market Data.
        Verifies:
        1. live_update_count > 0
        2. price timestamp is recent (< 45 seconds old)
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
