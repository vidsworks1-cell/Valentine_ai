import asyncio
import logging
import os
import json
import time
from typing import Optional, Tuple
from datetime import datetime, timezone
from app.workers.websocket_worker import BinanceWebSocketWorker
from app.ai.reasoning import AiReasoningEngine
from app.trading.position_manager import PositionManager
from app.trading.exchange_reconciliation import ExchangeReconciliationEngine
from app.workers.price_cache import PriceCache

logger = logging.getLogger("lumina.scheduler.manager")

CONFIG_FILE_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "data", "system_config.json")

class LayeredSchedulerManager:
    """Orchestrates background loops across Layer 2, Layer 3, and Layer 4."""
    _instance: Optional['LayeredSchedulerManager'] = None
    _lock = asyncio.Lock()

    def __init__(self):
        self.ws_worker = BinanceWebSocketWorker()
        self.ai_engine = AiReasoningEngine()
        self.position_manager = PositionManager.get_instance()
        self.reconciliation_engine = ExchangeReconciliationEngine.get_instance()
        self.running = False
        self._recon_task: Optional[asyncio.Task] = None
        self._maintenance_task: Optional[asyncio.Task] = None
        
        # Core safeguards and modes - restored from persistent config if available
        self.global_trading_mode: str = "PAPER"  # OFF, PAPER, TESTNET, LIVE
        self.trading_venue: str = os.getenv("TRADING_VENUE", os.getenv("BINANCE_VENUE", "SPOT")).upper() # SPOT or FUTURES
        self.emergency_stop_active: bool = False
        self.load_persistent_config()

    def load_persistent_config(self):
        """Loads persistent system configuration from data/system_config.json or environment."""
        env_mode = os.getenv("GLOBAL_TRADING_MODE") or os.getenv("TRADING_MODE")
        if env_mode and env_mode.upper() in ["OFF", "PAPER", "TESTNET", "LIVE"]:
            self.global_trading_mode = env_mode.upper()
            logger.info(f"Loaded global trading mode from environment: {self.global_trading_mode}")

        env_venue = os.getenv("TRADING_VENUE") or os.getenv("BINANCE_VENUE")
        if env_venue and env_venue.upper() in ["SPOT", "FUTURES"]:
            self.trading_venue = env_venue.upper()
            logger.info(f"Loaded trading venue from environment: {self.trading_venue}")

        if os.path.exists(CONFIG_FILE_PATH):
            try:
                with open(CONFIG_FILE_PATH, "r") as f:
                    cfg = json.load(f)
                    saved_mode = cfg.get("global_trading_mode")
                    if saved_mode and str(saved_mode).upper() in ["OFF", "PAPER", "TESTNET", "LIVE"]:
                        self.global_trading_mode = str(saved_mode).upper()
                        logger.info(f"💾 Loaded persistent global trading mode: {self.global_trading_mode} from '{CONFIG_FILE_PATH}'")
                    saved_venue = cfg.get("trading_venue")
                    if saved_venue and str(saved_venue).upper() in ["SPOT", "FUTURES"]:
                        self.trading_venue = str(saved_venue).upper()
                        logger.info(f"💾 Loaded persistent trading venue: {self.trading_venue} from '{CONFIG_FILE_PATH}'")
                    if "emergency_stop_active" in cfg:
                        self.emergency_stop_active = bool(cfg.get("emergency_stop_active"))
            except Exception as e:
                logger.error(f"Failed loading persistent config from {CONFIG_FILE_PATH}: {e}")

    def save_persistent_config(self):
        """Saves current global trading mode and safeguards to persistent storage."""
        try:
            os.makedirs(os.path.dirname(CONFIG_FILE_PATH), exist_ok=True)
            cfg_data = {
                "global_trading_mode": self.global_trading_mode,
                "trading_venue": self.trading_venue,
                "emergency_stop_active": self.emergency_stop_active,
                "updated_at": datetime.now(timezone.utc).isoformat()
            }
            with open(CONFIG_FILE_PATH, "w") as f:
                json.dump(cfg_data, f, indent=2)
            logger.info(f"💾 Saved persistent global trading mode '{self.global_trading_mode}' and venue '{self.trading_venue}' to '{CONFIG_FILE_PATH}'")
        except Exception as e:
            logger.error(f"Failed saving persistent config to {CONFIG_FILE_PATH}: {e}")

    def set_trading_mode(self, mode: str) -> str:
        """Sets trading mode in memory and persists to storage."""
        mode_upper = mode.upper()
        if mode_upper not in ["OFF", "PAPER", "TESTNET", "LIVE"]:
            raise ValueError(f"Invalid trading mode: {mode}. Allowed: OFF, PAPER, TESTNET, LIVE")
        self.global_trading_mode = mode_upper
        self.save_persistent_config()
        return self.global_trading_mode

    def set_trading_venue(self, venue: str) -> str:
        """Sets trading venue (SPOT or FUTURES) in memory and persists to storage."""
        venue_upper = venue.upper()
        if venue_upper not in ["SPOT", "FUTURES"]:
            raise ValueError(f"Invalid trading venue: {venue}. Allowed: SPOT, FUTURES")
        self.trading_venue = venue_upper
        self.save_persistent_config()
        logger.info(f"🔄 [VENUE SWITCH] Switched Lumina trading venue to: {self.trading_venue}")
        return self.trading_venue

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

    async def _reconciliation_loop(self):
        """Periodic background reconciliation task syncing exchange & SQLite state every 60s."""
        logger.info("Starting periodic exchange state reconciliation daemon (every 60s)...")
        while self.running:
            try:
                await asyncio.sleep(60.0)
                if not self.running:
                    break
                await self.reconciliation_engine.reconcile_state()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error during periodic exchange reconciliation: {e}")

    async def _dust_and_fee_maintenance_loop(self):
        """
        Background maintenance daemon running every 6 hours:
        1. Upgrade 3: Ensures BNB balance has ~$1.00 buffer for 25% fee discount.
        2. Upgrade 4: Automatically sweeps sub-dollar dust balances into BNB.
        """
        logger.info("Maintenance daemon initialized (automated Spot dust conversion disabled; managed manually by user).")
        await asyncio.sleep(30.0)
        while self.running:
            # Automated Spot dust conversion is disabled per user preference (Spot assets are managed manually)
            # Sleep 6 hours
            try:
                await asyncio.sleep(6 * 3600.0)
            except asyncio.CancelledError:
                break
            
            # Sleep 6 hours (21600 seconds)
            try:
                await asyncio.sleep(6 * 3600.0)
            except asyncio.CancelledError:
                break

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

        # Execute Initial Exchange State Reconciliation & launch 60s background daemon
        try:
            await self.reconciliation_engine.reconcile_state()
        except Exception as re_err:
            logger.error(f"Initial reconciliation failed during startup: {re_err}")
            
        self._recon_task = asyncio.create_task(self._reconciliation_loop())
        self._maintenance_task = asyncio.create_task(self._dust_and_fee_maintenance_loop())
        
        logger.info("All layers (L2, L3, L4), Reconciliation Daemon, and 6h Dust/BNB Fee Sweeper are actively running.")

    async def stop_all(self):
        """Gracefully terminates background threads across all layers."""
        if not self.running:
            return
            
        self.running = False
        logger.info("Gracefully stopping background layer orchestrators...")
        
        # Stop Reconciliation Task
        if self._recon_task:
            self._recon_task.cancel()
            try:
                await self._recon_task
            except asyncio.CancelledError:
                pass
            self._recon_task = None

        # Stop 6h Maintenance Task
        if self._maintenance_task:
            self._maintenance_task.cancel()
            try:
                await self._maintenance_task
            except asyncio.CancelledError:
                pass
            self._maintenance_task = None

        # Stop Layer 3 Scanner
        await self.ai_engine.stop()
        
        # Stop Layer 4 Position Manager
        await self.position_manager.stop()
        
        # Stop Layer 2 Ticker Worker
        await self.ws_worker.stop()
        
        logger.info("All background layers shutdown successfully.")
