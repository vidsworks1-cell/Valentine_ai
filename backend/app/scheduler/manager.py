import asyncio
import logging
import time
from typing import Optional, Tuple
from app.workers.websocket_worker import BinanceWebSocketWorker
from app.ai.reasoning import AiReasoningEngine
from app.trading.position_manager import PositionManager
from app.workers.price_cache import PriceCache

logger = logging.getLogger("lumina.scheduler.manager")

class LayeredSchedulerManager:
    """Orchestrates background loops across Layer 2, Layer 3, and Layer 4."""
    _instance: Optional['LayeredSchedulerManager'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self.ws_worker = BinanceWebSocketWorker()
        self.ai_engine = AiReasoningEngine()
        self.position_manager = PositionManager.get_instance()
        self.running = False
        
        # Core safeguards and modes
        self.global_trading_mode: str = "PAPER"  # OFF, PAPER, TESTNET, LIVE
        self.emergency_stop_active: bool = False

    @classmethod
    async def get_instance(cls) -> 'LayeredSchedulerManager':
        async with cls._lock:
            if cls._instance is None:
                cls._instance = LayeredSchedulerManager()
            return cls._instance

    async def is_trading_enabled(self) -> Tuple[bool, str]:
        """Central validation safeguard. Implements the Circuit Breaker pattern."""
        if self.emergency_stop_active:
            return False, "EMERGENCY_STOP_ACTIVE: All trading blocked by operator."
            
        if self.global_trading_mode == "OFF":
            return False, "OFF: Global trading mode is set to OFF."

        cache = await PriceCache.get_instance()
        
        # 1. Connection Status Circuit Breaker
        if cache.connection_status != "CONNECTED":
            return False, f"CIRCUIT_BREAKER_TRIGGERED: Stale network connectivity ({cache.connection_status})."

        # 2. Feed Freshness Circuit Breaker
        stale_time = time.time() - cache.last_update_time
        if stale_time > 5.0:
            return False, f"CIRCUIT_BREAKER_TRIGGERED: Market price feed is stale (last update: {stale_time:.1f}s ago)."

        return True, f"ALLOWED: Active under {self.global_trading_mode} mode."

    async def start_all(self):
        """Starts all operational background layers."""
        if self.running:
            return
        
        self.running = True
        logger.info("Initializing multi-layer trading daemon orchestration...")
        
        # Start Layer 2 Price Subscription WebSocket
        await self.ws_worker.start()
        
        # Start Layer 4 Trailing Stop & Position Manager
        await self.position_manager.start()

        # Start Layer 3 AI Reasoning & Opportunity Scanner
        await self.ai_engine.start()
        
        logger.info("All layers (L2, L3, L4) are actively running.")

    async def stop_all(self):
        """Gracefully terminates background threads across all layers."""
        if not self.running:
            return
            
        self.running = False
        logger.info("Gracefully stopping background layer orchestrators...")
        
        # Stop Layer 3 Scanner
        await self.ai_engine.stop()
        
        # Stop Layer 4 Position Manager
        await self.position_manager.stop()
        
        # Stop Layer 2 Ticker Worker
        await self.ws_worker.stop()
        
        logger.info("All background layers shutdown successfully.")
