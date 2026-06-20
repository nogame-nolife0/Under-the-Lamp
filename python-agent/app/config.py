from functools import lru_cache

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    doubao_api_key: str = ""
    doubao_base_url: str = "https://ark.cn-beijing.volces.com/api/v3"
    doubao_model: str = ""
    doubao_embedding_model: str = ""
    # multimodal: Doubao-embedding-vision（/embeddings/multimodal）
    # text: 旧版纯文本模型（/embeddings，即将下线）
    doubao_embedding_mode: str = "multimodal"

    chroma_persist_dir: str = "./data/chroma"

    agent_host: str = "0.0.0.0"
    agent_port: int = 8001
    agent_version: str = "1.0.0"

    vision_enabled: bool = True
    vision_max_images: int = 80
    vision_parallel_workers: int = 3
    doubao_timeout_seconds: float = 300.0
    paper_upload_base_dir: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()


def get_upload_base_dir() -> Path:
    from pathlib import Path

    settings = get_settings()
    if settings.paper_upload_base_dir.strip():
        return Path(settings.paper_upload_base_dir).expanduser()
    return Path.home() / "paper-data" / "uploads"
