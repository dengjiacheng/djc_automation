"""Application configuration using pydantic settings with structured sections."""

from functools import lru_cache
from pathlib import Path
from typing import Literal, Optional

from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class ServerSettings(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000
    reload: bool = False


class DatabaseSettings(BaseModel):
    url: str = Field(default="sqlite+aiosqlite:///./automation.db", alias="url")
    echo: bool = False
    pool_size: Optional[int] = None
    max_overflow: Optional[int] = None


class SecuritySettings(BaseModel):
    secret_key: str = Field(default="change-me", min_length=8)
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 60 * 24 * 7


class StorageSettings(BaseModel):
    test_apk_dir: Path = Field(default=Path("storage/test_apk"))


class WebSocketSettings(BaseModel):
    heartbeat_interval: int = 30
    timeout: int = 300


class Settings(BaseSettings):
    """Top-level application settings with nested sections."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_nested_delimiter="__",
        extra="ignore",
        case_sensitive=False,
    )

    environment: Literal["development", "staging", "production", "test"] = "development"
    debug: bool = False
    project_name: str = "Android Automation Server"
    api_prefix: str = "/api"

    server: ServerSettings = ServerSettings()
    database: DatabaseSettings = DatabaseSettings()
    security: SecuritySettings = SecuritySettings()
    storage: StorageSettings = StorageSettings()
    websocket: WebSocketSettings = WebSocketSettings()

    static_dir: Path = Path("app/web/static")
    template_dir: Path = Path("app/web/templates")

    @property
    def database_url(self) -> str:
        return self.database.url

    @property
    def host(self) -> str:
        return self.server.host

    @property
    def port(self) -> int:
        return self.server.port

    @property
    def secret_key(self) -> str:
        return self.security.secret_key

    @property
    def algorithm(self) -> str:
        return self.security.algorithm

    @property
    def access_token_expire_minutes(self) -> int:
        return self.security.access_token_expire_minutes

    @property
    def test_apk_storage_dir(self) -> str:
        return str(self.storage.test_apk_dir)

    @property
    def ws_heartbeat_interval(self) -> int:
        return self.websocket.heartbeat_interval

    @property
    def ws_timeout(self) -> int:
        return self.websocket.timeout


@lru_cache()
def get_settings() -> Settings:
    return Settings()
