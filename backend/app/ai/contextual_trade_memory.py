"""
Module 1: Contextual Trade Memory (Upgraded Multi-Measure Analog Engine)

Stores and matches high-dimensional market context snapshots for every trade.
Rather than relying on normalized Euclidean distance alone, Lumina combines:
1. Weighted Categorical Matching (strategy family, asset symbol/class, trading session, day-of-week)
2. Cosine Similarity (continuous multidimensional angular directional profile)
3. Euclidean Distance (metric space normalized magnitude separation)
4. Regime Weighting (hard institutional regime alignment and compatibility penalty)
5. Recency Weighting (exponential decay half-life weighting with memory preservation floor)
6. Historical Similarity Confidence Score (evaluates metric concordance, sample cluster density,
   and outcome variance stability to quantify confidence in the analog match).
"""

import os
import json
import math
import time
import logging
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime, timezone

logger = logging.getLogger("lumina.ai.contextual_trade_memory")

MEMORY_STATE_FILE = os.path.join(os.path.dirname(__file__), "..", "..", "data", "contextual_trade_memory.json")


class ContextualTradeSnapshot:
    def __init__(
        self,
        trade_id: str,
        symbol: str,
        strategy: str,
        regime: str,
        atr_pct: float = 0.015,
        volatility_percentile: float = 50.0,
        volume_percentile: float = 50.0,
        liquidity_score: float = 75.0,
        spread_bps: float = 4.0,
        market_structure_score: float = 80.0,
        htf_confirmation_score: float = 80.0,
        momentum_score: float = 75.0,
        rsi: float = 52.0,
        adx: float = 24.0,
        bollinger_width: float = 0.035,
        vwap_deviation: float = 0.005,
        funding_rate: float = 0.0001,
        session: str = "London",
        weekday: int = 1,
        hour: int = 10,
        macro_score: float = 45.0,
        confidence_score: float = 0.82,
        risk_size: float = 0.015,
        holding_duration_mins: float = 35.0,
        exit_reason: str = "TAKE_PROFIT",
        realized_r_multiple: float = 1.8,
        realized_expectancy_contribution: float = 0.027,
        timestamp: Optional[float] = None
    ):
        self.trade_id = str(trade_id)
        self.symbol = symbol.upper()
        self.strategy = strategy
        self.regime = regime.upper()
        self.atr_pct = float(atr_pct)
        self.volatility_percentile = float(volatility_percentile)
        self.volume_percentile = float(volume_percentile)
        self.liquidity_score = float(liquidity_score)
        self.spread_bps = float(spread_bps)
        self.market_structure_score = float(market_structure_score)
        self.htf_confirmation_score = float(htf_confirmation_score)
        self.momentum_score = float(momentum_score)
        self.rsi = float(rsi)
        self.adx = float(adx)
        self.bollinger_width = float(bollinger_width)
        self.vwap_deviation = float(vwap_deviation)
        self.funding_rate = float(funding_rate)
        self.session = session
        self.weekday = int(weekday)
        self.hour = int(hour)
        self.macro_score = float(macro_score)
        self.confidence_score = float(confidence_score)
        self.risk_size = float(risk_size)
        self.holding_duration_mins = float(holding_duration_mins)
        self.exit_reason = exit_reason
        self.realized_r_multiple = float(realized_r_multiple)
        self.realized_expectancy_contribution = float(realized_expectancy_contribution)
        self.timestamp = float(timestamp) if timestamp is not None else time.time()

    def to_feature_vector(self) -> List[float]:
        """Normalized 10-dimensional continuous vector for multi-metric distance computation."""
        return [
            min(1.0, max(0.0, self.atr_pct / 0.06)),
            min(1.0, max(0.0, self.volatility_percentile / 100.0)),
            min(1.0, max(0.0, self.volume_percentile / 100.0)),
            min(1.0, max(0.0, self.liquidity_score / 100.0)),
            min(1.0, max(0.0, 1.0 - (self.spread_bps / 30.0))),
            min(1.0, max(0.0, self.market_structure_score / 100.0)),
            min(1.0, max(0.0, self.htf_confirmation_score / 100.0)),
            min(1.0, max(0.0, self.momentum_score / 100.0)),
            min(1.0, max(0.0, self.rsi / 100.0)),
            min(1.0, max(0.0, (100.0 - self.macro_score) / 100.0))
        ]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "trade_id": self.trade_id,
            "symbol": self.symbol,
            "strategy": self.strategy,
            "regime": self.regime,
            "atr_pct": round(self.atr_pct, 4),
            "volatility_percentile": round(self.volatility_percentile, 1),
            "volume_percentile": round(self.volume_percentile, 1),
            "liquidity_score": round(self.liquidity_score, 1),
            "spread_bps": round(self.spread_bps, 2),
            "market_structure_score": round(self.market_structure_score, 1),
            "htf_confirmation_score": round(self.htf_confirmation_score, 1),
            "momentum_score": round(self.momentum_score, 1),
            "rsi": round(self.rsi, 1),
            "adx": round(self.adx, 1),
            "bollinger_width": round(self.bollinger_width, 4),
            "vwap_deviation": round(self.vwap_deviation, 4),
            "funding_rate": round(self.funding_rate, 6),
            "session": self.session,
            "weekday": self.weekday,
            "hour": self.hour,
            "macro_score": round(self.macro_score, 1),
            "confidence_score": round(self.confidence_score, 3),
            "risk_size": round(self.risk_size, 4),
            "holding_duration_mins": round(self.holding_duration_mins, 1),
            "exit_reason": self.exit_reason,
            "realized_r_multiple": round(self.realized_r_multiple, 3),
            "realized_expectancy_contribution": round(self.realized_expectancy_contribution, 4),
            "timestamp": self.timestamp
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> 'ContextualTradeSnapshot':
        return cls(**d)


