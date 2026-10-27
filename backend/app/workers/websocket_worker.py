import asyncio
import json
import logging
import time
import urllib.request
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
        symbols = [
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
            "ADAUSDT", "DOTUSDT", "MATICUSDT", "AVAXUSDT",
            "LINKUSDT", "NEARUSDT", "LTCUSDT", "XRPUSDT",
            "PEPEUSDT", "DOGEUSDT", "RENDERUSDT", "INJUSDT",
            "SUIUSDT", "FETUSDT", "TIAUSDT"
        ]
        
        from app.scheduler.manager import LayeredSchedulerManager
        scheduler = await LayeredSchedulerManager.get_instance()
        trading_mode = scheduler.global_trading_mode
        trading_venue = getattr(scheduler, "trading_venue", "SPOT").upper()
        is_testnet = (trading_mode == "TESTNET") or getattr(settings, "BINANCE_TESTNET", False)

        if trading_venue == "FUTURES":
            base_url = "wss://stream.binancefuture.com/ws" if is_testnet else "wss://fstream.binance.com/ws"
        else:
            base_url = "wss://testnet.binance.vision/ws" if is_testnet else "wss://stream.binance.com:9443/ws"
            
        logger.info(f"Connecting to Binance {trading_venue} WebSocket at: {base_url} (Mode={trading_mode}, Testnet={is_testnet})")
        
        # Start background universe refresher to populate 24h volume for all 700+ contracts immediately
        asyncio.create_task(self._bootstrap_exchange_universe(trading_venue, is_testnet))

        while self.running:
            try:
                if self._retry_count > 0:
                    logger.info("Reconnecting...")
                    cache.connection_status = "RECONNECTING"
                
                logger.info(f"Connecting to Binance WebSocket: {base_url}")
                async with websockets.connect(base_url, ping_interval=20, ping_timeout=20) as websocket:
                    logger.info(f"Connected to Binance {trading_venue} Market Stream")
                    cache.connection_status = "CONNECTED"
                    self._retry_count = 0
                    
                    try:
                        async with AsyncSessionLocal() as db:
                            await AuditService.create_audit_log(db, AuditLogCreate(
                                level="INFO",
                                category="SYSTEM",
                                message=f"Binance {trading_venue} WebSocket connected",
                                details=f"Connected to all-market stream at {base_url}."
                             ))
                    except Exception as ae:
                        logger.error(f"Failed to log WebSocket connection audit event: {ae}")
                        
                    # Subscribe to all-market ticker array (!miniTicker@arr streams all symbols at once)
                    subscribe_payload = {
                        "method": "SUBSCRIBE",
                        "params": ["!miniTicker@arr"],
                        "id": 1
                    }
                    await websocket.send(json.dumps(subscribe_payload))
                    logger.info(f"Subscribed to !miniTicker@arr (All-Market Stream for {trading_venue})")
                    
                    async for message in websocket:
                        if not self.running:
                            break

                        # Chaos Engine fault injections
                        from app.trading.chaos_engine import ChaosEngine
                        chaos = ChaosEngine.get_instance()
                        if chaos.enabled:
                            if chaos.should_inject_network_disconnect():
                                raise Exception("Chaos injected network disconnect")
                            if chaos.should_inject_ws_drop():
                                continue

                        try:
                            data = json.loads(message)
                            if isinstance(data, dict) and "result" in data and data.get("id") == 1:
                                logger.info("Subscribed to all-market ticker stream successfully")
                            else:
                                # Handle all-market array format (!miniTicker@arr) or wrapped stream format
                                items = data if isinstance(data, list) else ([data.get("data", data)] if "stream" in data else [data])
                                for item in items:
                                    if isinstance(item, dict) and "s" in item and "c" in item:
                                        sym = str(item["s"]).upper()
                                        if sym.endswith("USDT"):
                                            px = float(item["c"])
                                            q_vol = float(item.get("q", 0.0))
                                            h_px = float(item.get("h", 0.0))
                                            l_px = float(item.get("l", 0.0))
                                            cache.update_price_and_volume(sym, px, q_vol, high_24h=h_px, low_24h=l_px)
                        except Exception as inner_e:
                            logger.error(f"Error parsing ticker message: {inner_e}")
                    
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

    async def _bootstrap_exchange_universe(self, venue: str = "FUTURES", is_testnet: bool = False):
        """Periodically fetches all 24h tickers from Binance REST API to ensure 100% full market coverage."""
        cache = await PriceCache.get_instance()
        if venue == "FUTURES":
            api_url = "https://fapi.binance.com/fapi/v1/ticker/24hr"
        else:
            api_url = "https://api.binance.com/api/v3/ticker/24hr"

        def _fetch():
            req = urllib.request.Request(api_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=10) as resp:
                if resp.status == 200:
                    return json.loads(resp.read().decode("utf-8"))
            return []

        first_run = True
        last_log_time = 0.0
        while self.running:
            try:
                tickers = await asyncio.to_thread(_fetch)
                count = 0
                if isinstance(tickers, list):
                    for item in tickers:
                        sym = str(item.get("symbol", "")).upper()
                        if sym.endswith("USDT") and item.get("lastPrice"):
                            try:
                                px = float(item["lastPrice"])
                                vol = float(item.get("quoteVolume", 0.0))
                                h_px = float(item.get("highPrice", 0.0))
                                l_px = float(item.get("lowPrice", 0.0))
                                cache.update_price_and_volume(sym, px, vol, high_24h=h_px, low_24h=l_px)
                                count += 1
                            except Exception:
                                pass
                now = time.time()
                if first_run or (now - last_log_time >= 600.0):
                    logger.info(f"⚡ [UNIVERSE BOOTSTRAP] Synced {count} active USDT contracts from Binance {venue} into PriceCache.")
                    first_run = False
                    last_log_time = now
                else:
                    logger.debug(f"[UNIVERSE BOOTSTRAP] Periodic sync: {count} contracts refreshed.")
            except Exception as e:
                logger.debug(f"Universe bootstrap polling notice: {e}")

            await asyncio.sleep(20.0)
