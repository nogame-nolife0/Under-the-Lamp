"""
测试豆包 API 是否配置正确。
用法：在 python-agent 目录下执行  python scripts/test_doubao.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import get_settings
from app.llm.doubao_client import chat


def main() -> None:
    settings = get_settings()
    print("DOUBAO_API_KEY:", "已配置" if settings.doubao_api_key else "未配置")
    print("DOUBAO_MODEL:", settings.doubao_model or "未配置")
    print("DOUBAO_BASE_URL:", settings.doubao_base_url)

    if not settings.doubao_api_key or not settings.doubao_model:
        print("\n请先配置环境变量 DOUBAO_API_KEY 和 DOUBAO_MODEL")
        sys.exit(1)

    reply = chat("请用一句话介绍你能做什么。")
    print("\n豆包回复：", reply)


if __name__ == "__main__":
    main()
