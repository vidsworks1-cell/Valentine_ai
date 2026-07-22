import asyncio
import json
import logging
import websockets
from app.core.config import settings
from app.workers.price_cache import PriceCache
from app.database.session import AsyncSessionLocal
from app.services.audit_service import AuditService
from app.api.schemas import AuditLogCreate

logger = logging.getLogger("lumina.workers.websocket")

class BinanceWebSocketWorker:
    """Layer 2 worker connecting to the real Binance Spot WebSocket stream."""
    
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
        
        is_testnet = getattr(settings, "BINANCE_TESTNET", False)
        if is_testnet:
            base_url = "wss://testnet.binance.vision/ws"
        else:
            base_url = "wss://stream.binance.com:9443/ws"
            
        logger.info(f"Connecting to Binance Spot WebSocket at: {base_url} (Testnet Mode={is_testnet})")
        
        while self.running:
            try:
                if self._retry_count > 0:
                    logger.info("Reconnecting...")
                    cache.connection_status = "RECONNECTING"
                
                logger.info(f"Connecting to Binance WebSocket: {base_url}")
                async with websockets.connect(base_url, ping_interval=20, ping_timeout=20) as websocket:
                    if is_testnet:
                        logger.info("Connected to Binance Testnet")
                    else:
                        logger.info("Connected to Binance WebSocket")
                    cache.connection_status = "CONNECTED"
                    self._retry_count = 0  # Reset retry count on successful connection
                    
                    try:
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="INFO",
                                category="SYSTEM",
                                message="Binance WebSocket connected",
                                details=f"Successfully connected to Binance stream at {base_url}."
                             ))
                    except Exception as ae:
                        logger.error(f"Failed to log WebSocket connection audit event: {ae}")
                        
                    subscribe_payload = {
                        "method": "SUBSCRIBE",
                        "params": [f"{symbol.lower()}@ticker" for symbol in symbols],
                        "id": 1
                    }
                    await websocket.send(json.dumps(subscribe_payload))
                    for symbol in symbols:
                        logger.info(f"Subscribed to {symbol.lower()}@ticker")
                    
                    try:
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="INFO",
                                category="SYSTEM",
                                message="Binance WebSocket subscription sent",
                                details=f"Subscription request sent for: {symbols}"
                            ))
                    except Exception as ae:
                        logger.error(f"Failed to log subscription success audit event: {ae}")
                    
                    async for message in websocket:
                        if not self.running:
                            break
                        try:
                            data = json.loads(message)
                            if "result" in data and data.get("id") == 1:
                                logger.info("Subscribed streams success")
                            else:
                                # Handle both single stream format and combined stream wrapper format
                                ticker_data = data.get("data", data) if "stream" in data else data
                                if "s" in ticker_data and "c" in ticker_data:
                                    symbol = ticker_data["s"]
                                    price = float(ticker_data["c"])
                                    cache.update_price(symbol, price)
                        except Exception as inner_e:
                            logger.error(f"Error parsing ticker message: {inner_e}")
                    
                    # If async for finishes without exception, the server closed the stream
                    logger.info("Connection closed by server")
                    
            except asyncio.CancelledError:
                break
            except Exception as e:
                self._retry_count += 1
                logger.error(f"Exception details: {e}", exc_info=True)
                logger.info("Connection closed by server")
                cache.connection_status = "DISCONNECTED"
                
                try:
                    async with AsyncSessionLocal() as db:
                        await AuditService.create_audit_log(db, AuditLogCreate(
                            level="WARNING",
                            category="SYSTEM",
                            message="Binance WebSocket disconnected",
                            details=f"Error details: {str(e)}. Reconnect attempt {self._retry_count} scheduled."
                        ))
                except Exception as ae:
                    logger.error(f"Failed to log WebSocket disconnection audit event: {ae}")
                
                logger.info("Reconnecting...")
                await asyncio.sleep(min(2.0 + self._retry_count * 0.5, 10.0))
