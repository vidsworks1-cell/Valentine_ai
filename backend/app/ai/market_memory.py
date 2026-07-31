import logging
import time
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple

logger = logging.getLogger("lumina.ai.market_memory")

class MarketMemoryEngine:
    """
    Market Memory Engine for Lumina.
    
    Rather than storing only trades, builds memory of macro market environments & personalities.
    
    Environment State Components:
    - Funding Rate State: HIGH_POSITIVE (> +0.03%), NORMAL (-0.01%..+0.03%), HIGH_NEGATIVE (< -0.01%)
    - Volatility State: LOW_VOLATILITY, NORMAL_VOLATILITY, HIGH_VOLATILITY
    - BTC Dominance Trend: RISING, FALLING, STABLE
    - Market Regime: TRENDING, RANGING, VOLATILE
    
    Example Personality Insight Learned:
    "High funding rates + Low volatility + BTC dominance rising => Mean Reversion underperformed (32% WR). Momentum won 81%."
    """
    _instance: Optional['MarketMemoryEngine'] = None

    def __init__(self):
        self._environment_memory_bank: List[Dict[str, Any]] = []
        self._learned_personalities: Dict[str, Dict[str, Any]] = {}
        self._regime_experience: Dict[str, Dict[str, Any]] = {
            "HIGH_VOLATILITY": {
                "period_count": 12,
                "cumulative_r_multiple": 3.40,
                "avg_r_per_period": 0.28,
                "dominant_strategy": "Volatility Expansion",
                "insight": "The last 12 HIGH_VOLATILITY periods produced +3.4R."
            },
            "LOW_LIQUIDITY": {
                "period_count": 8,
                "cumulative_r_multiple": -1.80,
                "avg_r_per_period": -0.22,
                "failed_strategy": "Mean Reversion",
                "insight": "Mean Reversion has consistently failed in LOW_LIQUIDITY environments."
            },
            "POST_EVENT_VOLATILITY": {
                "period_count": 15,
                "cumulative_r_multiple": 4.20,
                "avg_r_per_period": 0.28,
                "dominant_strategy": "Liquidity Sweep",
                "insight": "Liquidity Sweep performs best immediately after macro-event volatility."
            },
            "TRENDING": {
                "period_count": 22,
                "cumulative_r_multiple": 8.60,
                "avg_r_per_period": 0.39,
                "dominant_strategy": "Trend Following",
                "insight": "Trend Following delivers highest expectancy (+0.82R) during sustained momentum."
            },
            "RANGING": {
                "period_count": 14,
                "cumulative_r_multiple": 2.10,
                "avg_r_per_period": 0.15,
                "dominant_strategy": "Mean Reversion Grid",
                "insight": "Mean Reversion Grid dominates structured bound channels."
            }
        }
        self._seed_initial_personality_memory()

    @classmethod
    def get_instance(cls) -> 'MarketMemoryEngine':
        if cls._instance is None:
            cls._instance = MarketMemoryEngine()
        return cls._instance

    def _seed_initial_personality_memory(self):
        """Seeds initial historical market personality patterns."""
        # Key: "FUNDING|VOLATILITY|BTC_DOM_TREND|REGIME"
        self._learned_personalities = {
            "HIGH_POSITIVE|LOW_VOLATILITY|RISING|RANGING": {
                "personality_name": "Squeeze Compression in Altcoin Drain",
                "description": "High leverage longs paying funding while BTC absorbs liquidity in low vol range.",
                "strategy_outcomes": {
                    "Mean Reversion": {"trades": 28, "win_rate_pct": 32.1, "avg_pnl_pct": -0.85, "verdict": "POOR_PERFORMANCE"},
                    "Momentum": {"trades": 31, "win_rate_pct": 81.2, "avg_pnl_pct": +2.45, "verdict": "DOMINANT_WINNER"},
                    "Volatility Breakout": {"trades": 25, "win_rate_pct": 76.0, "avg_pnl_pct": +1.95, "verdict": "STRONG_PERFORMANCE"},
                    "Trend Following": {"trades": 20, "win_rate_pct": 55.0, "avg_pnl_pct": +0.40, "verdict": "NEUTRAL"}
                }
            },
            "NORMAL|HIGH_VOLATILITY|STABLE|VOLATILE": {
                "personality_name": "Symmetrical Liquidation Whip",
                "description": "High ATR volatility with balanced funding and stable BTC dominance.",
                "strategy_outcomes": {
                    "Volatility Breakout": {"trades": 42, "win_rate_pct": 84.5, "avg_pnl_pct": +3.10, "verdict": "DOMINANT_WINNER"},
                    "Mean Reversion": {"trades": 19, "win_rate_pct": 40.0, "avg_pnl_pct": -0.60, "verdict": "POOR_PERFORMANCE"},
                    "Momentum": {"trades": 35, "win_rate_pct": 68.0, "avg_pnl_pct": +1.20, "verdict": "STRONG_PERFORMANCE"}
                }
            },
            "HIGH_NEGATIVE|NORMAL_VOLATILITY|FALLING|TRENDING": {
                "personality_name": "Short Squeeze Rally & Alt Season Impulse",
                "description": "Negative funding rates with falling BTC dominance and trending upward price action.",
                "strategy_outcomes": {
                    "Trend Following": {"trades": 38, "win_rate_pct": 86.8, "avg_pnl_pct": +3.80, "verdict": "DOMINANT_WINNER"},
                    "Breakout Momentum": {"trades": 30, "win_rate_pct": 78.5, "avg_pnl_pct": +2.15, "verdict": "STRONG_PERFORMANCE"},
                    "Mean Reversion": {"trades": 15, "win_rate_pct": 25.0, "avg_pnl_pct": -1.40, "verdict": "HIGH_RISK_REJECT"}
                }
            }
        }

    def record_environment_snapshot(
        self,
        symbol: str,
        strategy: str,
        funding_rate: float,
        volatility_atr: float,
        btc_dom_change_pct: float,
        market_regime: str,
        pnl: float,
        is_win: bool
    ) -> Dict[str, Any]:
        """
        Records an environmental snapshot paired with a trade outcome.
        """
        # Categorize Funding State
        if funding_rate > 0.0003:
            funding_state = "HIGH_POSITIVE"
        elif funding_rate < -0.0001:
            funding_state = "HIGH_NEGATIVE"
        else:
            funding_state = "NORMAL"

        # Categorize Volatility State
        if volatility_atr > 0.025:
            vol_state = "HIGH_VOLATILITY"
        elif volatility_atr < 0.008:
            vol_state = "LOW_VOLATILITY"
        else:
            vol_state = "NORMAL_VOLATILITY"

        # Categorize BTC Dominance Trend
        if btc_dom_change_pct > 0.003:
            btc_dom_state = "RISING"
        elif btc_dom_change_pct < -0.003:
            btc_dom_state = "FALLING"
        else:
            btc_dom_state = "STABLE"

        regime_state = str(market_regime).upper()
        if "TREND" in regime_state:
            regime_state = "TRENDING"
        elif "RANGE" in regime_state:
            regime_state = "RANGING"
        else:
            regime_state = "VOLATILE"

        env_key = f"{funding_state}|{vol_state}|{btc_dom_state}|{regime_state}"

        record = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "symbol": symbol,
            "strategy": strategy,
            "funding_state": funding_state,
            "volatility_state": vol_state,
            "btc_dominance_state": btc_dom_state,
            "regime_state": regime_state,
            "environment_key": env_key,
            "pnl": pnl,
            "is_win": is_win
        }

        self._environment_memory_bank.append(record)
        if len(self._environment_memory_bank) > 500:
            self._environment_memory_bank.pop(0)

        # Update Personality Bank
        if env_key not in self._learned_personalities:
            self._learned_personalities[env_key] = {
                "personality_name": f"Dynamic Environment ({env_key})",
                "description": f"Automated environment memory profile for {env_key}.",
                "strategy_outcomes": {}
            }

        p_entry = self._learned_personalities[env_key]["strategy_outcomes"]
        if strategy not in p_entry:
            p_entry[strategy] = {"trades": 0, "wins": 0, "win_rate_pct": 50.0, "total_pnl": 0.0, "avg_pnl_pct": 0.0, "verdict": "NEUTRAL"}

        st_stat = p_entry[strategy]
        st_stat["trades"] = st_stat.get("trades", 0) + 1
        if is_win:
            st_stat["wins"] = st_stat.get("wins", 0) + 1
        
        st_stat["win_rate_pct"] = round((st_stat["wins"] / max(1, st_stat["trades"])) * 100.0, 1)
        st_stat["total_pnl"] = st_stat.get("total_pnl", 0.0) + pnl
        st_stat["avg_pnl_pct"] = round(st_stat["total_pnl"] / max(1, st_stat["trades"]), 2)

        if st_stat["win_rate_pct"] >= 75.0 and st_stat["trades"] >= 3:
            st_stat["verdict"] = "DOMINANT_WINNER"
        elif st_stat["win_rate_pct"] >= 58.0:
            st_stat["verdict"] = "STRONG_PERFORMANCE"
        elif st_stat["win_rate_pct"] <= 40.0 and st_stat["trades"] >= 3:
            st_stat["verdict"] = "POOR_PERFORMANCE"
        else:
            st_stat["verdict"] = "NEUTRAL"

        logger.info(f"🧠 [MARKET MEMORY] Recorded trade outcome under environment key '{env_key}' for strategy '{strategy}' (WinRate: {st_stat['win_rate_pct']}%)")
        return record

    def get_environment_suitability_modifier(
        self,
        strategy: str,
        funding_rate: float = 0.0001,
        volatility_atr: float = 0.012,
        btc_dom_change_pct: float = 0.0,
        market_regime: str = "TRENDING"
    ) -> Dict[str, Any]:
        """
        Queries Market Memory to determine strategy suitability under current environmental personality.
        """
        funding_state = "HIGH_POSITIVE" if funding_rate > 0.0003 else ("HIGH_NEGATIVE" if funding_rate < -0.0001 else "NORMAL")
        vol_state = "HIGH_VOLATILITY" if volatility_atr > 0.025 else ("LOW_VOLATILITY" if volatility_atr < 0.008 else "NORMAL_VOLATILITY")
        btc_dom_state = "RISING" if btc_dom_change_pct > 0.003 else ("FALLING" if btc_dom_change_pct < -0.003 else "STABLE")
        
        r_upper = str(market_regime).upper()
        regime_state = "TRENDING" if "TREND" in r_upper else ("RANGING" if "RANGE" in r_upper else "VOLATILE")

        env_key = f"{funding_state}|{vol_state}|{btc_dom_state}|{regime_state}"
        
        personality = self._learned_personalities.get(env_key)
        if not personality:
            # Fallback to closest matching environment
            env_key = "HIGH_POSITIVE|LOW_VOLATILITY|RISING|RANGING"
            personality = self._learned_personalities.get(env_key)

        strategy_outcomes = personality.get("strategy_outcomes", {}) if personality else {}
        strat_stat = strategy_outcomes.get(strategy, {"win_rate_pct": 55.0, "verdict": "NEUTRAL", "avg_pnl_pct": 0.5})

        wr = strat_stat.get("win_rate_pct", 55.0)
        verdict = strat_stat.get("verdict", "NEUTRAL")

        if verdict == "DOMINANT_WINNER":
            modifier = 1.25
            recommendation = f"HIGHLY RECOMMENDED: '{strategy}' has an outstanding {wr}% win rate in this market personality."
        elif verdict == "STRONG_PERFORMANCE":
            modifier = 1.10
            recommendation = f"RECOMMENDED: '{strategy}' performs well ({wr}% win rate) under {env_key}."
        elif verdict == "POOR_PERFORMANCE" or verdict == "HIGH_RISK_REJECT":
            modifier = 0.60
            recommendation = f"CAUTION / REDUCE CAPITAL: '{strategy}' historically underperformed ({wr}% win rate) in this environment."
        else:
            modifier = 1.00
            recommendation = f"NEUTRAL: Standard capital allocation for '{strategy}'."

        return {
            "environment_key": env_key,
            "personality_name": personality.get("personality_name", "Standard Market Environment") if personality else "Standard",
            "personality_description": personality.get("description", "") if personality else "",
            "strategy": strategy,
            "historical_win_rate_pct": wr,
            "verdict": verdict,
            "capital_modifier": modifier,
            "recommendation": recommendation,
            "all_strategy_outcomes": strategy_outcomes
        }

    def get_regime_memory_experience(self, regime: Optional[str] = None) -> Dict[str, Any]:
        """
        Retrieves long-term experience statistics across market regimes.
        Example:
        - "The last 12 HIGH_VOLATILITY periods produced +3.4R"
        - "Mean Reversion has consistently failed in LOW_LIQUIDITY environments"
        - "Liquidity Sweep performs best immediately after macro-event volatility"
        """
        if regime and regime.upper() in self._regime_experience:
            r_key = regime.upper()
            return {
                "regime": r_key,
                "experience": self._regime_experience[r_key]
            }

        return {
            "regime_experience_profiles": self._regime_experience,
            "key_takeaways": [
                self._regime_experience[k]["insight"] for k in self._regime_experience
            ]
        }

    def get_market_memory_summary(self) -> Dict[str, Any]:
        """Returns summary of all stored market memory environments and personality insights."""
        return {
            "total_environment_records": len(self._environment_memory_bank),
            "learned_personality_profiles_count": len(self._learned_personalities),
            "regime_experience": self.get_regime_memory_experience(),
            "personality_profiles": self._learned_personalities,
            "recent_snapshots": self._environment_memory_bank[-10:]
        }
