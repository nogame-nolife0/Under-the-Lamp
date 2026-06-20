"""豆包视觉识别：识别 Word 嵌入图片中的公式与小题。"""

from __future__ import annotations

import base64
import logging
from pathlib import Path

from app.config import get_settings
from app.image_normalize import normalize_image_for_vision
from app.llm.doubao_client import create_doubao_client

logger = logging.getLogger(__name__)

STEM_IMAGE_PROMPT = """这是试卷【题干卷】Word 文档中的一张嵌入图片。
请识别图中全部内容，包括但不限于：小题编号 (1)(2)(3)(4)、逻辑函数、卡诺图、真值表、公式。
要求：
1. 按阅读顺序逐条输出，保留小题编号
2. 公式用 LaTeX 风格（如 \\overline{A}、F(A,B,C,D)=...）
3. 只输出识别到的实质内容，不要解释解题方法
4. 不要写「未给出」「需推导」等占位语"""

ANSWER_IMAGE_PROMPT = """这是试卷【答案卷】Word 文档中的一张嵌入图片。
请识别图中全部答案与「解」后的具体内容，包括小题编号、逻辑表达式、最简式结果。
要求：
1. 按 (1)(2)(3)(4) 或卷面顺序逐条输出
2. 公式用 LaTeX 风格保留
3. 只输出识别到的实质答案，不要写解题步骤说明或占位语
4. 若有 F'、\\overline{F}、最简与或式等须完整摘录"""


def vision_ocr_image(image_path: str, parse_mode: str, image_index: int) -> str:
    settings = get_settings()
    if not settings.doubao_api_key or not settings.doubao_model:
        raise ValueError("未配置 DOUBAO_API_KEY 或 DOUBAO_MODEL")

    path = Path(image_path)
    if not path.exists():
        raise FileNotFoundError(f"图片不存在: {image_path}")

    normalized = normalize_image_for_vision(str(path))
    logger.info("视觉识别使用图片: %s (原图: %s)", normalized.name, path.name)

    encoded = base64.b64encode(normalized.read_bytes()).decode("ascii")
    data_url = f"data:image/png;base64,{encoded}"

    mode = (parse_mode or "STEM").upper()
    base_prompt = ANSWER_IMAGE_PROMPT if mode == "ANSWER" else STEM_IMAGE_PROMPT
    prompt = f"{base_prompt}\n\n图片序号：{image_index}"

    client = create_doubao_client()
    response = client.chat.completions.create(
        model=settings.doubao_model,
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "image_url", "image_url": {"url": data_url}},
                    {"type": "text", "text": prompt},
                ],
            }
        ],
        temperature=0.1,
        max_tokens=4096,
    )
    content = response.choices[0].message.content or ""
    return content.strip()
