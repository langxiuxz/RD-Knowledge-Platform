from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "study-agent-ai"
    app_env: str = "dev"
    host: str = "0.0.0.0"
    port: int = Field(default=8000, ge=1, le=65535)
    log_level: str = "INFO"
    internal_token: str = ""
    spring_boot_base_url: str = "http://localhost:8080"
    spring_boot_connect_timeout: float = Field(default=2.0, gt=0)
    spring_boot_read_timeout: float = Field(default=30.0, gt=0)
    llm_provider: str = "none"
    llm_api_key: str = ""
    llm_model: str = ""
    vector_store_provider: str = "none"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
