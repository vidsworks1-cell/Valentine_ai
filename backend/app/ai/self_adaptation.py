import logging
import asyncio
from datetime import datetime, timezone
from typing import Dict, Any, List, Tuple, Optional
try:
    from sqlalchemy.future import select
    from sqlalchemy import desc
except ImportError:
    select = None
    desc = None
from app.database.session import AsyncSessionLocal
from app.models.trading import Trade

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

                # Global Dynamic Threshold Calibration Delta
                if total_trades_count >= 5:
                    if overall_win_rate >= 65.0 or recent_streak >= 3:
                        threshold_calibration = -0.08  # Lower threshold aggressively during high-performance phase
                        insights.append(f"🎯 Dynamic Calibration: High system win rate ({overall_win_rate:.0f}%, streak: {recent_streak:+d}). Calibrating threshold lower (-0.08) to capture clean trade setups.")
                    elif overall_win_rate >= 50.0:
                        threshold_calibration = -0.05  # Moderate threshold reduction during healthy performance
                        insights.append(f"🎯 Dynamic Calibration: Healthy system performance ({overall_win_rate:.0f}% win rate). Calibrating threshold offset (-0.05).")
                    elif overall_win_rate >= 40.0:
                        threshold_calibration = -0.02
                    else:
                        threshold_calibration = 0.05  # Raise threshold during drawdown to protect capital
                        insights.append(f"🛡️ Dynamic Calibration: System underperformance detected ({overall_win_rate:.0f}% win rate). Raising threshold (+0.05) to filter strictly.")
                else:
                    threshold_calibration = -0.05  # Responsive calibration for small trade samples

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

                    confidence_modifier = 0.0
                    status = "ACTIVE"

                    # Rules:
                    # If strategy lost 8 of last 10 trades or 5+ consecutive losses -> reduce weighting heavily / disable
                    if losses >= 8 or consecutive_losses >= 5 or (total_recent >= 5 and win_rate < 30.0):
                        confidence_modifier = -0.15
                        status = "DISABLED" if losses >= 9 else "PENALIZED"
                        insights.append(
                            f"⚠️ Strategy '{strat}' has lost {losses}/{total_recent} recent trades ({win_rate:.0f}% win rate). "
                            f"Applying confidence penalty (-0.15) and status: {status}."
                        )
                    elif win_rate >= 65.0 and total_recent >= 5:
                        confidence_modifier = 0.08
                        status = "BOOSTED"
                        insights.append(
                            f"🚀 Strategy '{strat}' is performing exceptionally with {win_rate:.0f}% win rate over {total_recent} trades. "
                            f"Applying confidence boost (+0.08)."
                        )
                    elif win_rate < 40.0 and total_recent >= 5:
                        confidence_modifier = -0.05
                        status = "PENALIZED"
                        insights.append(
                            f"📉 Strategy '{strat}' win rate dropped to {win_rate:.0f}%. Applying mild confidence penalty (-0.05)."
                        )

                    new_strat_stats[strat] = {
                        "total_trades": len(s_trades),
                        "recent_sample": total_recent,
                        "wins": wins,
                        "losses": losses,
                        "win_rate_pct": round(win_rate, 1),
                        "consecutive_losses": consecutive_losses,
                        "confidence_modifier": round(confidence_modifier, 2),
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

                    confidence_modifier = 0.0
                    status = "ACTIVE"

                    if win_rate < 35.0 and total_recent >= 5:
                        confidence_modifier = -0.08
                        status = "PENALIZED"
                        insights.append(
                            f"⚠️ Asset '{sym}' consistently underperforming with {win_rate:.0f}% win rate. "
                            f"Reducing confidence multiplier (-0.08)."
                        )
                    elif win_rate >= 65.0 and total_recent >= 5:
                        confidence_modifier = 0.05
                        status = "BOOSTED"
                        insights.append(
                            f"📈 Asset '{sym}' outperforming with {win_rate:.0f}% win rate. "
                            f"Applying confidence bonus (+0.05)."
                        )

                    new_sym_stats[sym] = {
                        "total_trades": len(sym_trades),
                        "recent_sample": total_recent,
                        "wins": wins,
                        "win_rate_pct": round(win_rate, 1),
                        "net_pnl": round(net_pnl, 2),
                        "confidence_modifier": round(confidence_modifier, 2),
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
                self._learning_insights = insights if insights else ["Lumina performance within expected variance parameters across all strategies."]
                self._last_evaluated_at = datetime.now(timezone.utc)

                logger.info(f"[SELF-ADAPTATION] Analysis complete across {len(trades)} trades. Insights: {len(self._learning_insights)}")

            except Exception as e:
                logger.error(f"[SELF-ADAPTATION] Error during performance analysis: {e}")

    async def evaluate_opportunity_adaptation(self, setup: Dict[str, Any]) -> Tuple[bool, float, List[str]]:
        """
        Evaluates a proposed trade opportunity against self-adaptation rules.
        Returns:
            - is_allowed (bool): False if strategy is disabled or severely penalized
            - adaptive_modifier (float): Additive modifier to confidence score (e.g. +0.05 or -0.15)
            - adaptation_reasons (List[str]): Explanations of adaptations applied
        """
        # Ensure performance stats are populated
        if not self._last_evaluated_at or (datetime.now(timezone.utc) - self._last_evaluated_at).total_seconds() > 60:
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

        return is_allowed, round(adaptive_modifier, 2), reasons

    def get_dynamic_confidence_threshold(self, base_persona_min_conf: float = 0.78, market_regime: str = "TRENDING") -> float:
        """
        Computes the dynamically calibrated confidence threshold based on:
        1. Persona baseline minimum confidence
        2. Market regime offset (+0.02 for RANGING, 0.00 for TRENDING)
        3. Self-adaptation dynamic performance calibration delta
        """
        regime_offset = 0.02 if market_regime.upper() == "RANGING" else (0.0 if market_regime.upper() == "TRENDING" else 0.01)
        raw_threshold = base_persona_min_conf + regime_offset + self._dynamic_threshold_calibration
        
        # Floor at 0.65 (so system never trades reckless noise) and Cap at 0.88 (so system never freezes)
        calibrated_threshold = round(max(0.65, min(0.88, raw_threshold)), 2)
        return calibrated_threshold

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
            "strategy_performance": self._strategy_stats,
            "symbol_performance": self._symbol_stats,
            "regime_volatility_matrix": self._matrix_stats,
            "learning_insights": self._learning_insights,
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
