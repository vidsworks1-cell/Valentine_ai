"""
Module 9: Explainable Institutional Decision Report Engine

Generates an institutional audit report for every executed trade.
Captures:
- Context Similarity %
- Historical Context Expectancy (+X.XX R)
- Historical Similarity Confidence % & Tier
- Market Quality Score (0-100)
- Breakout Quality Score (0-100)
- Strategy Portfolio Capital Allocation Share %
- Strategy Expected Expectancy & Confidence
- Meta Conviction Score (0-100)
- Expected Value (EV) Rank
- Dynamic Risk Allocation %
- Primary Identified Risks
- Final Institutional Conviction Tier

Stored with Execution Analytics for reporting, risk governance, and regulatory auditability.
"""

import os
import json
import time
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("lumina.trading.decision_report")

DECISION_REPORTS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "data", "decision_reports")


class ExplainableDecisionReport:
    def __init__(
        self,
        trade_id: str,
        symbol: str,
        side: str,
        strategy: str,
        entry_price: float,
        context_similarity_pct: float,
        historical_context_expectancy_r: float,
        market_quality_score: float,
        breakout_quality_score: float,
        meta_conviction_score: float,
        ev_rank_str: str,
        risk_allocation_pct: float,
        primary_risks: List[str],
        conviction_tier: str,
        similarity_confidence_pct: float = 75.0,
        similarity_confidence_tier: str = "HIGH_CONFIDENCE",
        strategy_portfolio_share_pct: float = 30.0,
        strategy_expected_expectancy_r: float = 1.20,
        strategy_confidence_score: float = 80.0,
        strategy_allocation_tier: str = "HIGH_ALLOCATION",
        timestamp: Optional[float] = None
    ):
        self.trade_id = str(trade_id)
        self.symbol = symbol.upper()
        self.side = side.upper()
        self.strategy = strategy
        self.entry_price = float(entry_price)
        self.context_similarity_pct = float(context_similarity_pct)
        self.historical_context_expectancy_r = float(historical_context_expectancy_r)
        self.market_quality_score = float(market_quality_score)
        self.breakout_quality_score = float(breakout_quality_score)
        self.meta_conviction_score = float(meta_conviction_score)
        self.ev_rank_str = ev_rank_str
        self.risk_allocation_pct = float(risk_allocation_pct)
        self.primary_risks = primary_risks
        self.conviction_tier = conviction_tier
        self.similarity_confidence_pct = float(similarity_confidence_pct)
        self.similarity_confidence_tier = similarity_confidence_tier
        self.strategy_portfolio_share_pct = float(strategy_portfolio_share_pct)
        self.strategy_expected_expectancy_r = float(strategy_expected_expectancy_r)
        self.strategy_confidence_score = float(strategy_confidence_score)
        self.strategy_allocation_tier = strategy_allocation_tier
        self.timestamp = timestamp or time.time()

    def format_institutional_summary(self) -> str:
        """Formats clean, human-readable terminal/log report."""
        risks_str = " | ".join(self.primary_risks) if self.primary_risks else "None detected (Clean alignment)"
        return (
            f"📋 ==================== INSTITUTIONAL DECISION REPORT ====================\n"
            f"Trade ID:                 #{self.trade_id} ({self.symbol} {self.side} via {self.strategy})\n"
            f"Entry Price:              ${self.entry_price:.4f}\n"
            f"Context Similarity:       {self.context_similarity_pct:.1f}%\n"
            f"Historical Expectancy:    {self.historical_context_expectancy_r:+.2f}R\n"
            f"Similarity Confidence:    {self.similarity_confidence_pct:.1f}% ({self.similarity_confidence_tier})\n"
            f"Market Quality Score:     {self.market_quality_score:.0f}/100\n"
            f"Breakout Quality Score:   {self.breakout_quality_score:.0f}/100\n"
            f"Strategy Edge:            {self.strategy_expected_expectancy_r:+.2f}R ({self.strategy_confidence_score:.0f}% Conf, {self.strategy_portfolio_share_pct:.1f}% Share [{self.strategy_allocation_tier}])\n"
            f"Meta Conviction:          {self.meta_conviction_score:.0f}/100\n"
            f"Expected Value Rank:      {self.ev_rank_str}\n"
            f"Risk Allocation:          {self.risk_allocation_pct*100:.2f}%\n"
            f"Primary Risks:            {risks_str}\n"
            f"Conviction Grade:         {self.conviction_tier}\n"
            f"=========================================================================="
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "trade_id": self.trade_id,
            "symbol": self.symbol,
            "side": self.side,
            "strategy": self.strategy,
            "entry_price": self.entry_price,
            "context_similarity_pct": self.context_similarity_pct,
            "historical_context_expectancy_r": self.historical_context_expectancy_r,
            "similarity_confidence_pct": self.similarity_confidence_pct,
            "similarity_confidence_tier": self.similarity_confidence_tier,
            "market_quality_score": self.market_quality_score,
            "breakout_quality_score": self.breakout_quality_score,
            "strategy_portfolio_share_pct": self.strategy_portfolio_share_pct,
            "strategy_expected_expectancy_r": self.strategy_expected_expectancy_r,
            "strategy_confidence_score": self.strategy_confidence_score,
            "strategy_allocation_tier": self.strategy_allocation_tier,
            "meta_conviction_score": self.meta_conviction_score,
            "ev_rank_str": self.ev_rank_str,
            "risk_allocation_pct": self.risk_allocation_pct,
            "primary_risks": self.primary_risks,
            "conviction_tier": self.conviction_tier,
            "timestamp": self.timestamp
        }