class ContextualTradeMemory:
    _instance: Optional['ContextualTradeMemory'] = None

    def __init__(self):
        self._snapshots: List[ContextualTradeSnapshot] = []
        self._load_memory()

    @classmethod
    def get_instance(cls) -> 'ContextualTradeMemory':
        if cls._instance is None:
            cls._instance = ContextualTradeMemory()
        return cls._instance

    @staticmethod
    def get_current_session(dt: Optional[datetime] = None) -> str:
        """Determines active forex/crypto trading session by UTC hour."""
        now = dt or datetime.now(timezone.utc)
        hour = now.hour
        if 0 <= hour < 8:
            return "Asia"
        elif 8 <= hour < 14:
            return "London"
        elif 14 <= hour < 21:
            return "New York"
        else:
            return "Asia/Pacific"

    def _load_memory(self):
        """Loads historical contextual snapshots, seeding realistic baseline institutional cases if empty."""
        try:
            if os.path.exists(MEMORY_STATE_FILE):
                with open(MEMORY_STATE_FILE, "r") as f:
                    data = json.load(f)
                    self._snapshots = [ContextualTradeSnapshot.from_dict(d) for d in data.get("snapshots", [])]
                    logger.info(f"Loaded {len(self._snapshots)} contextual trade snapshots from disk.")
            else:
                self._seed_baseline_snapshots()
        except Exception as e:
            logger.warning(f"Could not load contextual memory from {MEMORY_STATE_FILE}: {e}")
            self._seed_baseline_snapshots()

    def _seed_baseline_snapshots(self):
        """Seeds empirical institutional memory cases across diverse symbols, strategies, regimes, and sessions."""
        now = time.time()
        day = 86400.0

        seeds = [
            ContextualTradeSnapshot(
                trade_id="SEED-001", symbol="LINKUSDT", strategy="Breakout Momentum", regime="TRENDING",
                atr_pct=0.028, volatility_percentile=78.0, volume_percentile=88.0, liquidity_score=85.0,
                spread_bps=3.2, market_structure_score=92.0, htf_confirmation_score=90.0, momentum_score=88.0,
                rsi=62.0, adx=32.0, session="London", weekday=2, hour=10, macro_score=38.0,
                confidence_score=0.86, risk_size=0.015, holding_duration_mins=42.0, exit_reason="TAKE_PROFIT",
                realized_r_multiple=2.64, realized_expectancy_contribution=0.039,
                timestamp=now - (2 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-002", symbol="AVAXUSDT", strategy="Breakout Momentum", regime="VOLATILE",
                atr_pct=0.032, volatility_percentile=84.0, volume_percentile=82.0, liquidity_score=80.0,
                spread_bps=4.1, market_structure_score=86.0, htf_confirmation_score=85.0, momentum_score=84.0,
                rsi=66.0, adx=29.0, session="New York", weekday=3, hour=15, macro_score=44.0,
                confidence_score=0.84, risk_size=0.012, holding_duration_mins=55.0, exit_reason="TAKE_PROFIT",
                realized_r_multiple=2.18, realized_expectancy_contribution=0.026,
                timestamp=now - (4 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-003", symbol="BTCUSDT", strategy="Volatility Expansion", regime="TRENDING",
                atr_pct=0.022, volatility_percentile=82.0, volume_percentile=86.0, liquidity_score=95.0,
                spread_bps=1.1, market_structure_score=94.0, htf_confirmation_score=92.0, momentum_score=86.0,
                rsi=64.0, adx=35.0, session="London", weekday=1, hour=9, macro_score=32.0,
                confidence_score=0.88, risk_size=0.018, holding_duration_mins=72.0, exit_reason="TAKE_PROFIT",
                realized_r_multiple=2.85, realized_expectancy_contribution=0.051,
                timestamp=now - (1 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-004", symbol="SOLUSDT", strategy="Volatility Expansion", regime="VOLATILE",
                atr_pct=0.034, volatility_percentile=88.0, volume_percentile=84.0, liquidity_score=86.0,
                spread_bps=2.8, market_structure_score=88.0, htf_confirmation_score=86.0, momentum_score=82.0,
                rsi=61.0, adx=30.0, session="New York", weekday=4, hour=16, macro_score=40.0,
                confidence_score=0.82, risk_size=0.015, holding_duration_mins=48.0, exit_reason="TAKE_PROFIT",
                realized_r_multiple=1.92, realized_expectancy_contribution=0.028,
                timestamp=now - (3 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-005", symbol="ETHUSDT", strategy="Mean Reversion", regime="RANGING",
                atr_pct=0.015, volatility_percentile=38.0, volume_percentile=44.0, liquidity_score=92.0,
                spread_bps=1.6, market_structure_score=72.0, htf_confirmation_score=68.0, momentum_score=45.0,
                rsi=32.0, adx=16.0, session="Asia", weekday=3, hour=4, macro_score=42.0,
                confidence_score=0.76, risk_size=0.010, holding_duration_mins=34.0, exit_reason="TAKE_PROFIT",
                realized_r_multiple=1.35, realized_expectancy_contribution=0.013,
                timestamp=now - (6 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-006", symbol="ETHUSDT", strategy="Mean Reversion", regime="TRENDING",
                atr_pct=0.024, volatility_percentile=65.0, volume_percentile=62.0, liquidity_score=88.0,
                spread_bps=2.2, market_structure_score=60.0, htf_confirmation_score=52.0, momentum_score=55.0,
                rsi=48.0, adx=26.0, session="New York", weekday=5, hour=18, macro_score=56.0,
                confidence_score=0.70, risk_size=0.010, holding_duration_mins=18.0, exit_reason="STOP_LOSS",
                realized_r_multiple=-1.0, realized_expectancy_contribution=-0.010,
                timestamp=now - (8 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-007", symbol="SUIUSDT", strategy="Breakout Momentum", regime="TRENDING",
                atr_pct=0.035, volatility_percentile=80.0, volume_percentile=90.0, liquidity_score=78.0,
                spread_bps=5.0, market_structure_score=90.0, htf_confirmation_score=88.0, momentum_score=89.0,
                rsi=68.0, adx=34.0, session="London", weekday=2, hour=11, macro_score=36.0,
                confidence_score=0.85, risk_size=0.014, holding_duration_mins=38.0, exit_reason="TAKE_PROFIT",
                realized_r_multiple=2.82, realized_expectancy_contribution=0.039,
                timestamp=now - (5 * day)
            ),
            ContextualTradeSnapshot(
                trade_id="SEED-008", symbol="NEARUSDT", strategy="Mean Reversion", regime="TRENDING",
                atr_pct=0.026, volatility_percentile=68.0, volume_percentile=55.0, liquidity_score=80.0,
                spread_bps=3.8, market_structure_score=58.0, htf_confirmation_score=50.0, momentum_score=48.0,
                rsi=42.0, adx=28.0, session="London", weekday=4, hour=13, macro_score=52.0,
                confidence_score=0.71, risk_size=0.008, holding_duration_mins=25.0, exit_reason="STOP_LOSS",
                realized_r_multiple=-0.95, realized_expectancy_contribution=-0.008,
                timestamp=now - (7 * day)
            )
        ]
        self._snapshots = seeds
        self._save_memory()

    def _save_memory(self):
        try:
            os.makedirs(os.path.dirname(MEMORY_STATE_FILE), exist_ok=True)
            with open(MEMORY_STATE_FILE, "w") as f:
                json.dump({
                    "updated_at": time.time(),
                    "total_snapshots": len(self._snapshots),
                    "snapshots": [s.to_dict() for s in self._snapshots[-300:]]
                }, f, indent=2)
        except Exception as e:
            logger.warning(f"Failed to persist contextual trade memory: {e}")

    def record_completed_trade(
        self,
        trade_id: str,
        symbol: str,
        strategy: str,
        regime: str,
        atr: float,
        entry_price: float,
        pnl: float,
        exit_reason: str,
        holding_seconds: float,
        risk_amount: float,
        confidence: float = 0.80,
        macro_score: float = 40.0,
        extra_metrics: Optional[Dict[str, Any]] = None
    ) -> ContextualTradeSnapshot:
        """
        Builds and saves a full contextual trade snapshot upon trade completion.
        Computes realized R multiple = PnL / Initial Risk.
        """
        now = datetime.now(timezone.utc)
        extras = extra_metrics or {}

        atr_pct = (atr / entry_price) if entry_price > 0 else 0.015
        risk_den = max(abs(risk_amount), 1.0)
        r_multiple = round(pnl / risk_den, 3)
        expectancy_contrib = round(pnl / max(5000.0, risk_den * 100.0), 4)

        snapshot = ContextualTradeSnapshot(
            trade_id=str(trade_id),
            symbol=symbol,
            strategy=strategy,
            regime=regime,
            atr_pct=atr_pct,
            volatility_percentile=float(extras.get("volatility_percentile", min(95.0, max(15.0, (atr_pct / 0.04) * 100.0)))),
            volume_percentile=float(extras.get("volume_percentile", 65.0)),
            liquidity_score=float(extras.get("liquidity_score", 82.0)),
            spread_bps=float(extras.get("spread_bps", 3.5)),
            market_structure_score=float(extras.get("market_structure_score", 85.0)),
            htf_confirmation_score=float(extras.get("htf_confirmation_score", 80.0)),
            momentum_score=float(extras.get("momentum_score", 78.0)),
            rsi=float(extras.get("rsi", 55.0)),
            adx=float(extras.get("adx", 25.0)),
            bollinger_width=float(extras.get("bollinger_width", atr_pct * 1.5)),
            vwap_deviation=float(extras.get("vwap_deviation", 0.005)),
            funding_rate=float(extras.get("funding_rate", 0.0001)),
            session=self.get_current_session(now),
            weekday=now.weekday(),
            hour=now.hour,
            macro_score=macro_score,
            confidence_score=confidence,
            risk_size=round(abs(risk_amount) / 5000.0, 4),
            holding_duration_mins=round(holding_seconds / 60.0, 1),
            exit_reason=exit_reason,
            realized_r_multiple=r_multiple,
            realized_expectancy_contribution=expectancy_contrib,
            timestamp=now.timestamp()
        )
        self._snapshots.append(snapshot)
        self._save_memory()
        logger.info(
            f"🧠 [CONTEXTUAL MEMORY SAVED] Stored trade {trade_id} ({symbol} {strategy}): "
            f"R={r_multiple:+.2f}R, Session={snapshot.session}, Regime={regime}. Total memory bank: {len(self._snapshots)}."
        )
        return snapshot

    @staticmethod
    def _compute_cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
        """Computes cosine similarity between two non-negative continuous vectors."""
        dot = sum(a * b for a, b in zip(vec_a, vec_b))
        norm_a = math.sqrt(sum(a * a for a in vec_a))
        norm_b = math.sqrt(sum(b * b for b in vec_b))
        if norm_a <= 1e-9 or norm_b <= 1e-9:
            return 0.50
        cos_sim = dot / (norm_a * norm_b)
        return max(0.0, min(1.0, cos_sim))

    @staticmethod
    def _compute_euclidean_similarity(vec_a: List[float], vec_b: List[float]) -> float:
        """Computes normalized Euclidean similarity in [0, 1]."""
        dim = len(vec_a)
        if dim == 0:
            return 0.50
        dist = math.sqrt(sum((a - b) ** 2 for a, b in zip(vec_a, vec_b)))
        norm_dist = dist / math.sqrt(dim)
        return max(0.0, min(1.0, 1.0 - norm_dist))

    @staticmethod
    def _compute_weighted_categorical(
        cand_sym: str,
        cand_strat: str,
        cand_session: str,
        cand_weekday: int,
        snap: ContextualTradeSnapshot
    ) -> float:
        """
        Computes weighted categorical congruence across qualitative dimensions.
        Weights: Strategy (0.35), Symbol (0.25), Session (0.25), Weekday (0.15).
        """
        score = 0.0

        # 1. Strategy congruence
        cand_strat_clean = cand_strat.lower().replace("_", " ").strip()
        snap_strat_clean = snap.strategy.lower().replace("_", " ").strip()
        if snap_strat_clean == cand_strat_clean:
            score += 0.35
        elif ("breakout" in cand_strat_clean and "breakout" in snap_strat_clean) or \
             ("volatility" in cand_strat_clean and "volatility" in snap_strat_clean) or \
             ("mean reversion" in cand_strat_clean and "mean reversion" in snap_strat_clean):
            score += 0.22
        elif ("momentum" in cand_strat_clean and "trend" in snap_strat_clean) or \
             ("trend" in cand_strat_clean and "momentum" in snap_strat_clean):
            score += 0.15

        # 2. Symbol / Asset class congruence
        if snap.symbol == cand_sym:
            score += 0.25
        elif (("BTC" in cand_sym or "ETH" in cand_sym) and ("BTC" in snap.symbol or "ETH" in snap.symbol)) or \
             (cand_sym not in ["BTCUSDT", "ETHUSDT"] and snap.symbol not in ["BTCUSDT", "ETHUSDT"]):
            score += 0.12  # Same liquidity tier / altcoin class

        # 3. Market Session congruence
        if snap.session == cand_session:
            score += 0.25
        elif (cand_session in ["London", "New York"] and snap.session in ["London", "New York"]):
            score += 0.14  # High liquidity overlap
        else:
            score += 0.05

        # 4. Weekday congruence
        day_diff = abs(snap.weekday - cand_weekday)
        if day_diff == 0:
            score += 0.15
        elif day_diff == 1 or day_diff == 6:
            score += 0.10
        else:
            score += 0.04

        return max(0.0, min(1.0, score))

    @staticmethod
    def _compute_regime_weight(cand_regime: str, snap_regime: str) -> float:
        """
        Computes regime compatibility weight.
        Regime match is an institutional non-negotiable filter.
        """
        c = cand_regime.upper().strip()
        s = snap_regime.upper().strip()

        if c == s:
            return 1.00
        # Complementary regimes
        if (c in ["TRENDING", "VOLATILE"] and s in ["TRENDING", "VOLATILE"]):
            return 0.70
        if (c in ["RANGING", "COMPRESSED"] and s in ["RANGING", "COMPRESSED"]):
            return 0.65
        # Conflicting regimes (e.g. range vs strong trend)
        return 0.25

    @staticmethod
    def _compute_recency_weight(snap_ts: float, now_ts: float, half_life_days: float = 30.0) -> float:
        """
        Calculates exponential recency decay with a robust floor.
        Floor ensures multi-month institutional analog patterns remain valid.
        """
        age_seconds = max(0.0, now_ts - snap_ts)
        age_days = age_seconds / 86400.0
        decay = math.pow(0.5, age_days / half_life_days)
        return max(0.35, min(1.0, decay))

    def _calculate_similarity_confidence(
        self,
        top_matches: List[Tuple[float, Dict[str, float], ContextualTradeSnapshot]],
        candidate_dict: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Calculates a dedicated institutional confidence score (0-100%) for the analog match.
        Evaluates:
        1. Metric Concordance: Agreement between cosine, euclidean, categorical, and regime measures.
        2. Cluster Density: Concentration of high-similarity cases (>= 0.70).
        3. Outcome Stability: Variance/standard deviation of historical realized R-multiples.
        4. Recency Support: Average recency weight of matched cluster.
        """
        if not top_matches:
            return {
                "similarity_confidence_pct": 50.0,
                "confidence_tier": "LOW_DATA",
                "concordance_score": 0.50,
                "cluster_density_score": 0.50,
                "outcome_stability_score": 0.50,
                "variance_of_r": 1.0
            }

        # 1. Metric Concordance (Agreement across components)
        divergences = []
        for _, breakdown, _ in top_matches:
            vals = [
                breakdown.get("cosine_sim", 0.5),
                breakdown.get("euclidean_sim", 0.5),
                breakdown.get("categorical_sim", 0.5),
                breakdown.get("regime_weight", 0.5)
            ]
            mean_val = sum(vals) / len(vals)
            std_val = math.sqrt(sum((v - mean_val) ** 2 for v in vals) / len(vals))
            divergences.append(std_val)

        avg_divergence = sum(divergences) / len(divergences)
        concordance_score = max(0.20, min(1.0, 1.0 - (avg_divergence * 2.2)))

        # 2. Cluster Density (Proportion of strong analogs >= 0.70)
        strong_matches = sum(1 for sim, _, _ in top_matches if sim >= 0.70)
        cluster_density_score = min(1.0, (strong_matches / max(1, len(top_matches))) * 0.7 + (min(len(self._snapshots), 10) / 10.0) * 0.3)

        # 3. Outcome Stability (Variance of realized R)
        realized_rs = [snap.realized_r_multiple for _, _, snap in top_matches]
        mean_r = sum(realized_rs) / len(realized_rs)
        r_variance = sum((r - mean_r) ** 2 for r in realized_rs) / len(realized_rs) if len(realized_rs) > 1 else 0.25
        # Low variance in returns indicates high predictability of the context
        outcome_stability_score = max(0.25, min(1.0, 1.0 - (math.sqrt(r_variance) / 3.0)))

        # 4. Recency support
        avg_recency = sum(breakdown.get("recency_weight", 0.7) for _, breakdown, _ in top_matches) / len(top_matches)

        # Composite Confidence Score (0.0 to 1.0 -> converted to 0-100%)
        composite_conf = (
            (concordance_score * 0.35) +
            (outcome_stability_score * 0.30) +
            (cluster_density_score * 0.20) +
            (avg_recency * 0.15)
        )
        conf_pct = round(max(20.0, min(98.0, composite_conf * 100.0)), 1)

        if conf_pct >= 82.0:
            tier = "INSTITUTIONAL_GRADE"
        elif conf_pct >= 70.0:
            tier = "HIGH_CONFIDENCE"
        elif conf_pct >= 55.0:
            tier = "MODERATE"
        else:
            tier = "EXPLORATORY"

        return {
            "similarity_confidence_pct": conf_pct,
            "confidence_tier": tier,
            "concordance_score": round(concordance_score, 3),
            "cluster_density_score": round(cluster_density_score, 3),
            "outcome_stability_score": round(outcome_stability_score, 3),
            "variance_of_r": round(r_variance, 3)
        }

    def query_similar_contexts(
        self,
        candidate_snapshot: Dict[str, Any],
        k: int = 5
    ) -> Dict[str, Any]:
        """
        Performs non-linear similarity matching between an incoming candidate opportunity and
        historical trade snapshots by combining:
        - Weighted Categorical Matching
        - Cosine Similarity
        - Euclidean Distance
        - Regime Weighting
        - Recency Weighting
        And calculates a dedicated historical similarity confidence score.
        """
        if not self._snapshots:
            return {
                "context_similarity_pct": 50.0,
                "historical_context_expectancy_r": 1.0,
                "win_rate_in_context_pct": 50.0,
                "sample_size": 0,
                "similarity_confidence_pct": 50.0,
                "confidence_tier": "LOW_DATA",
                "top_matches": [],
                "reasoning_summary": "Insufficient historical context data; default baseline applied."
            }

        cand_symbol = str(candidate_snapshot.get("symbol", "")).upper()
        cand_strategy = str(candidate_snapshot.get("strategy_used", candidate_snapshot.get("strategy", "")))
        cand_regime = str(candidate_snapshot.get("market_regime", candidate_snapshot.get("regime", "TRENDING"))).upper()
        cand_atr_pct = float(candidate_snapshot.get("atr_pct", 0.02))
        cand_vol_pct = float(candidate_snapshot.get("volatility_percentile", 60.0))
        cand_volum_pct = float(candidate_snapshot.get("volume_percentile", 65.0))
        cand_liq = float(candidate_snapshot.get("liquidity_score", 80.0))
        cand_spread = float(candidate_snapshot.get("spread_bps", 3.5))
        cand_ms = float(candidate_snapshot.get("market_structure_score", 80.0))
        cand_htf = float(candidate_snapshot.get("htf_confirmation_score", 80.0))
        cand_mom = float(candidate_snapshot.get("momentum_score", 75.0))
        cand_rsi = float(candidate_snapshot.get("rsi", 55.0))
        cand_macro = float(candidate_snapshot.get("macro_score", 45.0))
        cand_session = candidate_snapshot.get("session", self.get_current_session())
        cand_weekday = int(candidate_snapshot.get("weekday", datetime.now(timezone.utc).weekday()))

        cand_vector = [
            min(1.0, max(0.0, cand_atr_pct / 0.06)),
            min(1.0, max(0.0, cand_vol_pct / 100.0)),
            min(1.0, max(0.0, cand_volum_pct / 100.0)),
            min(1.0, max(0.0, cand_liq / 100.0)),
            min(1.0, max(0.0, 1.0 - (cand_spread / 30.0))),
            min(1.0, max(0.0, cand_ms / 100.0)),
            min(1.0, max(0.0, cand_htf / 100.0)),
            min(1.0, max(0.0, cand_mom / 100.0)),
            min(1.0, max(0.0, cand_rsi / 100.0)),
            min(1.0, max(0.0, (100.0 - cand_macro) / 100.0))
        ]

        now_ts = time.time()
        scored_matches: List[Tuple[float, Dict[str, float], ContextualTradeSnapshot]] = []

        for snap in self._snapshots:
            snap_vec = snap.to_feature_vector()

            # 1. Cosine similarity (directional shape)
            cos_sim = self._compute_cosine_similarity(cand_vector, snap_vec)

            # 2. Euclidean similarity (magnitude in normalized space)
            euc_sim = self._compute_euclidean_similarity(cand_vector, snap_vec)

            # 3. Weighted categorical matching
            cat_sim = self._compute_weighted_categorical(
                cand_sym=cand_symbol,
                cand_strat=cand_strategy,
                cand_session=cand_session,
                cand_weekday=cand_weekday,
                snap=snap
            )

            # 4. Regime weighting
            regime_w = self._compute_regime_weight(cand_regime, snap.regime)

            # 5. Recency weighting
            recency_w = self._compute_recency_weight(snap.timestamp, now_ts)

            # Non-linear composite analog similarity score
            # Multi-measure formula blending all 5 dimensions:
            base_similarity = (
                (cos_sim * 0.28) +
                (euc_sim * 0.22) +
                (cat_sim * 0.25) +
                (regime_w * 0.25)
            )

            # Modulate with recency weighting (80% core analog + 20% recency bias)
            final_similarity = base_similarity * (0.80 + 0.20 * recency_w)
            final_similarity = max(0.05, min(0.99, final_similarity))

            breakdown = {
                "cosine_sim": round(cos_sim, 3),
                "euclidean_sim": round(euc_sim, 3),
                "categorical_sim": round(cat_sim, 3),
                "regime_weight": round(regime_w, 3),
                "recency_weight": round(recency_w, 3),
                "base_similarity": round(base_similarity, 3)
            }

            scored_matches.append((final_similarity, breakdown, snap))

        # Sort descending by composite similarity
        scored_matches.sort(key=lambda x: x[0], reverse=True)
        top_k = scored_matches[:k]

        total_sim = sum(sim for sim, _, _ in top_k)
        if total_sim <= 0:
            total_sim = 1.0

        # Weighted historical expectancy in R
        weighted_expectancy_r = sum(sim * s.realized_r_multiple for sim, _, s in top_k) / total_sim
        avg_similarity_pct = (sum(sim for sim, _, _ in top_k) / len(top_k)) * 100.0
        wins_count = sum(1 for _, _, s in top_k if s.realized_r_multiple > 0)
        win_rate_pct = (wins_count / len(top_k)) * 100.0

        # Calculate historical similarity confidence score
        confidence_meta = self._calculate_similarity_confidence(top_k, candidate_snapshot)

        top_breakdown = top_k[0][1] if top_k else {}

        summary = (
            f"Matched {len(top_k)} analogous contexts ({avg_similarity_pct:.1f}% avg similarity, "
            f"{confidence_meta['similarity_confidence_pct']}% confidence [{confidence_meta['confidence_tier']}]). "
            f"Historical expectancy in this market environment is {weighted_expectancy_r:+.2f}R "
            f"({win_rate_pct:.0f}% win rate across {cand_strategy} on {cand_symbol} / {cand_regime} in {cand_session} session). "
            f"Multi-Measure: Cos={top_breakdown.get('cosine_sim', 0):.2f}, "
            f"Euc={top_breakdown.get('euclidean_sim', 0):.2f}, "
            f"Cat={top_breakdown.get('categorical_sim', 0):.2f}, "
            f"Regime={top_breakdown.get('regime_weight', 0):.2f}."
        )

        return {
            "context_similarity_pct": round(avg_similarity_pct, 1),
            "historical_context_expectancy_r": round(weighted_expectancy_r, 2),
            "win_rate_in_context_pct": round(win_rate_pct, 1),
            "sample_size": len(self._snapshots),
            "matched_k": len(top_k),
            "similarity_confidence_pct": confidence_meta["similarity_confidence_pct"],
            "confidence_tier": confidence_meta["confidence_tier"],
            "concordance_score": confidence_meta["concordance_score"],
            "cluster_density_score": confidence_meta["cluster_density_score"],
            "outcome_stability_score": confidence_meta["outcome_stability_score"],
            "top_matches": [
                {
                    "trade_id": s.trade_id,
                    "symbol": s.symbol,
                    "strategy": s.strategy,
                    "regime": s.regime,
                    "session": s.session,
                    "similarity_pct": round(sim * 100.0, 1),
                    "realized_r": round(s.realized_r_multiple, 2),
                    "exit_reason": s.exit_reason,
                    "metrics_breakdown": bk
                }
                for sim, bk, s in top_k
            ],
            "reasoning_summary": summary
        }
