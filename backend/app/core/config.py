from typing import List
try:
    from pydantic_settings import BaseSettings, SettingsConfigDict
except ImportError:
    try:
        from pydantic import BaseSettings
        def SettingsConfigDict(**kwargs): return kwargs
    except ImportError:
        class BaseSettings:
            PROJECT_NAME: str = "Lumina Backend API"
            VERSION: str = "1.0.0"
            API_V1_STR: str = "/api/v1"
            DATABASE_URL: str = "sqlite+aiosqlite:///./lumina_backend.db"
            SECRET_KEY: str = "super-secret-lumina-key-change-in-production"
            ALLOWED_HOSTS: List[str] = ["*"]
            BINANCE_API_KEY: str = ""
            BINANCE_API_SECRET: str = ""
            BINANCE_TESTNET_API_KEY: str = ""
            BINANCE_TESTNET_SECRET_KEY: str = ""
            BINANCE_TESTNET_API_SECRET: str = ""
            BINANCE_TESTNET: bool = False
            BINANCE_VENUE: str = "SPOT" # "SPOT" or "FUTURES"
            TRADING_VENUE: str = "SPOT"
            BINANCE_FUTURES_LEVERAGE: int = 2
            BINANCE_FUTURES_MARGIN_TYPE: str = "ISOLATED"
            TELEGRAM_BOT_TOKEN: str = ""
            TELEGRAM_CHAT_ID: str = ""
            TELEGRAM_ENABLED: bool = True
        def SettingsConfigDict(**kwargs): return kwargs

class Settings(BaseSettings):
    PROJECT_NAME: str = "Lumina Backend API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # SQLite async fallback for local, can override via DATABASE_URL on Contabo (e.g. postgresql+asyncpg://...)
    DATABASE_URL: str = "sqlite+aiosqlite:///./lumina_backend.db"
    
    SECRET_KEY: str = "super-secret-lumina-key-change-in-production"
    ALLOWED_HOSTS: List[str] = ["*"]
    
    # Binance API & WebSocket configurations
    BINANCE_API_KEY: str = ""
    BINANCE_API_SECRET: str = ""
    BINANCE_TESTNET_API_KEY: str = ""
    BINANCE_TESTNET_SECRET_KEY: str = ""
    BINANCE_TESTNET_API_SECRET: str = ""
    BINANCE_TESTNET: bool = False
    BINANCE_VENUE: str = "SPOT" # "SPOT" or "FUTURES"
    TRADING_VENUE: str = "SPOT"
    BINANCE_FUTURES_LEVERAGE: int = 2
    BINANCE_FUTURES_MARGIN_TYPE: str = "ISOLATED"

    # Telegram Real-Time Telemetry & Alerting
    TELEGRAM_BOT_TOKEN: str = ""
    TELEGRAM_CHAT_ID: str = ""
    TELEGRAM_ENABLED: bool = True
    
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )

settings = Settings()
