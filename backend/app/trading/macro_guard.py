import asyncio
import logging
import math
import time
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

from app.workers.price_cache import PriceCache
from app.api.schemas import AuditLogCreate
from app.services.audit_service import AuditService
from app.database.session import AsyncSessionLocal

logger = logging.getLogger("lumina.trading.macro_guard")

class SystemicMacroGuardEngine:
    """
    Stage 4 Multi-Asset Cross-Correlation & Systemic Macro Guard for Lumina.
    
    Detects lead-lag relationships and market-wide correlation spikes:
    - Tracks BTC as macro lead indicator.
    - Monitors real-time cross-correlation matrix across traded assets (BTC, ETH, SOL, BNB).
    - Calculates Systemic Risk Index (0.0 to 1.0).
    - Automatically pre-emptively trims altcoin exposures or tightens stops during systemic cascades
      before individual altcoin stop-losses are breached.
    """
    _instance: Optional['SystemicMacroGuardEngine'] = None

    def __init__(self):
        self.risk_threshold_elevated: float = 0.72
        self.risk_threshold_critical: float = 0.82
        self.lead_asset: str = "BTCUSDT"
        self.recent_trim_events: List[Dict[str, Any]] = []
        self.last_evaluated_at: Optional[str] = None
        
        self.monitored_universe: List[str] = [
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
            "ADAUSDT", "DOTUSDT", "MATICUSDT", "AVAXUSDT",
            "LINKUSDT", "NEARUSDT", "LTCUSDT", "XRPUSDT",
            "PEPEUSDT", "DOGEUSDT", "RENDERUSDT", "INJUSDT",
            "SUIUSDT", "FETUSDT", "TIAUSDT"
        ]
        
        # Price history buffer for correlation calculations across entire universe
        self._price_history: Dict[str, List[float]] = {sym: [] for sym in self.monitored_universe}
        self.max_history_len: int = 30

    @classmethod
    def get_instance(cls) -> 'SystemicMacroGuardEngine':
        if cls._instance is None:
            cls._instance = SystemicMacroGuardEngine()
        return cls._instance

    def update_price(self, symbol: str, price: float):
        """Updates rolling price history for correlation matrix calculation."""
        if symbol not in self._price_history:
            self._price_history[symbol] = []
        self._price_history[symbol].append(price)
        if len(self._price_history[symbol]) > self.max_history_len:
            self._price_history[symbol].pop(0)

    def calculate_correlation(self, asset_a: str, asset_b: str) -> float:
        """Calculates Pearson correlation coefficient between two assets."""
        hist_a = self._price_history.get(asset_a, [])
        hist_b = self._price_history.get(asset_b, [])

        min_len = min(len(hist_a), len(hist_b))
        if min_len < 5:
            # Default realistic baseline correlation if history is accumulating
            baselines = {
                ("BTCUSDT", "ETHUSDT"): 0.88,
                ("BTCUSDT", "SOLUSDT"): 0.82,
                ("BTCUSDT", "BNBUSDT"): 0.79,
                ("BTCUSDT", "ADAUSDT"): 0.78,
                ("BTCUSDT", "AVAXUSDT"): 0.81,
                ("BTCUSDT", "LINKUSDT"): 0.77,
                ("BTCUSDT", "NEARUSDT"): 0.79,
                ("BTCUSDT", "SUIUSDT"): 0.74,
                ("BTCUSDT", "DOTUSDT"): 0.76,
                ("BTCUSDT", "LTCUSDT"): 0.75,
                ("BTCUSDT", "DOGEUSDT"): 0.70,
                ("BTCUSDT", "PEPEUSDT"): 0.68,
                ("BTCUSDT", "INJUSDT"): 0.76,
                ("BTCUSDT", "RENDERUSDT"): 0.77,
                ("BTCUSDT", "FETUSDT"): 0.75,
                ("BTCUSDT", "TIAUSDT"): 0.73,
                ("BTCUSDT", "XRPUSDT"): 0.72,
            }
            key = (asset_a, asset_b) if (asset_a, asset_b) in baselines else (asset_b, asset_a)
            return baselines.get(key, 0.75)

        a = hist_a[-min_len:]
        b = hist_b[-min_len:]

        mean_a = sum(a) / min_len
        mean_b = sum(b) / min_len

        var_a = sum((x - mean_a) ** 2 for x in a)
        var_b = sum((y - mean_b) ** 2 for y in b)

        if var_a == 0 or var_b == 0:
            return 0.80

        cov = sum((x - mean_a) * (y - mean_b) for x, y in zip(a, b))
        corr = cov / math.sqrt(var_a * var_b)
        return max(-1.0, min(1.0, round(corr, 3)))

    async def evaluate_systemic_risk(self) -> Dict[str, Any]:
        """Evaluates macro market correlation and lead-lag risks across active positions."""
        now_str = datetime.now(timezone.utc).isoformat()
        self.last_evaluated_at = now_str

        # Fetch current prices from PriceCache across all universe assets
        prices = {}
        for sym in self.monitored_universe:
            default_p = 65000.0 if "BTC" in sym else (3500.0 if "ETH" in sym else (150.0 if "SOL" in sym else (580.0 if "BNB" in sym else 10.0)))
            px = PriceCache.get_price(sym) or default_p
            prices[sym] = px
            self.update_price(sym, px)

        # Compute cross-correlation matrix against BTC for all universe assets
        correlations = {}
        for sym in self.monitored_universe:
            if sym != "BTCUSDT":
                correlations[sym] = self.calculate_correlation("BTCUSDT", sym)

        avg_correlation = sum(correlations.values()) / max(1, len(correlations))

        # Detect Lead Asset (BTC) short-term impulse drop
        btc_hist = self._price_history.get("BTCUSDT", [])
        btc_impulse_drop = False
        btc_change_pct = 0.0
        if len(btc_hist) >= 3:
            btc_change_pct = ((btc_hist[-1] - btc_hist[0]) / btc_hist[0]) * 100.0
            if btc_change_pct < -0.8: # Rapid downward movement
                btc_impulse_drop = True

        # Systemic Risk Index formula: weighted combination of avg correlation & BTC lead momentum
        base_risk = (avg_correlation - 0.5) * 1.5  # Map 0.5-1.0 corr to 0.0-0.75 risk
        if btc_impulse_drop:
            base_risk += 0.30
        
        systemic_risk_index = max(0.0, min(1.0, round(base_risk, 3)))

        # Determine Macro Regime
        if systemic_risk_index >= self.risk_threshold_critical:
            regime = "SYSTEMIC_CASCADE_WARNING"
        elif systemic_risk_index >= self.risk_threshold_elevated:
            regime = "ELEVATED_MACRO_RISK"
        else:
            regime = "NORMAL_DECENTRALIZED"

        # Pre-emptive De-risking Action during Systemic Cascade (Only on critical risk or BTC impulse drop)
        if regime == "SYSTEMIC_CASCADE_WARNING" or (regime == "ELEVATED_MACRO_RISK" and btc_impulse_drop):
            try:
                from app.scheduler.manager import LayeredSchedulerManager
                from app.trading.position_manager import PositionManager
                scheduler = await LayeredSchedulerManager.get_instance()
                active_mode = getattr(scheduler, "global_trading_mode", "PAPER")
                pm = PositionManager.get_instance()
                active_positions = pm.get_active_positions(trading_mode=active_mode)

                for pos in active_positions:
                    symbol = pos.get("symbol")
                    trade_id = pos.get("trade_id", pos.get("id"))
                    side = pos.get("side", "BUY")

                    # If BTC flash drops, altcoins highly correlated (>0.75) are pre-emptively trimmed
                    if symbol != "BTCUSDT" and correlations.get(symbol, 0.8) >= 0.75 and side == "BUY":
                        orig_sl = pos.get("trailing_stop", pos.get("entry_price", 0.0) * 0.98)
                        current_px = prices.get(symbol, pos.get("entry_price", 100.0))
                        new_sl = round(max(orig_sl, current_px * 0.995), 2) # Tighten stop loss to 0.5% below current price

                        # Require a meaningful step (at least 0.5% higher than orig_sl) to prevent micro-adjusting on every tick
                        if new_sl >= round(orig_sl * 1.005, 2):
                            pos["trailing_stop"] = new_sl
                            event = {
                                "trade_id": trade_id,
                                "symbol": symbol,
                                "action": "PREEMPTIVE_STOP_TIGHTEN",
                                "old_sl": orig_sl,
                                "new_sl": new_sl,
                                "systemic_risk_index": systemic_risk_index,
                                "btc_impulse_drop": btc_impulse_drop,
                                "timestamp": now_str
                            }
                            self.recent_trim_events.append(event)
                            if len(self.recent_trim_events) > 10:
                                self.recent_trim_events.pop(0)

                            logger.warning(
                                f"🛡️ [MACRO GUARD PRE-EMPTIVE ACTION] Trade {trade_id} {symbol}: "
                                f"High BTC correlation ({correlations.get(symbol):.2f}) & Systemic Risk ({systemic_risk_index:.2f}). "
                                f"Tightened Stop Loss from ${orig_sl} to ${new_sl} to protect capital."
                            )

                            # Log audit trail
                            async with AsyncSessionLocal() as db:
                                await AuditService.create_audit_log(db, AuditLogCreate(
                                    level="WARNING",
                                    category="MACRO_GUARD",
                                    message=f"Pre-emptive Macro Protection Triggered for {symbol}",
                                    details=f"Systemic Risk Index: {systemic_risk_index}. BTC change: {btc_change_pct:.2f}%. Stop loss tightened to ${new_sl}."
                                ))
            except Exception as e:
                logger.error(f"Error executing macro guard de-risking: {e}")

        return {
            "status": "EVALUATED",
            "evaluated_at": now_str,
            "lead_asset": self.lead_asset,
            "btc_change_pct": round(btc_change_pct, 2),
            "systemic_risk_index": systemic_risk_index,
            "macro_regime": regime,
            "average_correlation": round(avg_correlation, 3),
            "correlations_vs_btc": correlations,
            "thresholds": {
                "elevated": self.risk_threshold_elevated,
                "critical": self.risk_threshold_critical
            },
            "recent_trim_events": self.recent_trim_events
        }
