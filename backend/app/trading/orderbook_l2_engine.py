import logging
import math
import time
from typing import Dict, Any, List, Optional, Tuple
from app.workers.price_cache import PriceCache

logger = logging.getLogger("lumina.trading.orderbook_l2")

class OrderBookL2Engine:
    """
    Level 2 Order Book Microstructure & Depth Imbalance Engine.
    
    Provides microsecond-level order book analytics:
    1. Order Book Imbalance (OBI) on Top-5 and Top-20 depth levels:
       OBI = (Sum(BidQty) - Sum(AskQty)) / (Sum(BidQty) + Sum(AskQty))  [-1.0 to +1.0]
    2. Micro-Price Calculation (Volume-Weighted Top-of-Book):
       P_micro = (P_ask * Q_bid + P_bid * Q_ask) / (Q_bid + Q_ask)
    3. Bid / Ask Wall Detection within 0.50% range of mid-price
    4. Spread Compression Index (in basis points)
    5. Trade Microstructure Confluence Evaluator (bonus or penalty for breakout entries)
    """
    _instance: Optional['OrderBookL2Engine'] = None

    def __init__(self):
        self._history_snapshots: Dict[str, List[Dict[str, Any]]] = {}

    @classmethod
    def get_instance(cls) -> 'OrderBookL2Engine':
        if cls._instance is None:
            cls._instance = OrderBookL2Engine()
        return cls._instance

    def get_orderbook_metrics(self, symbol: str) -> Dict[str, Any]:
        """
        Retrieves or computes live L2 order book microstructure metrics.
        Uses cached L2 book if available, or derives an empirical model from live tick metrics.
        """
        clean_sym = symbol.replace("/", "").replace("-", "").strip().upper()
        cache = PriceCache.get_instance_sync()
        px = cache.get_price(clean_sym)
        if px <= 0:
            px = 92500.0 if "BTC" in clean_sym else 3500.0

        l2_data = cache.get_l2_orderbook(clean_sym)
        
        if l2_data and l2_data.get("bids") and l2_data.get("asks"):
            bids = l2_data["bids"]
            asks = l2_data["asks"]
        else:
            # Build empirical microstructure book from live price & volume
            bids, asks = self._generate_empirical_l2_levels(clean_sym, px)

        # 1. Top of Book
        best_bid = bids[0][0]
        best_ask = asks[0][0]
        best_bid_qty = bids[0][1]
        best_ask_qty = asks[0][1]
        
        mid_price = (best_bid + best_ask) / 2.0
        spread = max(0.0, best_ask - best_bid)
        spread_bps = round((spread / mid_price) * 10000.0, 2) if mid_price > 0 else 1.0

        # 2. Micro-Price
        tot_top_qty = best_bid_qty + best_ask_qty
        if tot_top_qty > 0:
            micro_price = (best_ask * best_bid_qty + best_bid * best_ask_qty) / tot_top_qty
        else:
            micro_price = mid_price
        micro_price_bias_bps = round(((micro_price - mid_price) / mid_price) * 10000.0, 2) if mid_price > 0 else 0.0

        # 3. Order Book Imbalance (OBI) - Top 5 and Top 20
        top5_bid_vol = sum(q for _, q in bids[:5])
        top5_ask_vol = sum(q for _, q in asks[:5])
        top5_tot = top5_bid_vol + top5_ask_vol
        obi_top5 = round((top5_bid_vol - top5_ask_vol) / top5_tot, 3) if top5_tot > 0 else 0.0

        top20_bid_vol = sum(q for _, q in bids[:20])
        top20_ask_vol = sum(q for _, q in asks[:20])
        top20_tot = top20_bid_vol + top20_ask_vol
        obi_top20 = round((top20_bid_vol - top20_ask_vol) / top20_tot, 3) if top20_tot > 0 else 0.0

        # 4. Bid / Ask Wall Detection
        # Identify levels where quantity is >= 2.5x the median level size
        all_qtys = [q for _, q in bids[:15]] + [q for _, q in asks[:15]]
        sorted_qtys = sorted(all_qtys)
        median_qty = sorted_qtys[len(sorted_qtys) // 2] if sorted_qtys else 1.0

        bid_wall = None
        for p, q in bids[:10]:
            if q >= median_qty * 2.5 and (mid_price - p) / mid_price <= 0.005:
                dist_pct = round(((mid_price - p) / mid_price) * 100.0, 3)
                bid_wall = {"price": p, "qty": q, "distance_pct": dist_pct, "wall_multiplier": round(q / median_qty, 1)}
                break

        ask_wall = None
        for p, q in asks[:10]:
            if q >= median_qty * 2.5 and (p - mid_price) / mid_price <= 0.005:
                dist_pct = round(((p - mid_price) / mid_price) * 100.0, 3)
                ask_wall = {"price": p, "qty": q, "distance_pct": dist_pct, "wall_multiplier": round(q / median_qty, 1)}
                break

        # 5. Pressure Interpretation
        if obi_top5 >= 0.30:
            pressure = "HEAVY_BUY_ABSORPTION"
        elif obi_top5 >= 0.12:
            pressure = "MODERATE_BUY_PRESSURE"
        elif obi_top5 <= -0.30:
            pressure = "HEAVY_SELL_ABSORPTION"
        elif obi_top5 <= -0.12:
            pressure = "MODERATE_SELL_PRESSURE"
        else:
            pressure = "BALANCED_EQUILIBRIUM"

        result = {
            "symbol": clean_sym,
            "mid_price": round(mid_price, 6),
            "best_bid": round(best_bid, 6),
            "best_ask": round(best_ask, 6),
            "spread_bps": spread_bps,
            "micro_price": round(micro_price, 6),
            "micro_price_bias_bps": micro_price_bias_bps,
            "obi_top5": obi_top5,
            "obi_top20": obi_top20,
            "pressure": pressure,
            "bid_wall": bid_wall,
            "ask_wall": ask_wall,
            "top5_bid_volume": round(top5_bid_vol, 2),
            "top5_ask_volume": round(top5_ask_vol, 2),
            "updated_at": time.time()
        }

        # Cache in rolling history
        if clean_sym not in self._history_snapshots:
            self._history_snapshots[clean_sym] = []
        self._history_snapshots[clean_sym].append(result)
        if len(self._history_snapshots[clean_sym]) > 30:
            self._history_snapshots[clean_sym].pop(0)

        return result

    def evaluate_microstructure_confluence(self, symbol: str, proposed_side: str) -> Dict[str, Any]:
        """
        Evaluates whether current L2 order book microstructure supports the proposed trade.
        Returns confidence bonus (+0.05 to -0.08) and execution advice.
        """
        metrics = self.get_orderbook_metrics(symbol)
        obi = metrics["obi_top5"]
        spread_bps = metrics["spread_bps"]
        micro_bias = metrics["micro_price_bias_bps"]
        bid_wall = metrics["bid_wall"]
        ask_wall = metrics["ask_wall"]

        side = proposed_side.upper()
        confidence_delta = 0.0
        reasons = []
        is_safe = True

        if side == "BUY":
            # Favorable: Buyers stacking bids, absorbing offers
            if obi >= 0.25:
                confidence_delta += 0.05
                reasons.append(f"Strong bid order book imbalance (+{obi:.2f}) indicates aggressive limit buyer absorption.")
            elif obi >= 0.10:
                confidence_delta += 0.02
                reasons.append(f"Moderate bid skew (+{obi:.2f}).")
            elif obi <= -0.30:
                # Heavy ask wall directly overhead
                confidence_delta -= 0.06
                reasons.append(f"Unfavorable book skew ({obi:.2f}): Heavy seller wall overhead.")

            if micro_bias > 1.0:
                confidence_delta += 0.02
                reasons.append(f"Micro-price leading spot (+{micro_bias:.1f} bps).")

            # Check for immediate ask wall blocking breakout
            if ask_wall and ask_wall["distance_pct"] < 0.25 and ask_wall["wall_multiplier"] >= 3.0:
                reasons.append(f"⚠️ Massive ask wall at ${ask_wall['price']} ({ask_wall['distance_pct']}% away, {ask_wall['wall_multiplier']}x depth). Risk of rejection.")
                confidence_delta -= 0.04
                if ask_wall["distance_pct"] < 0.10:
                    is_safe = False

            # Bid wall supporting entry
            if bid_wall and bid_wall["distance_pct"] < 0.35:
                reasons.append(f"🛡️ Bid wall support at ${bid_wall['price']} ({bid_wall['distance_pct']}% below mid) cushions downside.")
                confidence_delta += 0.02

        else: # SELL / SHORT
            if obi <= -0.25:
                confidence_delta += 0.05
                reasons.append(f"Strong ask order book imbalance ({obi:.2f}) indicates aggressive supply overhead.")
            elif obi <= -0.10:
                confidence_delta += 0.02
                reasons.append(f"Moderate ask skew ({obi:.2f}).")
            elif obi >= 0.30:
                confidence_delta -= 0.06
                reasons.append(f"Unfavorable book skew (+{obi:.2f}): Buyers heavily stacking bids below.")

            if micro_bias < -1.0:
                confidence_delta += 0.02
                reasons.append(f"Micro-price lagging spot ({micro_bias:.1f} bps).")

            if bid_wall and bid_wall["distance_pct"] < 0.25 and bid_wall["wall_multiplier"] >= 3.0:
                reasons.append(f"⚠️ Massive bid wall at ${bid_wall['price']} ({bid_wall['distance_pct']}% below, {bid_wall['wall_multiplier']}x depth). Risk of bounce.")
                confidence_delta -= 0.04
                if bid_wall["distance_pct"] < 0.10:
                    is_safe = False

        # Spread-to-ATR Friction Guard:
        # Blocks illiquid, wide-spread coins (like COW with 17.0 bps spread consuming 34% of ATR).
        # For highly liquid contracts (like SUI/SOL with spread < 8 bps), do not block normal 4-5 bps spreads.
        cache = PriceCache.get_instance_sync()
        ta = cache.get_technical_analysis(symbol)
        atr_val = float(ta.get("atr", 0.0))
        cur_px = float(cache.get_price(symbol) or 1.0)
        atr_bps = (atr_val / cur_px) * 10000.0 if cur_px > 0 and atr_val > 0 else 100.0
        friction_ratio = (spread_bps / atr_bps) if atr_bps > 0 else 0.0

        # True illiquid slippage trap: spread > 15 bps (0.15%), OR spread >= 8 bps consuming > 18% of ATR
        is_illiquid_trap = spread_bps > 15.0 or (spread_bps >= 8.0 and friction_ratio > 0.18)
        if is_illiquid_trap:
            is_safe = False
            reasons.append(
                f"🚫 [SPREAD FRICTION REJECTION] Spread ({spread_bps:.1f} bps) consumes {friction_ratio*100:.1f}% of ATR ({atr_bps:.1f} bps). High slippage risk."
            )
            confidence_delta -= 0.08
        elif spread_bps > 10.0 or friction_ratio > 0.12:
            confidence_delta -= 0.03
            reasons.append(f"Elevated spread ({spread_bps:.1f} bps). Moderate execution friction.")

        final_delta = round(max(-0.12, min(0.08, confidence_delta)), 3)

        return {
            "symbol": symbol,
            "side": side,
            "confidence_delta": final_delta,
            "is_safe_to_enter": is_safe,
            "obi_top5": obi,
            "pressure": metrics["pressure"],
            "spread_bps": spread_bps,
            "reasons": reasons,
            "metrics": metrics
        }

    def _generate_empirical_l2_levels(self, symbol: str, price: float) -> Tuple[List[Tuple[float, float]], List[Tuple[float, float]]]:
        """Generates realistic empirical 20-level order book for symbols pending live WS depth."""
        cache = PriceCache.get_instance_sync()
        tally = cache.get_update_tally(symbol)
        ta = cache.get_technical_analysis(symbol)
        rsi = float(ta.get("rsi", 50.0))
        rvol = cache.get_volume_surge_ratio(symbol)
        vol_24h = cache.get_volume_and_depth(symbol).get("volume_24h", 10_000_000.0)

        # Baseline spread: 1.5 to 3.0 bps for liquid crypto
        tick_size = 0.01 if price > 10.0 else (0.0001 if price > 0.1 else 0.000001)
        spread_amt = max(tick_size, round(price * 0.0002, 6))

        best_bid = round(price - (spread_amt / 2.0), 6)
        best_ask = round(price + (spread_amt / 2.0), 6)

        # Determine buyer/seller tilt based on RSI & RVOL
        bid_bias = 1.0 + ((rsi - 50.0) / 100.0) * 0.5 + (min(2.0, rvol - 1.0) * 0.2 if rvol > 1.0 else 0.0)
        ask_bias = 1.0 - ((rsi - 50.0) / 100.0) * 0.5

        base_qty = max(0.1, (vol_24h / 86400.0 / price) * 0.8)

        bids = []
        asks = []
        for i in range(20):
            step_spread = (i + 1) * spread_amt * 0.6
            p_bid = round(best_bid - step_spread, 6)
            p_ask = round(best_ask + step_spread, 6)
            
            # Level shape with occasional wall
            is_bid_wall = (i == 3 and bid_bias > 1.1)
            is_ask_wall = (i == 4 and ask_bias > 1.1)
            
            q_bid = round(base_qty * (1.0 + (i * 0.25)) * bid_bias * (3.5 if is_bid_wall else 1.0), 4)
            q_ask = round(base_qty * (1.0 + (i * 0.25)) * ask_bias * (3.5 if is_ask_wall else 1.0), 4)
            
            bids.append((p_bid, q_bid))
            asks.append((p_ask, q_ask))

        return bids, asks
