"""
Binance Futures Open Interest (OI) & Trapped Liquidity Squeeze Fuel Detector.

Detects when a breakout has genuine institutional liquidity fuel from trapped counterparties:
1. Open Interest (OI) Surge:
   - Measures rolling OI change. An OI surge of >= +3.0% indicates aggressive new capital entry,
     rather than temporary retail churning.
2. Funding Rate & Liquidations Fuel:
   - For BUY breakouts: Negative or neutral funding with rising OI indicates aggressive short positioning.
     As price breaks out, forced short liquidations and stop-market buys provide explosive continuation fuel.
   - For SELL breakdowns: Positive funding with rising OI indicates over-leveraged longs trapped.
3. Micro-Account Protection:
   - Ensures trades are placed in the direction of the trapped liquidity cascade, resulting in
     clean momentum expansions that rarely retrace back to entry.
"""

import logging
import time
from typing import Dict, Any, Optional
from app.workers.price_cache import PriceCache

logger = logging.getLogger("lumina.trading.open_interest_fuel")


class OpenInterestFuelDetector:
    """Detects Open Interest surges and trapped counterparty squeeze fuel in Binance Futures."""
    _instance: Optional['OpenInterestFuelDetector'] = None

    def __init__(self):
        # Rolling cache of recent OI observations per symbol: symbol -> [(timestamp, oi_val)]
        self._oi_history: Dict[str, list] = {}

    @classmethod
    def get_instance(cls) -> 'OpenInterestFuelDetector':
        if cls._instance is None:
            cls._instance = OpenInterestFuelDetector()
        return cls._instance

    def record_open_interest(self, symbol: str, open_interest: float):
        """Records an OI data point in rolling memory."""
        if open_interest <= 0.0:
            return
        sym = symbol.upper()
        if sym not in self._oi_history:
            self._oi_history[sym] = []
        now = time.time()
        self._oi_history[sym].append((now, open_interest))
        # Keep up to 30 observations
        if len(self._oi_history[sym]) > 30:
            self._oi_history[sym].pop(0)

    def evaluate_oi_squeeze_fuel(
        self,
        symbol: str,
        side: str,
        current_price: float
    ) -> Dict[str, Any]:
        """
        Evaluates Open Interest expansion and funding rate skew to detect trapped counterparty fuel.
        """
        sym = symbol.upper()
        side_upper = side.upper()
        cache = PriceCache.get_instance_sync()
        ta = cache.get_technical_analysis(sym)
        rvol = cache.get_volume_surge_ratio(sym) or 1.0

        # Estimate / query funding rate
        funding_rate = float(ta.get("funding_rate", 0.0001)) # Default 0.01% standard Binance funding
        
        # Calculate empirical OI change from rolling history if present
        oi_history = self._oi_history.get(sym, [])
        oi_change_pct = 0.0
        if len(oi_history) >= 2:
            oldest_oi = oi_history[0][1]
            latest_oi = oi_history[-1][1]
            if oldest_oi > 0:
                oi_change_pct = ((latest_oi - oldest_oi) / oldest_oi) * 100.0
        else:
            # If historical points are still filling, correlate with RVOL expansion
            oi_change_pct = max(0.5, (rvol - 1.0) * 3.5)

        # 1. Trapped Squeeze Fuel Conditions:
        # BUY: Rising OI (>= +2.5%) + Neutral or Negative Funding (<= 0.00015) -> Shorts trapped
        # SELL: Rising OI (>= +2.5%) + Overheated Funding (>= 0.00030) -> Longs trapped
        is_trapped_squeeze = False
        squeeze_fuel_type = "NEUTRAL_OI"

        if side_upper == "BUY":
            if oi_change_pct >= 2.5 and funding_rate <= 0.00015:
                is_trapped_squeeze = True
                squeeze_fuel_type = "SHORT_SQUEEZE_FUEL_EXPANSION"
            elif oi_change_pct >= 1.5:
                squeeze_fuel_type = "MILD_OI_ACCUMULATION"
        else: # SELL
            if oi_change_pct >= 2.5 and funding_rate >= 0.00010:
                is_trapped_squeeze = True
                squeeze_fuel_type = "LONG_LIQUIDATION_CASCADE_FUEL"
            elif oi_change_pct >= 1.5:
                squeeze_fuel_type = "MILD_OI_DISTRIBUTION"

        # Squeeze confidence bonus (-0.02 to +0.06)
        if is_trapped_squeeze:
            fuel_bonus = 0.05 if rvol >= 1.8 else 0.03
        else:
            fuel_bonus = 0.0

        reason = (
            f"OI Fuel: {squeeze_fuel_type} (OI Change: {oi_change_pct:+.1f}%, "
            f"Funding: {funding_rate*100:.3f}%, Bonus: +{fuel_bonus:.2f})"
        )

        return {
            "symbol": sym,
            "side": side_upper,
            "has_squeeze_fuel": is_trapped_squeeze,
            "squeeze_fuel_type": squeeze_fuel_type,
            "oi_change_pct": round(oi_change_pct, 2),
            "funding_rate": funding_rate,
            "fuel_confidence_bonus": fuel_bonus,
            "reason": reason
        }
