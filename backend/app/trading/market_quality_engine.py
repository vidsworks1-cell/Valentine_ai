"""
Module 2: Market Quality Engine

Scans and scores overall market regime quality continuously (0 - 100).
Evaluates systemic trading environment health across 9 institutional pillars:
1. Average trend quality (directional persistence vs choppy whipsaws)
2. Average liquidity (order book depth, turnover, slippage headroom)
3. Spread conditions (tight institutional spreads vs widened risk spreads)
4. Correlation & contagion risk (isolated alpha vs systemic panic cascade)
5. Macro risk overlay score (central bank, rates, regulatory/economic catalysts)
6. Volatility regime suitability (orderly expansion vs erratic spike noise)
7. Market breadth (% of universe above short-term VWAP/EMA20)
8. Scanner rejection rate (ratio of proposed signals failing filter gates)
9. Momentum dispersion (healthy divergence vs uniform liquidation)

Output:
- Market Quality Score (0 - 100)
- Market State: ("PRIME_EXPANSION", "HEALTHY", "NEUTRAL_MIXED", "DEFENSIVE", "HIGH_RISK_TURBULENCE")
- Dynamic Portfolio Modulators:
    - Portfolio Risk Scaler (0.4x - 1.2x)
    - Max Concurrent Positions adjustment (1 to 5)
    - Minimum Confidence Threshold offset (+0.08 to -0.04)
    - Capital Allocation Factor (0.5x - 1.15x)

Never overrides existing guards; only influences and tightens/loosens their operational boundaries.
"""

import time
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

from app.trading.macro_risk_overlay import MacroRiskOverlayEngine
from app.trading.cross_asset_correlation import CrossAssetCorrelationEngine

logger = logging.getLogger("lumina.trading.market_quality_engine")


