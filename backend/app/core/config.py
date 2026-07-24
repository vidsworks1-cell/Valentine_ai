from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict

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
    
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )

settings = Settings()
