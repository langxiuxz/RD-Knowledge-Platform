from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "study-agent-ai"
    app_env: str = "dev"
    host: str = "0.0.0.0"
    port: int = Field(default=8000, ge=1, le=65535)
    log_level: str = "INFO"
    # Secrets must be supplied by environment variables or a local .env file.
    internal_token: str = ""
    spring_boot_base_url: str = "http://localhost:8080"
    spring_boot_connect_timeout: float = Field(default=2.0, gt=0)
    spring_boot_read_timeout: float = Field(default=30.0, gt=0)
    document_callback_attempts: int = Field(default=3, ge=1, le=5)
    document_callback_retry_delay: float = Field(default=0.25, gt=0, le=5)
    # Supported remote providers: deepseek, openai, openai-compatible.
    # Keep local startup independent from external API credentials.
    llm_provider: str = "none"
    llm_api_key: str = ""
    llm_model: str = ""
    llm_base_url: str = "https://api.deepseek.com"
    llm_timeout: float = Field(default=30.0, gt=0)
    tool_timeout: float = Field(default=5.0, gt=0)
    tool_max_calls: int = Field(default=8, ge=1, le=16)#tool_max_calls: int = Field(default=4, ge=1, le=8)
    max_agent_steps: int = Field(default=10, ge=1, le=16)#max_agent_steps: int = Field(default=6, ge=1, le=10)
    rag_top_k: int = Field(default=5, ge=1, le=20)
    vector_store_provider: Literal["milvus", "memory", "none"] = "milvus"
    milvus_uri: str = Field(default="http://localhost:19530", pattern=r"^https?://")
    milvus_token: str = Field(default="", repr=False)
    milvus_db_name: str = Field(default="default", pattern=r"^[A-Za-z_][A-Za-z0-9_]*$", max_length=255)
    milvus_collection: str = Field(default="study_agent_local_hash_v1", pattern=r"^[A-Za-z_][A-Za-z0-9_]*$", max_length=255)
    milvus_timeout: float = Field(default=10.0, gt=0, le=120)

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
