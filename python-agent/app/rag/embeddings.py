import time

import httpx
from openai import OpenAI

from app.config import get_settings


def create_embedding_client() -> OpenAI:
    settings = get_settings()
    if not settings.doubao_api_key:
        raise ValueError("未配置 DOUBAO_API_KEY 环境变量")
    return OpenAI(
        api_key=settings.doubao_api_key,
        base_url=settings.doubao_base_url,
        timeout=settings.doubao_timeout_seconds,
        max_retries=2,
    )


def _normalize_embedding(raw) -> list[float]:
    if not raw:
        return []
    if isinstance(raw[0], list):
        return [float(v) for v in raw[0]]
    return [float(v) for v in raw]


def _extract_embedding(payload: dict) -> list[float]:
    data = payload.get("data")
    if isinstance(data, dict) and "embedding" in data:
        return _normalize_embedding(data["embedding"])
    if isinstance(data, list) and data:
        first = data[0]
        if isinstance(first, dict):
            return _normalize_embedding(first.get("embedding"))
    raise RuntimeError(f"无法解析向量化响应: {payload}")


def _embed_multimodal_text(text: str, model: str) -> list[float]:
    settings = get_settings()
    url = settings.doubao_base_url.rstrip("/") + "/embeddings/multimodal"
    headers = {
        "Authorization": f"Bearer {settings.doubao_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model,
        "input": [{"type": "text", "text": text}],
        "encoding_format": "float",
    }

    last_error = ""
    with httpx.Client(timeout=settings.doubao_timeout_seconds) as client:
        for attempt in range(3):
            response = client.post(url, json=payload, headers=headers)
            if response.status_code == 200:
                return _extract_embedding(response.json())
            last_error = response.text
            if "ClosedEndpoint" in last_error and attempt < 2:
                time.sleep(1.5 * (attempt + 1))
                continue
            break
    raise RuntimeError(f"多模态向量化失败: {last_error}")


def _embed_multimodal_texts(texts: list[str], model: str) -> list[list[float]]:
    return [_embed_multimodal_text(text, model) for text in texts]


def _embed_text_api(texts: list[str], model: str) -> list[list[float]]:
    client = create_embedding_client()
    response = client.embeddings.create(model=model, input=texts)
    ordered = sorted(response.data, key=lambda item: item.index)
    return [item.embedding for item in ordered]


def embed_texts(texts: list[str]) -> list[list[float]]:
    if not texts:
        return []
    settings = get_settings()
    model = settings.doubao_embedding_model
    if not model:
        raise ValueError("未配置 DOUBAO_EMBEDDING_MODEL（火山方舟 Embedding 接入点 ID）")

    mode = (settings.doubao_embedding_mode or "multimodal").strip().lower()
    if mode == "multimodal":
        return _embed_multimodal_texts(texts, model)
    return _embed_text_api(texts, model)
