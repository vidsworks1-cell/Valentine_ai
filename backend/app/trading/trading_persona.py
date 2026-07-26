import logging
from typing import Dict, Any, Optional, List
from datetime import datetime, timezone

logger = logging.getLogger("lumina.trading.trading_persona")

class TradingPersonaPresetEngine:
    """
    Trading Persona Preset Engine for Lumina.
    
    Allows users and institutional operators to configure Lumina's execution strategy behavior
    by selecting from fine-tuned trading persona presets or defining custom quantitative parameters.
    
    Presets Available:
    1. CONSERVATIVE_MACRO_SWING:
       - Min Bayesian Confidence: 0.82+
       - Wider ATR Stop Losses: 2.5x ATR (2.0x to 3.0x)
       - Target R:R: 1:2.0+ (Take Profit 5.0x ATR)
       - Low Max Leverage: 3x (2x to 5x)
       - Max Portfolio Allocation: 10%
       - Macro Overlay Sensitivity: High (Heavy penalty for high macro risk)
       
    2. AGGRESSIVE_MOMENTUM_SCALPER:
       - Min Bayesian Confidence: 0.72
       - Tight Trailing Stops: 1.0x ATR
       - Target R:R: 1:2.2 (Take Profit 2.2x ATR)
       - Higher Max Leverage: 10x (5x to 20x)
       - Max Portfolio Allocation: 25%
       - High focus on 100x/50x Liquidation Sweeps, RVOL Volume Surges, and Volatility Squeezes
       
    3. DELTA_NEUTRAL_ARBITRAGE:
       - Min Bayesian Confidence: 0.75
       - Stop Loss: 1.2x ATR
       - Take Profit: 2.0x ATR
       - Max Leverage: 5x
       - Max Portfolio Allocation: 20%
       - Focus on Funding Rate Skews, Exchange Netflow Imbalances, and Cross-Asset Decoupling
       
    4. CUSTOM_QUANT:
       - User-configured parameter values.
    """
    _instance: Optional['TradingPersonaPresetEngine'] = None

    PRESETS: Dict[str, Dict[str, Any]] = {
        "CONSERVATIVE_MACRO_SWING": {
            "name": "Conservative Macro Swing",
            "key": "CONSERVATIVE_MACRO_SWING",
            "description": "Prioritizes capital preservation, macro stability, and low drawdowns with strict confidence bars.",
            "min_bayesian_confidence": 0.82,
            "sl_atr_multiplier": 2.5,
            "tp_atr_multiplier": 5.0,
            "max_leverage": 3,
            "max_position_allocation_pct": 10.0,
            "trailing_stop_enabled": True,
            "macro_risk_penalty_weight": 1.5,
            "liquidation_sweep_focus": False,
            "arbitrage_skew_focus": False,
            "preferred_timeframes": ["1h", "4h", "1d"]
        },
        "AGGRESSIVE_MOMENTUM_SCALPER": {
            "name": "Aggressive Momentum Scalper",
            "key": "AGGRESSIVE_MOMENTUM_SCALPER",
            "description": "Capitalizes on rapid momentum breakouts, volume surges, and 50x/100x liquidation sweeps.",
            "min_bayesian_confidence": 0.72,
            "sl_atr_multiplier": 1.0,
            "tp_atr_multiplier": 2.2,
            "max_leverage": 10,
            "max_position_allocation_pct": 25.0,
            "trailing_stop_enabled": True,
            "macro_risk_penalty_weight": 0.8,
            "liquidation_sweep_focus": True,
            "arbitrage_skew_focus": False,
            "preferred_timeframes": ["1m", "5m", "15m"]
        },
        "DELTA_NEUTRAL_ARBITRAGE": {
            "name": "Delta-Neutral Arbitrage",
            "key": "DELTA_NEUTRAL_ARBITRAGE",
            "description": "Exploits funding rate skews, exchange netflow imbalances, and cross-asset correlation spreads.",
            "min_bayesian_confidence": 0.75,
            "sl_atr_multiplier": 1.2,
            "tp_atr_multiplier": 2.0,
            "max_leverage": 5,
            "max_position_allocation_pct": 20.0,
            "trailing_stop_enabled": True,
            "macro_risk_penalty_weight": 1.0,
            "liquidation_sweep_focus": False,
            "arbitrage_skew_focus": True,
            "preferred_timeframes": ["5m", "15m", "1h"]
        }
    }

    def __init__(self):
        self._active_preset_key: str = "CONSERVATIVE_MACRO_SWING"
        self._custom_config: Dict[str, Any] = dict(self.PRESETS["CONSERVATIVE_MACRO_SWING"])
        self._custom_config["key"] = "CUSTOM_QUANT"
        self._custom_config["name"] = "Custom Quantitative Preset"
        self._last_updated_at: datetime = datetime.now(timezone.utc)

    @classmethod
    def get_instance(cls) -> 'TradingPersonaPresetEngine':
        if cls._instance is None:
            cls._instance = TradingPersonaPresetEngine()
        return cls._instance

    def get_active_persona(self) -> Dict[str, Any]:
        """Returns the currently active persona preset parameters."""
        if self._active_preset_key == "CUSTOM_QUANT":
            return dict(self._custom_config)
        preset = self.PRESETS.get(self._active_preset_key, self.PRESETS["CONSERVATIVE_MACRO_SWING"])
        return dict(preset)

    def set_active_persona(self, persona_key: str, custom_overrides: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Sets the active trading persona preset.
        Valid keys: CONSERVATIVE_MACRO_SWING, AGGRESSIVE_MOMENTUM_SCALPER, DELTA_NEUTRAL_ARBITRAGE, CUSTOM_QUANT
        """
        key = persona_key.upper()
        if key in self.PRESETS:
            self._active_preset_key = key
            logger.info(f"🎭 [TRADING PERSONA] Active persona set to '{key}'.")
        elif key == "CUSTOM_QUANT":
            self._active_preset_key = "CUSTOM_QUANT"
            if custom_overrides:
                self._custom_config.update(custom_overrides)
            logger.info(f"🎭 [TRADING PERSONA] Active persona set to 'CUSTOM_QUANT' with parameters: {self._custom_config}.")
        else:
            raise ValueError(f"Invalid persona preset key: {persona_key}. Valid options: {list(self.PRESETS.keys()) + ['CUSTOM_QUANT']}")

        self._last_updated_at = datetime.now(timezone.utc)
        return self.get_active_persona()

    def get_all_presets(self) -> Dict[str, Any]:
        """Returns list of all available presets and current active key."""
        return {
            "active_preset_key": self._active_preset_key,
            "active_persona": self.get_active_persona(),
            "presets": self.PRESETS,
            "timestamp": self._last_updated_at.isoformat()
        }
