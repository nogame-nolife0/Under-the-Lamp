"""
测试 Embedding 是否配置正确。
用法：在 python-agent 目录下执行  python scripts/test_embedding.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import get_settings
from app.rag.embeddings import embed_texts


def main() -> None:
    settings = get_settings()
    print("DOUBAO_API_KEY:", "已配置" if settings.doubao_api_key else "未配置")
    print("DOUBAO_EMBEDDING_MODEL:", settings.doubao_embedding_model or "未配置")
    print("DOUBAO_EMBEDDING_MODE:", settings.doubao_embedding_mode)

    if not settings.doubao_api_key or not settings.doubao_embedding_model:
        print("\n请先配置 DOUBAO_API_KEY 和 DOUBAO_EMBEDDING_MODEL")
        sys.exit(1)

    vectors = embed_texts(["数字电子技术 触发器 时序逻辑"])
    print("\n向量化成功，维度:", len(vectors[0]))


if __name__ == "__main__":
    main()
