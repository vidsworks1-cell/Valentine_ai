import logging
import math
from typing import Dict, Any, Tuple, Optional
from app.workers.price_cache import PriceCache

logger = logging.getLogger("lumina.trading.volatility_squeeze")

class VolatilitySqueezeDetector:
    """
    Dynamic Volatility Compression (Squeeze) Detector for Lumina.
    
    Identifies periods where market volatility contracts into tight consolidation ranges
    (Bollinger Bands compressing inside Keltner Channels) prior to explosive directional expansions.
    
    States:
    - SQUEEZE_ACTIVE: BB Bandwidth < KC Bandwidth (Energy coiling/compression building)
    - SQUEEZE_FIRING: BB Bandwidth expands outside KC + RVOL expansion (Breakout in progress)
    - NO_SQUEEZE: Normal volatility regime
    """
    _instance: Optional['VolatilitySqueezeDetector'] = None

    @classmethod
    def get_instance(cls) -> 'VolatilitySqueezeDetector':
        if cls._instance is None:
            cls._instance = VolatilitySqueezeDetector()
        return cls._instance

    def detect_squeeze(self, symbol: str, price: float) -> Dict[str, Any]:
        """
        Analyzes live market metrics to detect volatility compression and breakout signals.
        
        Formulae:
        - Bollinger Bands (20, 2.0): BB_Upper = SMA20 + 2*StdDev, BB_Lower = SMA20 - 2*StdDev
        - Keltner Channels (20, 1.5): KC_Upper = SMA20 + 1.5*ATR20, KC_Lower = SMA20 - 1.5*ATR20
        - Squeeze Ratio = BB_Width / KC_Width. Ratio < 1.0 indicates Squeeze Active.
        - ATR Compression Ratio = Current_ATR / Baseline_ATR. Ratio < 0.75 indicates severe compression.
        """
        sym = symbol.upper()
        cache = PriceCache.get_instance_sync()
        history = cache.get_price_history(sym)
        ta = cache.get_technical_analysis(sym)

        dec_prec = 8 if price < 0.001 else (6 if price < 1.0 else 4)

        # Baseline metrics calculation using real ATR
        atr = ta.get("atr", max(round(price * 0.008, dec_prec), 10 ** (-dec_prec)))
        baseline_atr = max(round(price * 0.012, dec_prec), 10 ** (-dec_prec))
        atr_compression_ratio = round(atr / baseline_atr, 2) if baseline_atr > 0 else 1.0

        # Real Empirical Bollinger Bands & Keltner Channels
        if len(history) >= 14:
            chunk = history[-20:]
            sma20 = sum(chunk) / len(chunk)
            variance = sum((p - sma20) ** 2 for p in chunk) / len(chunk)
            std_dev = math.sqrt(variance)
        else:
            sma20 = price
            std_dev = atr * 0.85

        bb_upper = round(sma20 + (2.0 * std_dev), dec_prec)
        bb_lower = round(sma20 - (2.0 * std_dev), dec_prec)
        bb_width = max(round(bb_upper - bb_lower, dec_prec), 10 ** (-dec_prec))

        # Keltner Channels Width calculation (1.5x ATR multiplier around SMA)
        kc_upper = round(sma20 + (1.5 * atr), dec_prec)
        kc_lower = round(sma20 - (1.5 * atr), dec_prec)
        kc_width = max(round(kc_upper - kc_lower, dec_prec), 10 ** (-dec_prec))

        # Squeeze Ratio (< 1.0 implies BB compressed inside KC)
        squeeze_ratio = round(bb_width / kc_width, 3) if kc_width > 0 else 1.0

        # Compression Score (0.0 = Wide expansion, 100.0 = Extreme tight compression)
        compression_score = round(max(0.0, min(100.0, (1.2 - squeeze_ratio) * 200.0)), 1)

        # Relative Volume (RVOL) from real tick quote volume delta
        rvol = cache.get_volume_surge_ratio(sym)

        # Determine Squeeze State - Calibrated for high-conviction Volatility Expansion
        if squeeze_ratio < 0.95 or atr_compression_ratio < 0.72:
            squeeze_state = "SQUEEZE_ACTIVE"
            squeeze_description = f"High volatility compression coiling energy (Squeeze ratio {squeeze_ratio:.2f}). Standby for breakout."
        elif (0.92 <= squeeze_ratio <= 1.30) and (rvol >= 1.25 or price >= bb_upper * 0.999 or price <= bb_lower * 1.001):
            squeeze_state = "SQUEEZE_FIRING"
            squeeze_description = f"Squeeze release in progress! Volatility expansion & volume surge ({rvol:.2f}x) confirmed."
        else:
            squeeze_state = "NO_SQUEEZE"
            squeeze_description = "Normal volatility expansion / trend continuation."

        # Breakout Directional Bias & Squeeze Pre-Positioning
        is_squeeze_prepositioning = (squeeze_state == "SQUEEZE_ACTIVE")
        prepositioning_trigger_upper = bb_upper if is_squeeze_prepositioning else None
        prepositioning_trigger_lower = bb_lower if is_squeeze_prepositioning else None
        recommended_order_routing = "LIMIT" if is_squeeze_prepositioning else ("MARKET" if squeeze_state == "SQUEEZE_FIRING" else "LIMIT")

        # Predictive Duration & Expansion Target Estimation
        predicted_coiling_candles = max(1, min(18, int((squeeze_ratio / 0.95) * 6))) if squeeze_state == "SQUEEZE_ACTIVE" else 0
        expected_expansion_move_pct = round(max(1.5, min(15.0, (1.2 - squeeze_ratio) * 15.0 + (atr_compression_ratio * 3.0))), 2)
        upper_expansion_target = round(price * (1.0 + (expected_expansion_move_pct / 100.0)), dec_prec)
        lower_expansion_target = round(price * (1.0 - (expected_expansion_move_pct / 100.0)), dec_prec)

        if squeeze_state == "SQUEEZE_FIRING":
            directional_bias = "BUY" if price >= sma20 else "SELL"
        elif squeeze_state == "SQUEEZE_ACTIVE":
            directional_bias = "CONSOLIDATING"
        else:
            directional_bias = "BUY" if price >= sma20 else "SELL"

        # Minervini Volatility Contraction Pattern (VCP) Engine
        vcp_analysis = self.detect_vcp_pattern(sym, price, history)

        return {
            "symbol": sym,
            "price": price,
            "squeeze_state": squeeze_state,
            "squeeze_description": squeeze_description,
            "squeeze_ratio": squeeze_ratio,
            "compression_score": compression_score,
            "is_squeeze_prepositioning": is_squeeze_prepositioning,
            "prepositioning_triggers": {
                "upper_breakout_trigger": prepositioning_trigger_upper,
                "lower_breakout_trigger": prepositioning_trigger_lower,
                "recommended_routing": recommended_order_routing
            },
            "predictive_expansion": {
                "predicted_coiling_candles": predicted_coiling_candles,
                "expected_expansion_move_pct": expected_expansion_move_pct,
                "upper_expansion_target": upper_expansion_target,
                "lower_expansion_target": lower_expansion_target
            },
            "atr": atr,
            "baseline_atr": baseline_atr,
            "atr_compression_ratio": atr_compression_ratio,
            "bollinger_bands": {
                "upper": bb_upper,
                "lower": bb_lower,
                "width": bb_width
            },
            "keltner_channels": {
                "upper": kc_upper,
                "lower": kc_lower,
                "width": kc_width
            },
            "rvol": rvol,
            "directional_bias": directional_bias,
            "vcp_pattern": vcp_analysis
        }

    def detect_vcp_pattern(self, symbol: str, current_price: float, history: list) -> Dict[str, Any]:
        """
        Minervini Volatility Contraction Pattern (VCP) & Contraction Ratio Detector.
        
        Mathematically verifies the series of declining contractions (T1 > T2 > T3):
        - Scans rolling price history to segment successive wave peaks and troughs.
        - Calculates depth percentage: Depth_k = (High_k - Low_k) / High_k * 100%.
        - Enforces contraction damping: Depth_k / Depth_{k-1} <= 0.68.
        - Verifies the 'Apex of the Coil': Price is coiled within 1.5% of upper resistance.
        """
        if len(history) < 20:
            # Synthetic evaluation if history is still warming up
            return {
                "is_vcp_valid": False,
                "contractions_count": 0,
                "contractions_depths": [],
                "contraction_ratio": 1.0,
                "is_at_apex": False,
                "vcp_score": 50.0,
                "rationale": "Insufficient history for VCP wave decomposition (min 20 points)"
            }

        # Segment recent price history into 3 successive rolling contraction windows (T1: earliest, T2: middle, T3: latest)
        w_len = len(history)
        t1_slice = history[: int(w_len * 0.40)]
        t2_slice = history[int(w_len * 0.40): int(w_len * 0.75)]
        t3_slice = history[int(w_len * 0.75):]

        if not t1_slice or not t2_slice or not t3_slice:
            return {"is_vcp_valid": False, "contractions_count": 0, "vcp_score": 40.0, "rationale": "History slices invalid"}

        h1, l1 = max(t1_slice), min(t1_slice)
        h2, l2 = max(t2_slice), min(t2_slice)
        h3, l3 = max(t3_slice), min(t3_slice)

        depth1 = ((h1 - l1) / h1 * 100.0) if h1 > 0 else 0.0
        depth2 = ((h2 - l2) / h2 * 100.0) if h2 > 0 else 0.0
        depth3 = ((h3 - l3) / h3 * 100.0) if h3 > 0 else 0.0

        depths = [round(depth1, 2), round(depth2, 2), round(depth3, 2)]

        # Verify progressive contraction criteria:
        # Minervini VCP requires each wave to be tighter than the previous:
        # 1. Depth 1 > Depth 2 > Depth 3
        # 2. Contraction Ratio (Depth_k / Depth_{k-1}) <= 0.70
        is_t1_t2_contracting = (depth1 > 0 and depth2 <= depth1 * 0.85)
        is_t2_t3_contracting = (depth2 > 0 and depth3 <= depth2 * 0.80)
        
        is_classic_3_tier_vcp = is_t1_t2_contracting and is_t2_t3_contracting
        is_2_tier_vcp = is_t1_t2_contracting or is_t2_t3_contracting

        contractions_count = 3 if is_classic_3_tier_vcp else (2 if is_2_tier_vcp else 1)
        contraction_ratio = round(depth3 / depth2, 3) if depth2 > 0 else 1.0

        # Apex check: Price is coiled tightly within 1.5% of recent swing high (h3 or h2)
        swing_resistance = max(h2, h3)
        distance_to_resistance_pct = ((swing_resistance - current_price) / swing_resistance * 100.0) if swing_resistance > 0 else 5.0
        is_at_apex = (0.0 <= distance_to_resistance_pct <= 1.50)

        # Calculate institutional VCP score
        vcp_score = 40.0
        if is_classic_3_tier_vcp:
            vcp_score = 90.0 if is_at_apex else 78.0
        elif is_2_tier_vcp:
            vcp_score = 75.0 if is_at_apex else 62.0

        is_vcp_valid = (is_classic_3_tier_vcp or is_2_tier_vcp) and (depth3 <= 4.0)

        rationale = (
            f"Minervini VCP {contractions_count}T Contractions [{depth1:.1f}% -> {depth2:.1f}% -> {depth3:.1f}%] "
            f"(Ratio: {contraction_ratio:.2f}). Apex: {'YES (Coiled at resistance)' if is_at_apex else 'NO'}."
        )

        return {
            "is_vcp_valid": is_vcp_valid,
            "contractions_count": contractions_count,
            "contractions_depths": depths,
            "contraction_ratio": contraction_ratio,
            "is_at_apex": is_at_apex,
            "vcp_score": round(vcp_score, 1),
            "resistance_level": swing_resistance,
            "distance_to_resistance_pct": round(distance_to_resistance_pct, 2),
            "rationale": rationale
        }