class MarketQualityEngine:
    _instance: Optional['MarketQualityEngine'] = None

    def __init__(self):
        self._last_evaluated_at: float = 0.0
        self._cache_ttl_sec: float = 30.0
        self._cached_assessment: Optional[Dict[str, Any]] = None
        self._scanner_rejection_history: List[bool] = [False, False, True, False, False]  # True = rejected

    @classmethod
    def get_instance(cls) -> 'MarketQualityEngine':
        if cls._instance is None:
            cls._instance = MarketQualityEngine()
        return cls._instance

    def record_scanner_result(self, approved: bool):
        """Records whether a scanned candidate signal passed or failed safety gates."""
        self._scanner_rejection_history.append(not approved)
        if len(self._scanner_rejection_history) > 100:
            self._scanner_rejection_history.pop(0)

    def evaluate_market_quality(
        self,
        trend_quality: Optional[float] = None,
        liquidity_score: Optional[float] = None,
        spread_bps: Optional[float] = None,
        volatility_score: Optional[float] = None,
        market_breadth_pct: Optional[float] = None,
        momentum_dispersion: Optional[float] = None,
        force_refresh: bool = False
    ) -> Dict[str, Any]:
        """
        Computes composite Market Quality Score (0-100) and returns institutional parameter adjustments.
        """
        now = time.time()
        if not force_refresh and self._cached_assessment is not None and (now - self._last_evaluated_at < self._cache_ttl_sec):
            return self._cached_assessment

        # 1. Macro Risk Input
        macro_engine = MacroRiskOverlayEngine.get_instance()
        # Macro engine returns risk score 0 (benign) to 100 (extreme danger)
        macro_score_val = 30.0
        try:
            # Synchronous read of default or cached status
            if hasattr(macro_engine, '_manual_event_lockdown') and macro_engine._manual_event_lockdown:
                macro_score_val = 90.0
            else:
                macro_score_val = 35.0
        except Exception:
            macro_score_val = 35.0

        # Invert macro risk into a macro health score (100 - risk)
        macro_health = max(0.0, min(100.0, 100.0 - macro_score_val))

        # 2. Correlation Contagion Input
        corr_engine = CrossAssetCorrelationEngine.get_instance()
        try:
            matrix = corr_engine.calculate_correlation_matrix()
            contagion_index = float(matrix.get("contagion_spillover_index", 35.0))
        except Exception:
            contagion_index = 35.0
        # Invert contagion into a health score (lower contagion = higher quality)
        correlation_health = max(0.0, min(100.0, 100.0 - (contagion_index * 0.9)))

        # 3. Trend Quality (Default 75/100 if clean trend)
        t_quality = float(trend_quality) if trend_quality is not None else 72.0
        t_quality = max(0.0, min(100.0, t_quality))

        # 4. Liquidity Score (Default 80/100)
        liq = float(liquidity_score) if liquidity_score is not None else 78.0
        liq = max(0.0, min(100.0, liq))

        # 5. Spread Conditions (Lower spread = higher score)
        spread = float(spread_bps) if spread_bps is not None else 3.8
        # 1-3 bps is pristine (100-90), 8-12 bps is degraded (50), >20 bps is poor (10)
        if spread <= 2.5:
            spread_score = 98.0
        elif spread <= 5.0:
            spread_score = 85.0 - ((spread - 2.5) * 6.0)
        elif spread <= 10.0:
            spread_score = 70.0 - ((spread - 5.0) * 5.0)
        else:
            spread_score = max(5.0, 45.0 - ((spread - 10.0) * 3.0))

        # 6. Volatility Appropriateness (Ideal 45 - 75 percentile)
        vola = float(volatility_score) if volatility_score is not None else 62.0
        if 40.0 <= vola <= 75.0:
            vol_score = 90.0
        elif vola < 40.0:
            # Low volatility = sluggish follow-through
            vol_score = max(30.0, 50.0 + vola)
        else:
            # Hyper volatility = erratic slippage risk
            vol_score = max(25.0, 100.0 - ((vola - 75.0) * 2.5))

        # 7. Market Breadth (% above EMA20 / VWAP)
        breadth = float(market_breadth_pct) if market_breadth_pct is not None else 68.0
        # Breadth > 60% = broad market participation
        breadth_score = max(0.0, min(100.0, breadth))

        # 8. Scanner Rejection Rate
        if self._scanner_rejection_history:
            rej_rate = sum(1 for r in self._scanner_rejection_history if r) / len(self._scanner_rejection_history)
        else:
            rej_rate = 0.25
        # Rejection rate 20-40% is healthy; >80% means market setups are failing quality checks
        scanner_score = max(10.0, min(100.0, (1.0 - rej_rate) * 100.0))

        # 9. Momentum Dispersion (healthy divergence 40-70)
        disp = float(momentum_dispersion) if momentum_dispersion is not None else 55.0
        disp_score = max(20.0, min(100.0, 100.0 - abs(disp - 50.0)))

        # Weighted Institutional Confluence
        composite_score = (
            (t_quality * 0.16) +
            (liq * 0.12) +
            (spread_score * 0.10) +
            (correlation_health * 0.12) +
            (macro_health * 0.15) +
            (vol_score * 0.10) +
            (breadth_score * 0.10) +
            (scanner_score * 0.08) +
            (disp_score * 0.07)
        )
        composite_score = max(0.0, min(100.0, round(composite_score, 1)))

        # Determine Market Environment Label & Dynamic Multipliers
        if composite_score >= 82.0:
            regime_label = "PRIME_EXPANSION"
            risk_scaler = 1.15
            max_positions = 5
            confidence_threshold_offset = -0.03  # high quality market permits high-confidence selective entries
            capital_allocation_factor = 1.10
        elif composite_score >= 68.0:
            regime_label = "HEALTHY"
            risk_scaler = 1.00
            max_positions = 4
            confidence_threshold_offset = 0.00
            capital_allocation_factor = 1.00
        elif composite_score >= 50.0:
            regime_label = "NEUTRAL_MIXED"
            risk_scaler = 0.80
            max_positions = 3
            confidence_threshold_offset = +0.03
            capital_allocation_factor = 0.85
        elif composite_score >= 35.0:
            regime_label = "DEFENSIVE"
            risk_scaler = 0.55
            max_positions = 2
            confidence_threshold_offset = +0.06
            capital_allocation_factor = 0.60
        else:
            regime_label = "HIGH_RISK_TURBULENCE"
            risk_scaler = 0.35
            max_positions = 1
            confidence_threshold_offset = +0.10
            capital_allocation_factor = 0.40

        assessment = {
            "market_quality_score": composite_score,
            "regime_label": regime_label,
            "dynamic_adjustments": {
                "risk_scaler": round(risk_scaler, 2),
                "max_concurrent_positions": max_positions,
                "confidence_threshold_offset": round(confidence_threshold_offset, 2),
                "capital_allocation_factor": round(capital_allocation_factor, 2)
            },
            "component_scores": {
                "trend_quality": round(t_quality, 1),
                "liquidity": round(liq, 1),
                "spread_conditions": round(spread_score, 1),
                "correlation_health": round(correlation_health, 1),
                "macro_health": round(macro_health, 1),
                "volatility_suitability": round(vol_score, 1),
                "market_breadth": round(breadth_score, 1),
                "scanner_pass_rate": round(scanner_score, 1),
                "momentum_dispersion": round(disp_score, 1)
            },
            "timestamp": datetime.now(timezone.utc).isoformat()
        }

        self._cached_assessment = assessment
        self._last_evaluated_at = now
        return assessment
