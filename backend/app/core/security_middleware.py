import logging
import hmac
import hashlib
import time
from typing import Dict, Any, Optional

logger = logging.getLogger("lumina.core.security_middleware")

class SecurityGovernanceManager:
    """
    Security & Governance Engine for Lumina / AZA.
    Provides API rate-limiting enforcement, HMAC request signature verification,
    and audit security logging for all inbound communication between the Android app
    and FastAPI backend.
    """
    _instance: Optional['SecurityGovernanceManager'] = None

    @classmethod
    def get_instance(cls) -> 'SecurityGovernanceManager':
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        # IP / Key -> list of request timestamps
        self._request_rates: Dict[str, list] = {}
        self._rate_limit_per_minute: int = 120
        self._secret_key: str = "LUMINA_AZA_SHARED_HMAC_SECRET_2026"

    def verify_request_signature(self, payload: str, signature: str) -> bool:
        """Verifies HMAC SHA-256 signature for signed commands between app and backend."""
        if not signature:
            return False
        expected_sig = hmac.new(
            self._secret_key.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()
        return hmac.compare_digest(expected_sig, signature)

    def is_rate_limited(self, client_identifier: str) -> bool:
        """Enforces sliding window rate-limiting per client identifier (IP or API Key)."""
        now = time.time()
        window_start = now - 60.0

        timestamps = self._request_rates.get(client_identifier, [])
        # Filter out old timestamps
        valid_timestamps = [ts for ts in timestamps if ts > window_start]
        valid_timestamps.append(now)
        self._request_rates[client_identifier] = valid_timestamps

        if len(valid_timestamps) > self._rate_limit_per_minute:
            logger.warning(f"🚨 [SECURITY RATE LIMIT] Client '{client_identifier}' exceeded rate limit ({len(valid_timestamps)} req/min)")
            return True
        return False

    def log_security_event(self, event_type: str, client_ip: str, details: str):
        """Records security audit events."""
        logger.info(f"🛡️ [SECURITY AUDIT EVENT] [{event_type}] Client: {client_ip} | Details: {details}")
