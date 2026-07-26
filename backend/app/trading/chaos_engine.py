import random
import asyncio
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.trading.chaos_engine")

class ChaosEngine:
    """Chaos Engine for injecting faults into Binance API, WebSockets, DB, and Network."""
    _instance: Optional['ChaosEngine'] = None

    def __init__(self):
        self.enabled: bool = False
        self.api_timeout_rate: float = 0.0
        self.http_500_rate: float = 0.0
        self.ws_lost_msg_rate: float = 0.0
        self.ws_duplicate_msg_rate: float = 0.0
        self.delayed_fill_rate: float = 0.0
        self.delayed_fill_seconds: float = 0.2
        self.db_failure_rate: float = 0.0
        self.network_disconnect_rate: float = 0.0

        # Inject metrics
        self.stats: Dict[str, int] = {
            "api_timeouts": 0,
            "http_500_errors": 0,
            "ws_lost_messages": 0,
            "ws_duplicate_messages": 0,
            "delayed_fills": 0,
            "db_failures": 0,
            "network_disconnects": 0
        }

    @classmethod
    def get_instance(cls) -> 'ChaosEngine':
        if cls._instance is None:
            cls._instance = ChaosEngine()
        return cls._instance

    def configure(self, **kwargs):
        """Configures rates and enabled state dynamically."""
        for key, value in kwargs.items():
            if hasattr(self, key):
                setattr(self, key, value)
        logger.info(f"[CHAOS ENGINE] Configured: enabled={self.enabled}, rates={kwargs}")

    def reset_stats(self):
        """Resets all chaos injection counters."""
        for key in self.stats:
            self.stats[key] = 0

    def should_inject_api_timeout(self) -> bool:
        if not self.enabled or self.api_timeout_rate <= 0:
            return False
        triggered = random.random() < self.api_timeout_rate
        if triggered:
            self.stats["api_timeouts"] += 1
            logger.warning("💥 [CHAOS] Injecting Binance API Timeout")
        return triggered

    def should_inject_http_500(self) -> bool:
        if not self.enabled or self.http_500_rate <= 0:
            return False
        triggered = random.random() < self.http_500_rate
        if triggered:
            self.stats["http_500_errors"] += 1
            logger.warning("💥 [CHAOS] Injecting HTTP 500 Error")
        return triggered

    def should_inject_ws_drop(self) -> bool:
        if not self.enabled or self.ws_lost_msg_rate <= 0:
            return False
        triggered = random.random() < self.ws_lost_msg_rate
        if triggered:
            self.stats["ws_lost_messages"] += 1
            logger.warning("💥 [CHAOS] Injecting WebSocket Message Drop")
        return triggered

    def should_inject_ws_duplicate(self) -> bool:
        if not self.enabled or self.ws_duplicate_msg_rate <= 0:
            return False
        triggered = random.random() < self.ws_duplicate_msg_rate
        if triggered:
            self.stats["ws_duplicate_messages"] += 1
            logger.warning("💥 [CHAOS] Injecting WebSocket Duplicate Event")
        return triggered

    def get_fill_delay(self) -> float:
        if not self.enabled or self.delayed_fill_rate <= 0:
            return 0.0
        if random.random() < self.delayed_fill_rate:
            self.stats["delayed_fills"] += 1
            logger.warning(f"💥 [CHAOS] Injecting Delayed Fill ({self.delayed_fill_seconds}s)")
            return self.delayed_fill_seconds
        return 0.0

    def should_inject_db_failure(self) -> bool:
        if not self.enabled or self.db_failure_rate <= 0:
            return False
        triggered = random.random() < self.db_failure_rate
        if triggered:
            self.stats["db_failures"] += 1
            logger.warning("💥 [CHAOS] Injecting Database Failure")
        return triggered

    def should_inject_network_disconnect(self) -> bool:
        if not self.enabled or self.network_disconnect_rate <= 0:
            return False
        triggered = random.random() < self.network_disconnect_rate
        if triggered:
            self.stats["network_disconnects"] += 1
            logger.warning("💥 [CHAOS] Injecting Network Disconnect")
        return triggered

    def get_telemetry(self) -> Dict[str, Any]:
        return {
            "enabled": self.enabled,
            "rates": {
                "api_timeout_rate": self.api_timeout_rate,
                "http_500_rate": self.http_500_rate,
                "ws_lost_msg_rate": self.ws_lost_msg_rate,
                "ws_duplicate_msg_rate": self.ws_duplicate_msg_rate,
                "delayed_fill_rate": self.delayed_fill_rate,
                "db_failure_rate": self.db_failure_rate,
                "network_disconnect_rate": self.network_disconnect_rate,
            },
            "stats": dict(self.stats)
        }