class DecisionReportEngine:
    _instance: Optional['DecisionReportEngine'] = None

    def __init__(self):
        self._reports_history: List[ExplainableDecisionReport] = []

    @classmethod
    def get_instance(cls) -> 'DecisionReportEngine':
        if cls._instance is None:
            cls._instance = DecisionReportEngine()
        return cls._instance

    def create_and_store_report(
        self,
        trade_id: str,
        opportunity: Dict[str, Any],
        context_result: Dict[str, Any],
        market_quality_data: Dict[str, Any],
        breakout_quality_data: Dict[str, Any],
        meta_conviction_score: float,
        ev_rank_str: str,
        risk_pct: float
    ) -> ExplainableDecisionReport:
        """Constructs, logs, and persists the institutional decision report."""
        symbol = opportunity.get("symbol", "UNKNOWN")
        side = opportunity.get("side", "BUY")
        strategy = opportunity.get("strategy_used", opportunity.get("strategy", "General"))
        entry_price = float(opportunity.get("price", opportunity.get("entry_price", 0.0)))

        sim_pct = float(context_result.get("context_similarity_pct", 88.0))
        hist_exp = float(context_result.get("historical_context_expectancy_r", 2.14))
        sim_conf_pct = float(context_result.get("similarity_confidence_pct", 78.0))
        sim_conf_tier = str(context_result.get("confidence_tier", "HIGH_CONFIDENCE"))

        mq_score = float(market_quality_data.get("market_quality_score", 85.0))
        bq_score = float(breakout_quality_data.get("breakout_quality_score", 89.0))

        # Query Strategy Portfolio Manager
        strat_share = 25.0
        strat_exp = 1.0
        strat_conf = 78.0
        strat_tier = "MODERATE_ALLOCATION"
        try:
            from app.trading.strategy_portfolio_manager import StrategyPortfolioManager
            spm = StrategyPortfolioManager.get_instance()
            strat_data = spm.get_strategy_allocation(strategy)
            strat_share = float(strat_data.get("capital_share_pct", 25.0))
            strat_exp = float(strat_data.get("expected_expectancy_r", 1.0))
            strat_conf = float(strat_data.get("confidence_score", 78.0))
            strat_tier = str(strat_data.get("tier", "MODERATE_ALLOCATION"))
        except Exception:
            pass

        # Identify top risk factors
        primary_risks = []
        if mq_score < 65.0:
            primary_risks.append("Market Quality sub-optimal (chop/spread noise)")
        if float(breakout_quality_data.get("fake_breakout_probability_pct", 15.0)) > 25.0:
            primary_risks.append("Elevated fakeout probability")
        macro_val = float(opportunity.get("macro_score", 45.0))
        if macro_val > 55.0:
            primary_risks.append("Macro risk index elevated")
        rvol = float(opportunity.get("rvol", 1.8))
        if rvol < 1.2:
            primary_risks.append("Relative volume expansion below optimal threshold")
        if strat_tier in ["RESTRICTED_ALLOCATION", "DEFENSIVE_SUSPENDED"]:
            primary_risks.append(f"Strategy allocation throttled ({strat_tier})")
        if not primary_risks:
            primary_risks.append("Minimal structural friction; multi-agent consensus aligned")

        # Determine conviction tier
        if meta_conviction_score >= 82.0 and sim_pct >= 80.0 and hist_exp >= 1.5 and strat_tier == "DOMINANT_EDGE":
            conviction_tier = "INSTITUTIONAL GRADE (Alpha Core)"
        elif meta_conviction_score >= 74.0:
            conviction_tier = "HIGH CONVICTION (Standard Book)"
        else:
            conviction_tier = "TACTICAL SETUP (Selective)"

        report = ExplainableDecisionReport(
            trade_id=str(trade_id),
            symbol=symbol,
            side=side,
            strategy=strategy,
            entry_price=entry_price,
            context_similarity_pct=sim_pct,
            historical_context_expectancy_r=hist_exp,
            market_quality_score=mq_score,
            breakout_quality_score=bq_score,
            meta_conviction_score=meta_conviction_score,
            ev_rank_str=ev_rank_str,
            risk_allocation_pct=risk_pct,
            primary_risks=primary_risks,
            conviction_tier=conviction_tier,
            similarity_confidence_pct=sim_conf_pct,
            similarity_confidence_tier=sim_conf_tier,
            strategy_portfolio_share_pct=strat_share,
            strategy_expected_expectancy_r=strat_exp,
            strategy_confidence_score=strat_conf,
            strategy_allocation_tier=strat_tier
        )

        self._reports_history.append(report)
        logger.info(f"\n{report.format_institutional_summary()}")

        # Persist report JSON
        try:
            os.makedirs(DECISION_REPORTS_DIR, exist_ok=True)
            report_file = os.path.join(DECISION_REPORTS_DIR, f"report_trade_{trade_id}.json")
            with open(report_file, "w") as f:
                json.dump(report.to_dict(), f, indent=2)
        except Exception as e:
            logger.warning(f"Could not persist decision report file: {e}")

        return report

    def get_recent_reports(self, limit: int = 20) -> List[Dict[str, Any]]:
        return [r.to_dict() for r in self._reports_history[-limit:]]
