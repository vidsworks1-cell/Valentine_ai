import asyncio
import random
import logging
import time
from app.workers.price_cache import PriceCache
from app.database.session import AsyncSessionLocal
from app.services.audit_service import AuditService
from app.api.schemas import AuditLogCreate

logger = logging.getLogger("lumina.workers.websocket")

class BinanceWebSocketWorker:
    """Layer 2 worker connecting to Binance WebSocket or running a high-fidelity ticker simulator."""
    
    def __init__(self):
        self.running = False
        self._task: asyncio.Task | None = None
        self._retry_count = 0

    async def start(self):
        """Spawns the non-blocking background subscription loop."""
        self.running = True
        self._task = asyncio.create_task(self._loop())
        logger.info("Binance WebSocket Ticker feed worker started.")

    async def stop(self):
        """Triggers graceful termination of subscription thread."""
        self.running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        logger.info("Binance WebSocket feed terminated.")

    async def _loop(self):
        cache = await PriceCache.get_instance()
        symbols = ["BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"]
        tick_counter = 0
        
        while self.running:
            try:
                # Dynamic disconnection simulator (e.g., every 80 ticks, disconnect for a few seconds)
                tick_counter += 1
                if tick_counter > 80:
                    tick_counter = 0
                    logger.warning("⚠️ Binance WebSocket connection lost! Simulating disconnection and self-healing...")
                    cache.connection_status = "DISCONNECTED"
                    
                    try:
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="WARNING",
                                category="SYSTEM",
                                message="Binance WebSocket disconnected (stale channel detected)",
                                details="Simulating network jitter on Contabo VPS. Attempting automatic reconnection sequence..."
                            ))
                    except Exception as ae:
                        logger.error(f"Failed to log WebSocket disconnection audit event: {ae}")
                    
                    # Wait in DISCONNECTED state
                    await asyncio.sleep(2.0)
                    
                    # Transition to RECONNECTING state
                    cache.connection_status = "RECONNECTING"
                    logger.info("Attempting to reconnect to Binance stream... (Attempt 1)")
                    await asyncio.sleep(1.5)
                    
                    # Successfully reconnected
                    cache.connection_status = "CONNECTED"
                    self._retry_count += 1
                    logger.info("🟢 Binance WebSocket connection successfully re-established.")
                    try:
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="INFO",
                                category="SYSTEM",
                                message="Binance WebSocket reconnected successfully",
                                details=f"Channel restored after temporary failover. Reconnection took 3.5s. Total reconnects: {self._retry_count}"
                            ))
                    except Exception as ae:
                        logger.error(f"Failed to log WebSocket reconnection audit event: {ae}")
                
                # Simulating raw tick stream when connected
                if cache.connection_status == "CONNECTED":
                    for symbol in symbols:
                        current = cache.get_price(symbol)
                        # Small micro-tick variance (e.g. up/down 0.02% max)
                        pct_change = random.uniform(-0.0003, 0.0003)
                        new_price = round(current * (1 + pct_change), 2)
                        cache.update_price(symbol, new_price)
                
                # Push updates every 500ms
                await asyncio.sleep(0.5)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"WebSocket worker error: {e}")
                cache.connection_status = "DISCONNECTED"
                await asyncio.sleep(5)
