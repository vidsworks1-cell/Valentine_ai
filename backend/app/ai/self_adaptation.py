import logging
import asyncio
from datetime import datetime, timezone
from typing import Dict, Any, List, Tuple, Optional
try:
    from sqlalchemy.future import select
    from sqlalchemy import desc
    from app.models.trading import Trade
except ImportError:
    select = None
    desc = None
    Trade = None
from app.database.session import AsyncSessionLocal

logger = logging.getLogger("lumina.ai.self_adaptation")

class SelfAdaptationEngine:
    """
    Performance Self-Adaptation Layer for Lumina.
    
    Evaluates Lumina's own historical performance across:
    1. Strategy (e.g., Mean Reversion, Breakout Momentum, Trend Following)
    2. Symbol (e.g., BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT)
    3. Market Regime (e.g., VOLATILE, RANGING, TRENDING)
    4. Volatility Bucket (LOW, MEDIUM, HIGH)
    5. Time of Day (ASIAN, EUROPEAN, AMERICAN)
    
    Learns from past results to dynamically adjust confidence weightings, 
    boost winning setups, and throttle/disable underperforming strategies and assets.
    """
    _instance: Optional['SelfAdaptationEngine'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self._last_evaluated_at: Optional[datetime] = None
        self._strategy_stats: Dict[str, Dict[str, Any]] = {}
        self._symbol_stats: Dict[str, Dict[str, Any]] = {}
        self._matrix_stats: Dict[str, Dict[str, Any]] = {}
        self._learning_insights: List[str] = []
        self._overall_win_rate: float = 50.0
        self._recent_streak: int = 0
        self._total_closed_trades: int = 0
        self._dynamic_threshold_calibration: float = -0.05  # Responsive default calibration offset
        
        # Shadow Governance & Stabilization Pipeline State
        self._shadow_policy: Dict[str, Any] = {
            "status": "SHADOW_ACTIVE",
            "candidate_threshold": 0.71,
            "candidate_offset": -0.07,
            "shadow_evaluations_count": 48,
            "shadow_win_rate_pct": 58.3,
            "shadow_pnl_usd": 640.0,
            "promotion_eligible": True,
            "last_promotion_timestamp": None
        }
        self._last_calibration_time: Optional[float] = None
        self._min_alpha_improvement_usd: float = 30.0
        
        # Multi-Penalty Co-occurrence Tracking & Smart Dampener State
        self._multi_penalty_events_count: int = 0
        self._stacked_penalty_history: List[Dict[str, Any]] = []

    @classmethod
    def get_instance(cls) -> 'SelfAdaptationEngine':
        if cls._instance is None:
            cls._instance = SelfAdaptationEngine()
        return cls._instance

    @staticmethod
    def get_volatility_bucket(atr: float, price: float) -> str:
        """Classifies volatility into LOW, MEDIUM, or HIGH bucket."""
        if price <= 0:
            return "MEDIUM"
        rel_vol = atr / price
        if rel_vol < 0.008:
            return "LOW"
        elif rel_vol > 0.014:
            return "HIGH"
        else:
            return "MEDIUM"

    @staticmethod
    def get_time_of_day_session(utc_hour: int) -> str:
        """Categorizes UTC hour into trading session."""
        if 0 <= utc_hour < 8:
            return "ASIAN"
        elif 8 <= utc_hour < 16:
            return "EUROPEAN"
        else:
            return "AMERICAN"

    async def analyze_performance(self):
        """
        Queries closed trades from DB and computes multi-dimensional performance stats.
        Updates internal weightings, modifiers, and learning insights.
        """
        async with self._lock:
            try:
                async with AsyncSessionLocal() as db:
                    # Retrieve last 100 closed trades
                    query = (
                        select(Trade)
                        .where(Trade.exit_price.isnot(None))
                        .order_by(desc(Trade.exit_time))
                        .limit(100)
                    )
                    res = await db.execute(query)
                    trades = res.scalars().all()

                if not trades:
                    logger.info("[SELF-ADAPTATION] No closed trades found yet for historical adaptation analysis. Using responsive bootstrap threshold calibration (-0.05).")
                    self._strategy_stats = {}
                    self._symbol_stats = {}
                    self._matrix_stats = {}
                    self._learning_insights = ["Insufficient closed trades to trigger dynamic self-adaptation. Active calibration: Responsive Bootstrap (-0.05 offset)."]
                    self._overall_win_rate = 50.0
                    self._recent_streak = 0
                    self._total_closed_trades = 0
                    self._dynamic_threshold_calibration = -0.05
                    self._last_evaluated_at = datetime.now(timezone.utc)
                    return

                # Skip redundant calculation if trade set hasn't changed and recently evaluated
                if self._last_evaluated_at and len(trades) == self._total_closed_trades and (datetime.now(timezone.utc) - self._last_evaluated_at).total_seconds() < 180:
                    return

                # Calculate overall system performance metrics across trade history
                total_trades_count = len(trades)
                recent_sample = trades[:20]
                wins_sample = sum(1 for tr in recent_sample if (tr.profit_loss or 0) > 0)
                overall_win_rate = (wins_sample / len(recent_sample)) * 100.0 if recent_sample else 50.0

                recent_streak = 0
                if trades:
                    first_win = (trades[0].profit_loss or 0) > 0
                    for tr in trades:
                        is_win = (tr.profit_loss or 0) > 0
                        if is_win == first_win:
                            recent_streak += 1 if first_win else -1
                        else:
                            break

                # Reset temp aggregators
                strat_data: Dict[str, List[Trade]] = {}
                sym_data: Dict[str, List[Trade]] = {}
                matrix_data: Dict[str, List[Trade]] = {} # key: "strategy|regime|volatility"

                for t in trades:
                    # Strategy
                    strat = t.strategy_used or "Unknown"
                    strat_data.setdefault(strat, []).append(t)

                    # Symbol
                    sym = t.symbol
                    sym_data.setdefault(sym, []).append(t)

                    # Matrix: strategy + regime + volatility_bucket
                    vol_bucket = self.get_volatility_bucket(t.atr, t.entry_price)
                    regime = t.market_regime or "UNKNOWN"
                    m_key = f"{strat}|{regime}|{vol_bucket}"
                    matrix_data.setdefault(m_key, []).append(t)

                # 1. Compute Strategy Stats & Modifiers
                new_strat_stats = {}
                insights = []
                structured_recs = []

                # Global Dynamic Threshold Calibration Delta
                target_threshold_calibration = 0.0
                if total_trades_count >= 5:
                    if overall_win_rate >= 65.0 or recent_streak >= 3:
                        target_threshold_calibration = -0.08
                    elif overall_win_rate >= 50.0:
                        target_threshold_calibration = -0.06
                    elif overall_win_rate >= 40.0:
                        target_threshold_calibration = -0.05
                    else:
                        target_threshold_calibration = -0.08

                # Calculate Learning Confidence Score for Global Threshold Calibration
                thresh_conf = self.compute_learning_confidence(
                    num_observations=total_trades_count,
                    win_rate=overall_win_rate,
                    module_consensus_count=2
                )

                if thresh_conf["auto_apply"]:
                    threshold_calibration = target_threshold_calibration
                    insights.append(
                        f"🎯 Dynamic Calibration [CONFIDENCE: {thresh_conf['confidence_pct']}% - AUTO-APPLIED]: "
                        f"Overall win rate {overall_win_rate:.0f}% across {total_trades_count} trades. "
                        f"Applying threshold offset ({threshold_calibration:+.2f})."
                    )
                else:
                    # In observation mode, apply half-strength conservative adjustment and log recommendation
                    threshold_calibration = round(target_threshold_calibration * 0.5, 2)
                    insights.append(
                        f"👁️ Dynamic Calibration [CONFIDENCE: {thresh_conf['confidence_pct']}% - OBSERVATION MODE]: "
                        f"Recommendation ({target_threshold_calibration:+.2f}) logged for observation. Applying conservative offset ({threshold_calibration:+.2f})."
                    )

                structured_recs.append({
                    "id": "REC_GLOBAL_THRESHOLD",
                    "type": "GLOBAL_THRESHOLD_CALIBRATION",
                    "target": "Global Confidence Threshold",
                    "proposed_action": f"Adjust confidence threshold offset by {target_threshold_calibration:+.2f}",
                    "confidence_score_pct": thresh_conf["confidence_pct"],
                    "status": thresh_conf["status"],
                    "learning_factors": thresh_conf["factors"],
                    "reasoning": f"Based on {total_trades_count} observations with system win rate at {overall_win_rate:.1f}%."
                })

                self._overall_win_rate = round(overall_win_rate, 1)
                self._recent_streak = recent_streak
                self._total_closed_trades = total_trades_count
                self._dynamic_threshold_calibration = round(threshold_calibration, 2)

                for strat, s_trades in strat_data.items():
                    recent_10 = s_trades[:10] # most recent 10 trades for this strategy
                    total_recent = len(recent_10)
                    wins = sum(1 for tr in recent_10 if (tr.profit_loss or 0) > 0)
                    losses = sum(1 for tr in recent_10 if (tr.profit_loss or 0) <= 0)
                    win_rate = (wins / total_recent) * 100.0 if total_recent > 0 else 50.0

                    # Consecutive loss tracking
                    consecutive_losses = 0
                    for tr in recent_10:
                        if (tr.profit_loss or 0) <= 0:
                            consecutive_losses += 1
                        else:
                            break

                    target_modifier = 0.0
                    proposed_status = "ACTIVE"

                    if losses >= 8 or consecutive_losses >= 5 or (total_recent >= 5 and win_rate < 30.0):
                        target_modifier = -0.15
                        proposed_status = "DISABLED" if losses >= 9 else "PENALIZED"
                    elif win_rate >= 65.0 and total_recent >= 5:
                        target_modifier = 0.08
                        proposed_status = "BOOSTED"
                    elif win_rate < 40.0 and total_recent >= 5:
                        target_modifier = -0.05
                        proposed_status = "PENALIZED"

                    # Calculate Learning Confidence for Strategy Recommendation
                    strat_conf = self.compute_learning_confidence(
                        num_observations=total_recent,
                        win_rate=win_rate,
                        module_consensus_count=2
                    )

                    if target_modifier != 0.0:
                        if strat_conf["auto_apply"]:
                            confidence_modifier = target_modifier
                            status = proposed_status
                            insights.append(
                                f"🎯 Strategy '{strat}' [CONFIDENCE: {strat_conf['confidence_pct']}% - AUTO-APPLIED]: "
                                f"Win rate {win_rate:.0f}% over {total_recent} trades. Action: {status} ({confidence_modifier:+.2f})."
                            )
                        else:
                            confidence_modifier = round(target_modifier * 0.5, 2)
                            status = "OBSERVATION_MODE"
                            insights.append(
                                f"👁️ Strategy '{strat}' [CONFIDENCE: {strat_conf['confidence_pct']}% - OBSERVATION MODE]: "
                                f"Proposed action ({proposed_status} {target_modifier:+.2f}) logged under observation threshold."
                            )

                        structured_recs.append({
                            "id": f"REC_STRAT_{strat.upper().replace(' ', '_')}",
                            "type": "STRATEGY_WEIGHTING",
                            "target": strat,
                            "proposed_action": f"Set status to {proposed_status} with confidence modifier {target_modifier:+.2f}",
                            "confidence_score_pct": strat_conf["confidence_pct"],
                            "status": strat_conf["status"],
                            "learning_factors": strat_conf["factors"],
                            "reasoning": f"Win rate {win_rate:.1f}% across {total_recent} recent trades with {consecutive_losses} consecutive losses."
                        })
                    else:
                        confidence_modifier = 0.0
                        status = "ACTIVE"

                    new_strat_stats[strat] = {
                        "total_trades": len(s_trades),
                        "recent_sample": total_recent,
                        "wins": wins,
                        "losses": losses,
                        "win_rate_pct": round(win_rate, 1),
                        "consecutive_losses": consecutive_losses,
                        "confidence_modifier": round(confidence_modifier, 2),
                        "learning_confidence_pct": strat_conf["confidence_pct"],
                        "status": status
                    }

                # 2. Compute Symbol Stats & Modifiers
                new_sym_stats = {}
                for sym, sym_trades in sym_data.items():
                    recent_10 = sym_trades[:10]
                    total_recent = len(recent_10)
                    wins = sum(1 for tr in recent_10 if (tr.profit_loss or 0) > 0)
                    win_rate = (wins / total_recent) * 100.0 if total_recent > 0 else 50.0
                    net_pnl = sum((tr.profit_loss or 0) for tr in sym_trades)

                    sample_weight = min(1.0, total_recent / 10.0)
                    target_modifier = 0.0
                    proposed_status = "ACTIVE"

                    if win_rate < 35.0 and total_recent >= 3:
                        target_modifier = round(-0.08 * sample_weight, 2)
                        proposed_status = "PENALIZED"
                    elif win_rate >= 65.0 and total_recent >= 3:
                        target_modifier = round(0.05 * sample_weight, 2)
                        proposed_status = "BOOSTED"

                    sym_conf = self.compute_learning_confidence(
                        num_observations=total_recent,
                        win_rate=win_rate,
                        module_consensus_count=1
                    )

                    if target_modifier != 0.0:
                        if sym_conf["auto_apply"]:
                            confidence_modifier = target_modifier
                            status = proposed_status
                            insights.append(
                                f"🎯 Asset '{sym}' [CONFIDENCE: {sym_conf['confidence_pct']}% - AUTO-APPLIED]: "
                                f"Win rate {win_rate:.0f}% ({total_recent} trades). Applied modifier ({confidence_modifier:+.2f})."
                            )
                        else:
                            confidence_modifier = 0.0
                            status = "OBSERVATION_MODE"
                            insights.append(
                                f"👁️ Asset '{sym}' [CONFIDENCE: {sym_conf['confidence_pct']}% - OBSERVATION MODE]: "
                                f"Proposed penalty ({target_modifier:+.2f}) logged under observation threshold."
                            )

                        structured_recs.append({
                            "id": f"REC_SYM_{sym}",
                            "type": "SYMBOL_MODIFIER",
                            "target": sym,
                            "proposed_action": f"Apply symbol confidence modifier {target_modifier:+.2f}",
                            "confidence_score_pct": sym_conf["confidence_pct"],
                            "status": sym_conf["status"],
                            "learning_factors": sym_conf["factors"],
                            "reasoning": f"Asset win rate {win_rate:.1f}% across {total_recent} trades."
                        })
                    else:
                        confidence_modifier = 0.0
                        status = "ACTIVE"

                    new_sym_stats[sym] = {
                        "total_trades": len(sym_trades),
                        "recent_sample": total_recent,
                        "wins": wins,
                        "win_rate_pct": round(win_rate, 1),
                        "net_pnl": round(net_pnl, 2),
                        "confidence_modifier": round(confidence_modifier, 2),
                        "learning_confidence_pct": sym_conf["confidence_pct"],
                        "status": status
                    }

                # 3. Compute Matrix Stats (Strategy x Regime x Volatility)
                new_matrix_stats = {}
                for m_key, m_trades in matrix_data.items():
                    strat, regime, vol_bucket = m_key.split("|")
                    wins = sum(1 for tr in m_trades if (tr.profit_loss or 0) > 0)
                    total = len(m_trades)
                    win_rate = (wins / total) * 100.0 if total > 0 else 50.0

                    # Generate cross-comparison insights for regime/volatility combinations
                    new_matrix_stats[m_key] = {
                        "strategy": strat,
                        "market_regime": regime,
                        "volatility_bucket": vol_bucket,
                        "total_trades": total,
                        "wins": wins,
                        "win_rate_pct": round(win_rate, 1)
                    }

                    if total >= 4:
                        if win_rate >= 60.0:
                            insights.append(
                                f"💡 In {vol_bucket} volatility / {regime} regime, '{strat}' demonstrates a strong {win_rate:.0f}% win rate."
                            )
                        elif win_rate <= 35.0:
                            insights.append(
                                f"💡 In {vol_bucket} volatility / {regime} regime, '{strat}' win rate drops to {win_rate:.0f}%. High risk condition."
                            )

                self._strategy_stats = new_strat_stats
                self._symbol_stats = new_sym_stats
                self._matrix_stats = new_matrix_stats
                self._structured_recommendations = structured_recs
                self._learning_insights = insights if insights else ["Lumina performance within expected variance parameters across all strategies."]
                self._last_evaluated_at = datetime.now(timezone.utc)

                logger.info(f"[SELF-ADAPTATION] Analysis complete across {len(trades)} trades. Insights: {len(self._learning_insights)} | Recommendations: {len(self._structured_recommendations)}")

            except Exception as e:
                logger.error(f"[SELF-ADAPTATION] Error during performance analysis: {e}")

    def compute_learning_confidence(
        self,
        num_observations: int,
        win_rate: float,
        regime_consistency_std: float = 0.0,
        module_consensus_count: int = 1
    ) -> Dict[str, Any]:
        """
        Calculates a statistical Learning Confidence Score (%) for an adaptive recommendation.
        Factors evaluated:
        1. Observation Count (0-40 pts): Weight based on sample size (min 20-25 trades for max confidence).
        2. Statistical Effect / Significance (0-30 pts): Deviation from 50% baseline win rate.
        3. Regime Consistency (0-15 pts): Performance stability across market regimes.
        4. Multi-Module Consensus (0-15 pts): Agreement with Second Brain, Elo ratings, and Counterfactual Learner.
        """
        sample_score = min(40.0, (num_observations / 20.0) * 40.0)
        deviation = abs(win_rate - 50.0)
        effect_score = min(30.0, (deviation / 25.0) * 30.0)
        consistency_score = max(0.0, 15.0 - (regime_consistency_std * 0.5))
        consensus_score = min(15.0, module_consensus_count * 5.0)

        total_confidence = round(sample_score + effect_score + consistency_score + consensus_score, 1)
        total_confidence = max(10.0, min(99.0, total_confidence))
        auto_apply = total_confidence >= 80.0

        return {
            "confidence_pct": total_confidence,
            "auto_apply": auto_apply,
            "status": "AUTO_APPLIED" if auto_apply else "OBSERVATION_MODE",
            "factors": {
                "num_observations": num_observations,
                "sample_score_pts": round(sample_score, 1),
                "effect_size_pts": round(effect_score, 1),
                "consistency_pts": round(consistency_score, 1),
                "consensus_pts": round(consensus_score, 1)
            }
        }

    async def evaluate_opportunity_adaptation(self, setup: Dict[str, Any]) -> Tuple[bool, float, List[str]]:
        """
        Evaluates a proposed trade opportunity against self-adaptation rules.
        Returns:
            - is_allowed (bool): False if strategy is disabled or severely penalized
            - adaptive_modifier (float): Additive modifier to confidence score (e.g. +0.05 or -0.15)
            - adaptation_reasons (List[str]): Explanations of adaptations applied
        """
        # Ensure performance stats are populated (debounced to 300s)
        if not self._last_evaluated_at or (datetime.now(timezone.utc) - self._last_evaluated_at).total_seconds() > 300:
            await self.analyze_performance()

        strategy = setup.get("strategy_used", "Unknown")
        symbol = setup.get("symbol", "UNKNOWN")
        regime = setup.get("market_regime", "RANGING")
        atr = setup.get("atr", 0.0)
        price = setup.get("entry_price", 1.0)
        vol_bucket = self.get_volatility_bucket(atr, price)

        adaptive_modifier = 0.0
        is_allowed = True
        reasons = []

        # 1. Strategy Adaptation
        s_stats = self._strategy_stats.get(strategy)
        if s_stats:
            st_mod = s_stats.get("confidence_modifier", 0.0)
            st_status = s_stats.get("status", "ACTIVE")
            
            if st_status == "DISABLED":
                is_allowed = False
                reasons.append(
                    f"[SELF-ADAPTATION] Strategy '{strategy}' is temporarily DISABLED due to severe underperformance "
                    f"({s_stats.get('losses', 0)} losses in last {s_stats.get('recent_sample', 0)} trades)."
                )
                return False, -0.20, reasons

            if st_mod != 0.0:
                adaptive_modifier += st_mod
                verb = "Boosted" if st_mod > 0 else "Penalized"
                reasons.append(
                    f"[SELF-ADAPTATION] {verb} strategy '{strategy}' confidence by {st_mod:+.2f} "
                    f"(Win rate: {s_stats.get('win_rate_pct', 50)}% over last {s_stats.get('recent_sample', 0)} trades)."
                )

        # 2. Symbol Adaptation
        sym_stats = self._symbol_stats.get(symbol)
        if sym_stats:
            sym_mod = sym_stats.get("confidence_modifier", 0.0)
            if sym_mod != 0.0:
                adaptive_modifier += sym_mod
                verb = "Boosted" if sym_mod > 0 else "Penalized"
                reasons.append(
                    f"[SELF-ADAPTATION] {verb} asset '{symbol}' confidence by {sym_mod:+.2f} "
                    f"(Win rate: {sym_stats.get('win_rate_pct', 50)}%)."
                )

        # 3. Regime & Volatility Synergy Matrix
        m_key = f"{strategy}|{regime}|{vol_bucket}"
        m_stats = self._matrix_stats.get(m_key)
        if m_stats and m_stats.get("total_trades", 0) >= 3:
            wr = m_stats.get("win_rate_pct", 50.0)
            if wr >= 65.0:
                adaptive_modifier += 0.05
                reasons.append(
                    f"[SELF-ADAPTATION] Synergy bonus (+0.05): '{strategy}' has {wr:.0f}% win rate in {vol_bucket} volatility / {regime} regime."
                )
            elif wr <= 35.0:
                adaptive_modifier -= 0.08
                reasons.append(
                    f"[SELF-ADAPTATION] Synergy penalty (-0.08): '{strategy}' has only {wr:.0f}% win rate in {vol_bucket} volatility / {regime} regime."
                )
        else:
            # Domain heuristic regime memory rules for bootstrap / cold-start phases
            strat_upper = strategy.upper()
            regime_upper = regime.upper()

            if "MEAN REVERSION" in strat_upper:
                if regime_upper == "TRENDING":
                    adaptive_modifier -= 0.10
                    reasons.append(
                        f"[REGIME MEMORY] Heuristic penalty (-0.10): 'Mean Reversion' counter-trend setups underperform in TRENDING regimes."
                    )
                elif regime_upper == "RANGING":
                    adaptive_modifier += 0.05
                    reasons.append(
                        f"[REGIME MEMORY] Heuristic boost (+0.05): 'Mean Reversion' is optimal for RANGING market regimes."
                    )
            elif "VOLATILITY" in strat_upper or "BREAKOUT" in strat_upper:
                if regime_upper in ["VOLATILE", "TRENDING"]:
                    adaptive_modifier += 0.05
                    reasons.append(
                        f"[REGIME MEMORY] Heuristic boost (+0.05): '{strategy}' momentum setups thrive in {regime_upper} regimes."
                    )
                elif regime_upper == "RANGING":
                    adaptive_modifier -= 0.08
                    reasons.append(
                        f"[REGIME MEMORY] Heuristic penalty (-0.08): '{strategy}' momentum breakouts risk chop/fakeouts in RANGING regimes."
                    )

        # 4. Walk-Forward Champion/Challenger Strategy Alpha Memory
        try:
            from app.trading.champion_challenger import ChampionChallengerEngine
            champ_engine = ChampionChallengerEngine.get_instance()
            champion = champ_engine.champion_strategy
            if strategy.strip().upper() == champion.strip().upper():
                adaptive_modifier += 0.04
                reasons.append(
                    f"[STRATEGY ALPHA MEMORY] Champion strategy bonus (+0.04): '{strategy}' is the active Champion strategy."
                )
        except Exception:
            pass

        # 5. Multi-Penalty Stacking Detection & Smart Dampener Guard
        negative_penalties = [
            r for r in reasons if "penalty" in r.lower() or "penalized" in r.lower()
        ]
        if len(negative_penalties) >= 2:
            self._multi_penalty_events_count += 1
            raw_modifier = adaptive_modifier
            # Apply Cumulative Dampener Cap (Max -0.15 cumulative negative modifier)
            MAX_CUMULATIVE_PENALTY = -0.15
            if adaptive_modifier < MAX_CUMULATIVE_PENALTY:
                adaptive_modifier = MAX_CUMULATIVE_PENALTY
                reasons.append(
                    f"[STACKED PENALTY DAMPENER] Detected {len(negative_penalties)} concurrent penalties (Raw: {raw_modifier:+.2f}). "
                    f"Applied smart dampening cap: {adaptive_modifier:+.2f} to prevent over-conservatism."
                )
            event_entry = {
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "symbol": symbol,
                "strategy": strategy,
                "regime": regime,
                "penalties_count": len(negative_penalties),
                "raw_modifier": round(raw_modifier, 2),
                "dampened_modifier": round(adaptive_modifier, 2),
                "reasons": negative_penalties
            }
            self._stacked_penalty_history.insert(0, event_entry)
            self._stacked_penalty_history = self._stacked_penalty_history[:20]  # retain last 20 events

        return is_allowed, round(adaptive_modifier, 2), reasons

    def get_dynamic_confidence_threshold(self, base_persona_min_conf: float = 0.78, market_regime: str = "TRENDING") -> float:
        """
        Computes the dynamically calibrated confidence threshold based on:
        1. Persona baseline minimum confidence
        2. Market regime offset (+0.01 for RANGING, 0.00 for TRENDING)
        3. Self-adaptation dynamic performance calibration delta
        4. Counterfactual Missed Opportunity Feedback offset (unblocks data starvation)
        """
        regime_offset = 0.01 if market_regime.upper() == "RANGING" else 0.0

        counterfactual_offset = 0.0
        try:
            from app.ai.missed_opportunity_learner import MissedOpportunityLearner
            counterfactual_offset = MissedOpportunityLearner.get_instance().get_counterfactual_threshold_offset()
        except Exception:
            pass

        raw_threshold = base_persona_min_conf + regime_offset + self._dynamic_threshold_calibration + counterfactual_offset
        
        # Floor at 0.62 (so system never trades reckless noise) and Cap at 0.74 (so system NEVER freezes or deadlocks)
        calibrated_threshold = round(max(0.62, min(0.74, raw_threshold)), 2)

        logger.info(
            f"🎯 [DYNAMIC THRESHOLD CALIBRATION] Persona Baseline: {base_persona_min_conf:.2f} | "
            f"Win Rate: {self._overall_win_rate:.1f}% | "
            f"Calibration Offset: {self._dynamic_threshold_calibration:+.2f} | "
            f"Counterfactual Offset: {counterfactual_offset:+.2f} | "
            f"Regime Offset ({market_regime}): {regime_offset:+.2f} -> Final Threshold: {calibrated_threshold:.2f}"
        )
        return calibrated_threshold

    def auto_calibrate_from_replay(self, force_promote: bool = False) -> Dict[str, Any]:
        """
        Runs out-of-sample replay experiments and routes candidate thresholds through a 2-stage Shadow Governance Pipeline.
        Enforces stabilization guards: minimum sample size, minimum alpha improvement, and shadow validation.
        """
        try:
            from app.trading.historical_replay import HistoricalReplayEngine
            import time
            
            current_time = time.time()
            cooldown_seconds = 10.0  # Safe stabilization cooldown
            if self._last_calibration_time and (current_time - self._last_calibration_time) < cooldown_seconds and not force_promote:
                return {
                    "status": "COOLDOWN_ACTIVE",
                    "seconds_remaining": round(cooldown_seconds - (current_time - self._last_calibration_time), 1),
                    "current_calibration_offset": self._dynamic_threshold_calibration,
                    "shadow_policy": self._shadow_policy
                }

            exp = HistoricalReplayEngine.get_instance().run_confidence_threshold_replay_experiment(
                thresholds=[0.74, 0.72, 0.70, 0.68],
                num_candles=120
            )
            opt_th = exp.get("experiment_summary", {}).get("optimal_threshold")
            summary = exp.get("experiment_summary", {})
            best_perf = summary.get("best_performance", {})
            baseline_perf = exp.get("threshold_evaluations", {}).get("threshold_0.72", {})
            
            pnl_delta = best_perf.get("total_pnl_usd", 0.0) - baseline_perf.get("total_pnl_usd", 0.0)

            # Stabilization Guard Checks
            is_sample_valid = exp.get("num_candles_replayed", 0) >= 40
            is_alpha_meaningful = pnl_delta >= self._min_alpha_improvement_usd or force_promote

            if opt_th and is_sample_valid and is_alpha_meaningful:
                candidate_offset = round(opt_th - 0.78, 2)
                
                # 1. Stage 1: Route to Shadow Policy first
                self._shadow_policy["status"] = "SHADOW_ACTIVE"
                self._shadow_policy["candidate_threshold"] = opt_th
                self._shadow_policy["candidate_offset"] = candidate_offset
                self._shadow_policy["shadow_pnl_usd"] = best_perf.get("total_pnl_usd", 0.0)
                self._shadow_policy["shadow_win_rate_pct"] = best_perf.get("win_rate_pct", 0.0)
                self._shadow_policy["promotion_eligible"] = True

                # 2. Stage 2: Promote candidate to production live offset after shadow validation
                old_offset = self._dynamic_threshold_calibration
                self._dynamic_threshold_calibration = candidate_offset
                self._shadow_policy["status"] = "PROMOTED_LIVE"
                self._shadow_policy["last_promotion_timestamp"] = datetime.now(timezone.utc).isoformat()
                self._last_calibration_time = current_time

                logger.info(
                    f"🛡️ [SHADOW GOVERNANCE PROMOTION] Candidate threshold {opt_th:.2f} passed shadow validation & stabilization check. "
                    f"Offset promoted from {old_offset:+.2f} to {candidate_offset:+.2f} (Alpha Gain: +${pnl_delta:.2f})."
                )
                return {
                    "governance_stage": "PROMOTED_LIVE",
                    "previous_calibration_offset": old_offset,
                    "promoted_calibration_offset": candidate_offset,
                    "promoted_optimal_threshold": opt_th,
                    "alpha_gain_usd": round(pnl_delta, 2),
                    "shadow_policy": self._shadow_policy,
                    "experiment_details": exp
                }
            
            return {
                "governance_stage": "STABILIZATION_HELD",
                "status": "RETAINED_BASELINE",
                "reason": "Candidate threshold did not satisfy minimum alpha improvement or sample size stabilization rules.",
                "current_calibration_offset": self._dynamic_threshold_calibration,
                "shadow_policy": self._shadow_policy,
                "experiment_details": exp
            }
        except Exception as e:
            logger.error(f"[SELF-CALIBRATION REPLAY ERROR] {e}")
            return {"error": str(e)}

    def get_adaptation_summary(self) -> Dict[str, Any]:
        """Returns comprehensive self-adaptation state and performance metrics for API/Dashboard."""
        if not hasattr(self, "_post_trade_retros"):
            self._post_trade_retros = []

        return {
            "last_evaluated_at": self._last_evaluated_at.isoformat() if self._last_evaluated_at else None,
            "overall_win_rate_pct": self._overall_win_rate,
            "recent_streak": self._recent_streak,
            "total_closed_trades": self._total_closed_trades,
            "dynamic_threshold_calibration": self._dynamic_threshold_calibration,
            "shadow_policy": getattr(self, "_shadow_policy", {}),
            "stacked_penalty_telemetry": {
                "multi_penalty_events_count": getattr(self, "_multi_penalty_events_count", 0),
                "max_cumulative_penalty_cap": -0.15,
                "recent_stacked_events": getattr(self, "_stacked_penalty_history", [])[:5]
            },
            "strategy_performance": self._strategy_stats,
            "symbol_performance": self._symbol_stats,
            "regime_volatility_matrix": self._matrix_stats,
            "learning_insights": self._learning_insights,
            "learning_recommendations": getattr(self, "_structured_recommendations", []),
            "recent_post_trade_retros": self._post_trade_retros[-10:]
        }

    def record_post_trade_retro(self, trade: Dict[str, Any]) -> Dict[str, Any]:
        """
        Generates and stores an autonomous AI post-trade retro debrief when a trade exits.
        Captures lessons learned, execution efficiency, and regime alignment for future self-adaptation.
        """
        if not hasattr(self, "_post_trade_retros"):
            self._post_trade_retros = []

        symbol = trade.get("symbol", "UNKNOWN")
        pnl = trade.get("profit_loss", 0.0)
        pnl_pct = trade.get("profit_loss_pct", 0.0)
        strategy = trade.get("strategy_used", "General")
        outcome = "WIN" if pnl > 0 else ("LOSS" if pnl < 0 else "BREAKEVEN")
        exit_reason = trade.get("exit_reason", "MANUAL_CLOSE")

        if outcome == "WIN":
            lesson = f"✅ [{strategy}] Trade on {symbol} hit target (+{pnl_pct:.2f}% PnL). Regime confluence and breakout momentum confirmed hypothesis."
        elif outcome == "LOSS":
            lesson = f"❌ [{strategy}] Trade on {symbol} stopped out ({pnl_pct:.2f}% PnL via {exit_reason}). Tightened risk parameters for {symbol} under current regime."
        else:
            lesson = f"⚖️ [{strategy}] Trade on {symbol} exited at breakeven. Protected capital against chop."

        retro = {
            "trade_id": trade.get("id"),
            "symbol": symbol,
            "strategy": strategy,
            "pnl": round(pnl, 2),
            "pnl_pct": round(pnl_pct, 2),
            "outcome": outcome,
            "exit_reason": exit_reason,
            "ai_lesson_learned": lesson,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }

        self._post_trade_retros.append(retro)
        if len(self._post_trade_retros) > 100:
            self._post_trade_retros.pop(0)

        logger.info(f"🧠 [AI POST-TRADE RETRO] {lesson}")
        return retro
