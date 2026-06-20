"""将裸 LaTeX 文本自动包裹 $...$ 以便 KaTeX 渲染。"""

from __future__ import annotations

import re

_MATH_SPLIT_RE = re.compile(r"(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$)")

_PAREN_INLINE_RE = re.compile(r"\\\((.+?)\\\)")
_PAREN_BLOCK_RE = re.compile(r"\\\[(.+?)\\\]")

_LATEX_CMD_RE = re.compile(
    r"\\(?:overline|underline|frac|sqrt|cdot|times|bar|vec|hat|tilde|"
    r"text|mathrm|mathbf|left|right|quad|pm|mp|leq|geq|neq|approx|"
    r"sum|prod|int|alpha|beta|gamma|delta|pi|theta|lambda|mu|sigma|omega)\b"
)

_VAR_EQ_RE = re.compile(r"[A-Za-z]\s*=\s*(?=\\)")


def normalize_latex_delimiters(text: str) -> str:
    if not text:
        return text
    if "[嵌入图片" in text:
        return text
    if "\\" not in text and "$" not in text:
        return text
    text = _PAREN_INLINE_RE.sub(r"$\1$", text)
    text = _PAREN_BLOCK_RE.sub(r"$$\1$$", text)
    parts = _MATH_SPLIT_RE.split(text)
    out: list[str] = []
    for idx, part in enumerate(parts):
        if not part:
            continue
        if idx % 2 == 1:
            out.append(part)
        else:
            out.append(_wrap_bare_latex_in_plain(part))
    return "".join(out)


def normalize_latex_fields(data: dict) -> dict:
    """对解析结果中的文本字段做 LaTeX 规范化。"""
    for key in ("stemRaw", "answerRaw", "analysisRaw"):
        value = data.get(key)
        if isinstance(value, str) and value.strip():
            data[key] = normalize_latex_delimiters(value)
    options = data.get("options")
    if isinstance(options, list):
        data["options"] = [
            normalize_latex_delimiters(opt) if isinstance(opt, str) else opt
            for opt in options
        ]
    return data


def _wrap_bare_latex_in_plain(text: str) -> str:
    if "\\" not in text:
        return text

    result: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        start = _find_latex_start(text, i)
        if start is None:
            result.append(text[i])
            i += 1
            continue
        result.append(text[i:start])
        end = _scan_latex_end(text, start)
        result.append("$")
        result.append(text[start:end])
        result.append("$")
        i = end
    result.append(text[i:])
    return "".join(result)


def _find_latex_start(text: str, pos: int) -> int | None:
    n = len(text)
    for i in range(pos, n):
        if text[i] == "\\" and _LATEX_CMD_RE.match(text, i):
            prefix = _VAR_EQ_RE.search(text[pos:i])
            return pos + prefix.start() if prefix else i
        eq = _VAR_EQ_RE.match(text, i)
        if eq and _LATEX_CMD_RE.search(text, i + eq.end()):
            return i
    return None


def _scan_latex_end(text: str, start: int) -> int:
    i = start
    n = len(text)
    while i < n:
        if text[i] == "\\":
            cmd = _LATEX_CMD_RE.match(text, i)
            if not cmd:
                break
            i = cmd.end()
            while i < n and text[i] == " ":
                i += 1
            if i < n and text[i] == "{":
                i = _skip_brace_group(text, i) + 1
            continue

        if text[i] in "_{^":
            i += 1
            if i < n and text[i] == "{":
                i = _skip_brace_group(text, i) + 1
            elif i < n:
                i += 1
            continue

        if text[i] in "+-=·()[]":
            i += 1
            continue

        if text[i].isspace():
            j = i + 1
            while j < n and text[j].isspace():
                j += 1
            if j < n and (text[j] == "\\" or text[j] in "+-=·"):
                i = j
                continue
            break

        if text[i].isascii() and (text[i].isalnum() or text[i] in ",."):
            i += 1
            continue

        if "\u4e00" <= text[i] <= "\u9fff":
            break

        break
    return i


def _skip_brace_group(text: str, pos: int) -> int:
    if pos >= len(text) or text[pos] != "{":
        return pos
    depth = 0
    i = pos
    while i < len(text):
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return pos
