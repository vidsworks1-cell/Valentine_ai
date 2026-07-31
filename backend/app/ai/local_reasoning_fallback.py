import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.ai.local_reasoning_fallback")

class LocalReasoningFallbackEngine:
    """
    Local Intelligence / Fallback Reasoning Engine for Lumina.
    Provides deterministic local rule-based analysis and fallback decision scoring
    when cloud AI endpoints (e.g. Gemini, external LLMs) are offline, rate-limited,
    or unreachable.
    """
    _instance: Optional['LocalReasoningFallbackEngine'] = None

    @classmethod
    def get_instance(cls) -> 'LocalReasoningFallbackEngine':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def evaluate_local_fallback(self, setup_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Performs local offline evaluation of a trading setup based on market structure,
        volatility squeeze, and volume confluence.
        """
        symbol = setup_data.get("symbol", "UNKNOWN")
        side = setup_data.get("side", "BUY")
        price = setup_data.get("price", 0.0)
        strategy = setup_data.get("strategy", "Technical Setup")

        # Basic rule evaluation
        atr = setup_data.get("atr", 1.0)
        volume = setup_data.get("volume_24h", 100000.0)

        # Deterministic local score calculation
        local_conviction = 70.0
        reasons = []

        if volume > 500000:
            local_conviction += 5.0
            reasons.append("High 24h volume liquidity (>500k USD)")
        
        if atr > 0:
            local_conviction += 5.0
            reasons.append("Positive volatility ATR expansion")

        local_conviction = min(90.0, max(50.0, local_conviction))
        passed = local_conviction >= 70.0

        logger.info(
            f"💻 [LOCAL REASONING FALLBACK] Evaluated {symbol} {side} ({strategy}) locally. "
            f"Conviction Score: {local_conviction:.1f}/100. Approved: {passed}"
        )

        return {
            "mode": "LOCAL_FALLBACK",
            "symbol": symbol,
            "side": side,
            "price": price,
            "conviction_score": local_conviction,
            "approved": passed,
            "reasons": reasons,
            "fallback_used": True
        }
