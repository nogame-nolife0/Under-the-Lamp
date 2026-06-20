"""测试 LLM JSON 反斜杠修复逻辑。"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.graph_workflow import _load_json_array, _repair_json_latex_in_strings


def assert_loads(raw: str, expected_answer_substr: str) -> None:
    data = _load_json_array(raw)
    answer = data[0].get("answerRaw") or ""
    assert expected_answer_substr in answer, (answer, expected_answer_substr)
    print("OK:", expected_answer_substr[:40])


def main() -> None:
    # 非法转义 \o
    assert_loads(
        """[
  {
    "seqNo": 1,
    "questionLabel": "2-1",
    "answerRaw": "(1) $F' = \\overline{A}B + A\\overline{B}$"
  }
]""",
        "\\overline{A}",
    )

    # \f 被误判为 form feed
    assert_loads(
        """[
  {
    "seqNo": 1,
    "answerRaw": "$F = \\frac{1}{2}$"
  }
]""",
        "\\frac",
    )

    # \n 被误判为换行
    assert_loads(
        """[
  {
    "seqNo": 1,
    "answerRaw": "$a \\neq b$"
  }
]""",
        "\\neq",
    )

    repaired = _repair_json_latex_in_strings('"\\overline{A}"')
    assert json.loads(f'[{repaired}]') == ["\\overline{A}"]

    print("\n全部 JSON 修复测试通过")


if __name__ == "__main__":
    main()
