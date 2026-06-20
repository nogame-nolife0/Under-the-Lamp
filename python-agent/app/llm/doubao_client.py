"""
豆包 / 火山方舟 LLM 客户端（OpenAI 兼容接口）。
"""

from openai import OpenAI

from app.config import get_settings


def create_doubao_client() -> OpenAI:
    settings = get_settings()
    if not settings.doubao_api_key:
        raise ValueError("未配置 DOUBAO_API_KEY 环境变量")
    return OpenAI(
        api_key=settings.doubao_api_key,
        base_url=settings.doubao_base_url,
        timeout=settings.doubao_timeout_seconds,
        max_retries=2,
    )


def chat(prompt: str, system: str = "你是一个专业的试卷题目解析助手。") -> str:
    settings = get_settings()
    if not settings.doubao_model:
        raise ValueError("未配置 DOUBAO_MODEL（火山方舟推理接入点 ID）")

    client = create_doubao_client()
    response = client.chat.completions.create(
        model=settings.doubao_model,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        temperature=0.1,
        max_tokens=8192,
    )
    return response.choices[0].message.content or ""
