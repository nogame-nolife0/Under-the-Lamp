"""LangGraph 工作流：Word 文本 → 豆包结构化题目 JSON。"""

import json
import logging
import re
import shutil
from concurrent.futures import ThreadPoolExecutor, as_completed
from decimal import Decimal
from pathlib import Path
from typing import Any, TypedDict

logger = logging.getLogger(__name__)

from langgraph.graph import END, StateGraph

from app.config import get_settings, get_upload_base_dir
from app.doc_parser import count_images, extract_document_blocks, truncate_text
from app.latex_normalize import normalize_latex_delimiters
from app.image_normalize import ensure_display_png
from app.llm.doubao_client import chat
from app.llm.doubao_vision import vision_ocr_image
from app.models.schemas import (
    ParseWordData,
    ParseWordItem,
    ParseWordRequest,
    ParseWordSummary,
)


class ParseState(TypedDict):
    request: dict[str, Any]
    raw_text: str
    image_registry: dict[int, dict[str, str]]
    llm_json: list[dict[str, Any]]
    items: list[ParseWordItem]
    error: str | None


PARSE_SYSTEM_PROMPT_AUTO = """你是专业的试卷题目解析助手。
用户会提供从 Word 提取的原始文本（格式可能不统一）。
请识别每一道题，拆出题干、答案、解析（如有）、选项（如有），并判断题型与难度。

题型只能是以下之一：
SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE, FILL_BLANK, SHORT_ANSWER, CALCULATION, ESSAY, UNKNOWN

难度只能是：EASY, MEDIUM, HARD 或 null

必须只输出 JSON 数组，不要 markdown，不要额外说明。
JSON 字符串内所有反斜杠必须双写转义（如 LaTeX \\overline{A} 在 JSON 中写作 \\\\overline{A}）。
格式如下：
[
  {
    "seqNo": 1,
    "stemRaw": "题干全文",
    "answerRaw": "答案全文或null",
    "analysisRaw": "解析全文或null",
    "options": ["A. ...", "B. ..."] 或 null,
    "questionType": "SHORT_ANSWER",
    "difficulty": "MEDIUM",
    "chapter": "章节名或null",
    "knowledgePoints": ["知识点1"] 或 null,
    "confidenceScore": 0.85,
    "warnings": ["警告说明"] 或 null
  }
]

规则：
1. 答案可能在题后、卷末「参考答案」区，尽量配对
2. 无法配对时 answerRaw 设为 null，confidenceScore 低于 0.6，warnings 说明原因
3. 不要遗漏明显独立的题目
4. confidenceScore 范围 0~1"""

PARSE_SYSTEM_PROMPT_STEM = """你是专业的试卷题目解析助手。
用户提供的是【试卷/题干卷】Word 文本，本题源与答案卷分开存放，本文件通常只有题干和选项。

请只识别每一道题的题干与选项，不要猜测或编造答案。

题型只能是以下之一：
SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE, FILL_BLANK, SHORT_ANSWER, CALCULATION, ESSAY, UNKNOWN

难度只能是：EASY, MEDIUM, HARD 或 null

必须只输出 JSON 数组，不要 markdown，不要额外说明。
JSON 字符串内所有反斜杠必须双写转义（如 LaTeX \\overline{A} 在 JSON 中写作 \\\\overline{A}）。
格式如下：
[
  {
    "seqNo": 1,
    "questionLabel": "2-1",
    "stemRaw": "题干全文（含全部公式与小问）",
    "answerRaw": null,
    "analysisRaw": null,
    "options": ["A. ...", "B. ..."] 或 null,
    "questionType": "CALCULATION",
    "difficulty": "MEDIUM",
    "chapter": null,
    "knowledgePoints": null,
    "confidenceScore": 0.90,
    "warnings": null
  }
]

规则：
1. seqNo 按题目出现顺序从 1 递增
2. questionLabel 填写卷面主题号（如 4-1、2-1），必须与原文一致，不得省略
3. stemRaw 必须逐字完整摘录题干，包含：题号、全部文字、全部 [嵌入图片N] 占位符、二进制/数字条件、问句结尾（如「为多少？」）
4. 禁止截断：图片前后的文字（如「按顺序 10101011 输入时，输出」「为多少？」）必须保留
5. 含 (1)(2)(3)(4) 等多小问的大题合并为一道
6. 公式图用 [嵌入图片N] 占位，不要把 OCR 全文抄进 stemRaw
7. [嵌入图片N] 必须保留在原句中的位置，禁止堆到段末
8. 逻辑/计算题 questionType 优先 CALCULATION
9. answerRaw 必须始终为 null
10. confidenceScore 范围 0~1"""

PARSE_SYSTEM_PROMPT_ANSWER = """你是专业的试卷答案解析助手。
用户提供的是【答案卷】Word 文本，与题干卷分开，通常按题号列出答案与解析。

请识别每一题的题号、答案、解析（如有），不需要重复题干。

必须只输出 JSON 数组，不要 markdown，不要额外说明。
JSON 字符串内所有反斜杠必须双写转义（如 LaTeX \\overline{A} 在 JSON 中写作 \\\\overline{A}）。
格式如下：
[
  {
    "seqNo": 1,
    "questionLabel": "2-1",
    "stemRaw": null,
    "answerRaw": "答案全文（含具体公式结果）",
    "analysisRaw": "解析全文或null",
    "options": null,
    "questionType": "CALCULATION",
    "difficulty": null,
    "chapter": null,
    "knowledgePoints": null,
    "confidenceScore": 0.90,
    "warnings": null
  }
]

规则：
1. seqNo 按答案出现顺序从 1 递增
2. questionLabel 必须与题干卷题号一致（如 2-1），用于配对
3. answerRaw 必须逐字摘录卷面上的具体求解结果（如 F'=...、\\overline{F}=...），含全部 (1)(2)(3)(4) 小问答案
4. 严禁输出「未给出」「需推导」「需依据规则」等占位说明；卷面有「解：」则提取解后全部内容
5. 公式用 LaTeX 风格保留：\\overline{A}、F' 等
6. 文本中 [嵌入图片N] 段为图片 OCR 结果，必须完整写入 answerRaw
7. stemRaw 可设为 null
8. confidenceScore 范围 0~1"""


def _ocr_single_image(image_path: str, parse_mode: str, image_index: int) -> str:
    try:
        return vision_ocr_image(image_path, parse_mode, image_index)
    except Exception as exc:
        logger.warning("图片 %s 识别失败: %s", image_index, exc)
        return f"(图片识别失败: {exc})"


IMAGE_MARKER_RE = re.compile(r"\[嵌入图片(\d+)\]")


def _register_display_image(image_path: str, image_index: int) -> dict[str, str]:
    try:
        display_path = str(ensure_display_png(image_path))
    except Exception as exc:
        logger.warning("图片 %s 展示用 PNG 生成失败: %s", image_index, exc)
        display_path = image_path
    return {
        "file_path": image_path,
        "display_path": display_path,
        "placeholder": f"[嵌入图片{image_index}]",
    }


def _build_raw_text_with_vision(blocks, parse_mode: str) -> tuple[str, dict[int, dict[str, str]]]:
    settings = get_settings()
    segments: list[str | tuple[str, int, str]] = []
    image_jobs: list[tuple[int, str, int]] = []
    image_registry: dict[int, dict[str, str]] = {}
    image_count = count_images(blocks)
    img_serial = 0

    for block in blocks:
        if block.kind == "text":
            segments.append(block.content)
            continue
        if block.kind == "break":
            segments.append("\n")
            continue

        img_serial += 1
        if not settings.vision_enabled:
            segments.append(f"[嵌入图片{img_serial}]")
            image_registry[img_serial] = _register_display_image(block.content, img_serial)
            image_registry[img_serial]["ocr_text"] = "(未启用视觉识别)"
            continue

        if img_serial > settings.vision_max_images:
            segments.append(f"[嵌入图片{img_serial}]")
            image_registry[img_serial] = _register_display_image(block.content, img_serial)
            image_registry[img_serial]["ocr_text"] = (
                f"(超出视觉识别数量上限 {settings.vision_max_images})"
            )
            continue

        placeholder_index = len(segments)
        segments.append(("__image__", img_serial, block.content))
        image_jobs.append((placeholder_index, block.content, img_serial))
        image_registry[img_serial] = _register_display_image(block.content, img_serial)

    ocr_results: dict[int, str] = {}
    if image_jobs and settings.vision_enabled:
        workers = max(1, min(settings.vision_parallel_workers, len(image_jobs)))
        logger.info("开始并行识别 %s 张图片，并发数=%s", len(image_jobs), workers)
        with ThreadPoolExecutor(max_workers=workers) as executor:
            future_map = {
                executor.submit(_ocr_single_image, path, parse_mode, index): placeholder_index
                for placeholder_index, path, index in image_jobs
            }
            for future in as_completed(future_map):
                placeholder_index = future_map[future]
                try:
                    ocr_results[placeholder_index] = future.result()
                except Exception as exc:
                    ocr_results[placeholder_index] = f"(图片识别失败: {exc})"

    output_parts: list[str] = []
    success_count = 0
    for idx, item in enumerate(segments):
        if isinstance(item, tuple) and item[0] == "__image__":
            _, index, _ = item
            ocr_text = ocr_results.get(idx, "(图片识别失败: 未返回结果)")
            if not ocr_text.startswith("(图片识别失败"):
                success_count += 1
                ocr_text = normalize_latex_delimiters(ocr_text)
            image_registry[index]["ocr_text"] = ocr_text
            output_parts.append(f"[嵌入图片{index}]")
        else:
            output_parts.append(str(item))

    if image_count > 0 and success_count == 0 and settings.vision_enabled:
        output_parts.append(
            "\n[提示] 文档含 "
            f"{image_count} 张嵌入图片，但均未成功识别，请检查 Pillow 是否安装、EMF 转换是否正常"
        )

    return "".join(output_parts), image_registry


def _format_image_ocr_appendix(image_registry: dict[int, dict[str, str]]) -> str:
    if not image_registry:
        return ""
    lines = ["", "图片 OCR 参考（仅供理解，勿在 stemRaw 中重复抄写；必须保留 [嵌入图片N] 在原位置）："]
    for index in sorted(image_registry):
        reg = image_registry[index]
        ocr_text = reg.get("ocr_text") or "(无 OCR 结果)"
        lines.append(f"[嵌入图片{index}]: {ocr_text}")
    return "\n".join(lines)


def _inline_image_markers(text: str | None) -> str | None:
    if not text:
        return text
    normalized = str(text)
    normalized = re.sub(r"\s*\[嵌入图片(\d+)\]\s*", r"[嵌入图片\1]", normalized)
    normalized = re.sub(r"\[嵌入图片(\d+)\](?!\[)", r"[嵌入图片\1]", normalized)
    return normalized.strip()


QUESTION_LABEL_PATTERNS = (
    re.compile(r"(?:(?<=\n)|^)(\d+-\d+)\s*"),
    re.compile(r"(?<=[。？?；!！\n])(\d+-\d+)\s+(?=[\u4e00-\u9fff])"),
    re.compile(r"(?<=\s)(\d+-\d+)\s+(?=解|[\(（\u4e00-\u9fff])"),
    re.compile(r"(?:(?<=\n)|^)(\d+)[.、．]\s*"),
    re.compile(r"(?<=[。？?；!！\n])(\d+)[.、．]\s+(?=[\u4e00-\u9fff])"),
    re.compile(r"(?:(?<=\n)|^)(\d+)\s+(?=[\u4e00-\u9fffA-Za-z])"),
)

_QUESTION_OPENING_RE = re.compile(
    r"(设计一个|写出下图|试用\s*74|实现下列|优先编码器|用三线-八线|某车间有)"
)


def _significant_question_openings(text: str) -> list[re.Match[str]]:
    """题干内多处 opening 时，仅保留像「新题开头」的匹配，避免「设计一个…优先编码器」误判。"""
    matches = list(_QUESTION_OPENING_RE.finditer(text))
    if not matches:
        return []
    significant: list[re.Match[str]] = [matches[0]]
    for match in matches[1:]:
        prev = significant[-1]
        between = text[prev.end() : match.start()]
        if prev.group() == "设计一个" and match.group() == "优先编码器":
            continue
        if re.search(r"[。？?；!！\n]", between) or len(between.strip()) >= 12:
            significant.append(match)
    return significant


def _find_question_label_matches(text: str) -> list[re.Match[str]]:
    matches_by_start: dict[int, re.Match[str]] = {}
    for pattern in QUESTION_LABEL_PATTERNS:
        for match in pattern.finditer(text):
            start = match.start(1)
            if start not in matches_by_start:
                matches_by_start[start] = match
    return sorted(matches_by_start.values(), key=lambda item: item.start(1))


def _split_raw_into_questions(raw_text: str) -> list[dict[str, str | None]]:
    text = (raw_text or "").strip()
    if not text:
        return []
    matches = [
        match
        for match in _find_question_label_matches(text)
        if _is_valid_question_label(match.group(1))
    ]
    if matches:
        segments: list[dict[str, str | None]] = []
        for index, match in enumerate(matches):
            label = match.group(1)
            start = match.start(1)
            end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
            content = text[start:end].strip()
            if content:
                segments.append({"label": label, "content": content, "start": start, "end": end})
        if segments:
            return segments
    return [{"label": None, "content": text, "start": 0, "end": len(text)}]


def _is_valid_question_label(label: str | None) -> bool:
    if not label:
        return False
    normalized = str(label).strip()
    if re.fullmatch(r"\d+-\d+", normalized):
        return True
    if re.fullmatch(r"\d+", normalized):
        return 1 <= int(normalized) <= 50
    return False


def _clean_stem_garbage(text: str | None) -> str:
    if not text:
        return ""
    cleaned = re.sub(r"^[\s01\n]{4,}", "", text.strip())
    return cleaned.strip() or text.strip()


def _segment_to_stem(segment_content: str, question_label: str | None) -> str:
    content = (segment_content or "").strip()
    stem = _strip_options_from_segment(content)
    label = _normalize_question_label(question_label)
    if label:
        stem = _clip_stem_to_question_label(stem, label)
    else:
        stem = _trim_at_next_question_label(stem)
    if _stem_looks_merged(stem, label):
        stem = _clip_at_second_opening(stem)
    return _clean_stem_garbage(stem)


def _clip_at_second_opening(text: str) -> str:
    matches = _significant_question_openings(text)
    if len(matches) < 2:
        return text
    return text[: matches[1].start()].strip() or text


def _stem_completeness_score(text: str | None) -> int:
    if not text:
        return 0
    score = len(text.strip())
    score += len(IMAGE_MARKER_RE.findall(text)) * 80
    if "？" in text or "?" in text:
        score += 120
    if "多少" in text or "试求" in text or "求出" in text:
        score += 80
    if re.search(r"\b[01]{4,}\b", text):
        score += 30
    if re.search(r"\d+-\d+", text):
        score += 20
    if _stem_looks_incomplete(text):
        score -= 200
    return score


def _stem_quality_key(text: str | None) -> tuple[int, int, int, int, int]:
    """越大越好：优先完整问句，其次 Word 原文图片占位，再比汉字量。"""
    if not text or not str(text).strip():
        return (0, 0, 0, 0, 0)
    normalized = (_inline_image_markers(str(text)) or str(text)).strip()
    has_question = int(
        "？" in normalized
        or "?" in normalized
        or "多少" in normalized
        or "试求" in normalized
        or "求出" in normalized
    )
    not_incomplete = 0 if _stem_looks_incomplete(normalized) else 1
    marker_count = len(IMAGE_MARKER_RE.findall(normalized))
    return (
        has_question,
        not_incomplete,
        marker_count,
        _han_count(normalized),
        _stem_completeness_score(normalized),
    )


def _prefixes_for_raw_match(text: str) -> list[str]:
    plain = re.sub(r"\$\$[\s\S]+?\$\$|\$[^$\n]+?\$", "", text)
    plain = IMAGE_MARKER_RE.sub("", plain).strip()
    prefixes: list[str] = []
    seen: set[str] = set()

    def add(value: str) -> None:
        value = value.strip()
        if len(value) >= 6 and value not in seen:
            seen.add(value)
            prefixes.append(value)

    for length in (50, 40, 30, 25, 20, 15):
        if len(plain) >= 6:
            add(plain[: min(length, len(plain))])
    for run in re.findall(r"[\u4e00-\u9fff]+", plain):
        for length in (20, 15, 12, 10, 8):
            if len(run) >= length:
                add(run[:length])
    return prefixes


def _get_segment_bounds(
    segments: list[dict[str, str | None]],
    question_label: str | None,
    seq_no: int,
) -> tuple[int, int] | None:
    normalized = _normalize_question_label(question_label)
    if normalized:
        for segment in segments:
            if _normalize_question_label(segment.get("label")) == normalized:
                start = segment.get("start")
                end = segment.get("end")
                if isinstance(start, int) and isinstance(end, int):
                    return start, end
    if 1 <= seq_no <= len(segments):
        segment = segments[seq_no - 1]
        start = segment.get("start")
        end = segment.get("end")
        if isinstance(start, int) and isinstance(end, int):
            return start, end
    return None


def _trim_at_next_question_label(text: str, skip: int = 0) -> str:
    if not text:
        return ""
    best = len(text)
    for pattern in QUESTION_LABEL_PATTERNS:
        for match in pattern.finditer(text, skip):
            if match.start() > 0 and match.start() < best:
                best = match.start()
    return text[:best].strip() if best < len(text) else text.strip()


def _stem_looks_merged(stem: str | None, question_label: str | None) -> bool:
    if not stem:
        return False
    text = stem.strip()
    own = _normalize_question_label(question_label)
    labels: list[str] = []
    for match in QUESTION_LABEL_PATTERNS[0].finditer(text):
        label = _normalize_question_label(match.group(1))
        if label:
            labels.append(label)
    if own:
        if any(label != own for label in labels):
            return True
    elif len(set(labels)) > 1:
        return True
    has_how_many = bool(re.search(r"为多少[？?]", text))
    has_blank_choice = bool(re.search(r"应[（(]\s*[）)]", text))
    if has_how_many and has_blank_choice:
        return True
    if len(_significant_question_openings(text)) >= 2:
        return True
    question_marks = text.count("？") + text.count("?")
    if question_marks >= 2 and has_how_many and has_blank_choice:
        return True
    return False


def _clip_stem_to_question_label(stem: str | None, question_label: str | None) -> str:
    if not stem:
        return ""
    text = stem.strip()
    label = _normalize_question_label(question_label)
    if not label:
        return _trim_at_next_question_label(text)
    start = 0
    for pattern in _label_start_patterns(label):
        match = pattern.search(text)
        if match:
            start = match.start()
            break
    clipped = text[start:].strip()
    search_from = max(len(label), 2)
    clipped = _trim_at_next_question_label(clipped, search_from)
    return clipped or text


def _normalize_question_label(label: str | None) -> str | None:
    if not label:
        return None
    return re.sub(r"\s+", "", str(label).strip())


def _find_next_question_start(raw_text: str, start_pos: int) -> int:
    end = len(raw_text)
    for pattern in QUESTION_LABEL_PATTERNS:
        match = pattern.search(raw_text, start_pos)
        if match and match.start() < end:
            end = match.start()
    return end


def _label_start_patterns(label: str) -> list[re.Pattern]:
    escaped = re.escape(label)
    patterns = [
        re.compile(r"(?:(?<=\n)|^)" + escaped + r"[.、．]\s*"),
    ]
    if re.fullmatch(r"\d+-\d+", label):
        patterns.insert(0, re.compile(r"(?:(?<=\n)|^)" + escaped + r"(?=\S)"))
        patterns.insert(1, re.compile(r"(?:(?<=\n)|^)" + escaped + r"\s+"))
    elif label.isdigit():
        patterns.append(
            re.compile(r"(?:(?<=\n)|^)" + escaped + r"\s+(?=[\u4e00-\u9fff(（])")
        )
    return patterns


def _find_raw_segment(
    question_label: str | None,
    seq_no: int,
    segments: list[dict[str, str | None]],
    raw_text: str,
) -> str | None:
    normalized_label = _normalize_question_label(question_label)
    if normalized_label:
        for segment in segments:
            if _normalize_question_label(segment.get("label")) == normalized_label:
                return str(segment.get("content") or "")
        for pattern in _label_start_patterns(normalized_label):
            match = pattern.search(raw_text)
            if match:
                start = match.start()
                end = _find_next_question_start(raw_text, match.end())
                return raw_text[start:end].strip()
    if segments and 1 <= seq_no <= len(segments):
        return str(segments[seq_no - 1].get("content") or "")
    return None


def _extend_stem_in_raw(
    stem_hint: str | None,
    raw_text: str,
    end_pos: int | None = None,
) -> str | None:
    hint = (stem_hint or "").strip()
    if not hint or not raw_text:
        return None
    best: str | None = None
    best_key = (-1, -1, -1, -1, -1)
    for prefix in _prefixes_for_raw_match(hint):
        idx = raw_text.find(prefix)
        if idx < 0:
            continue
        end = _find_next_question_start(raw_text, idx + len(prefix))
        if end_pos is not None:
            end = min(end, end_pos)
        candidate = _strip_options_from_segment(raw_text[idx:end].strip())
        if not candidate:
            continue
        normalized = _inline_image_markers(candidate) or candidate
        key = _stem_quality_key(normalized)
        if key > best_key:
            best = normalized.strip()
            best_key = key
    return best


def _han_count(text: str | None) -> int:
    if not text:
        return 0
    return len(re.findall(r"[\u4e00-\u9fff]", text))


def _pick_best_stem(*candidates: str | None) -> str:
    best = ""
    best_key = (0, 0, 0, 0, 0)
    for candidate in candidates:
        if not candidate:
            continue
        normalized = _inline_image_markers(candidate) or candidate
        key = _stem_quality_key(normalized)
        if key > best_key:
            best = normalized.strip()
            best_key = key
    return best


def _strip_options_from_segment(content: str) -> str:
    lines = content.splitlines()
    stem_lines: list[str] = []
    for line in lines:
        stripped = line.strip()
        if re.match(r"^[A-Ha-h][.、．、:：]", stripped):
            break
        stem_lines.append(line)
    result = "\n".join(stem_lines).strip()
    if result:
        inline_option = _OPTION_MARKER_RE.search(result)
        if inline_option and inline_option.start() > 10:
            result = result[: inline_option.start()].strip()
    if not result:
        inline_option = _OPTION_MARKER_RE.search(content)
        if inline_option and inline_option.start() > 10:
            result = content[: inline_option.start()].strip()
    result = _trim_at_next_question_label(result or content.strip(), skip=8)
    return result or content.strip()


_OPTION_MARKER_RE = re.compile(
    r"(?:(?<=\n)|^|\s|(?<=[\]\)）0-9x×\^]))([A-Ha-h])[.、．、:：]\s*"
)
_OPTION_LABEL_RE = re.compile(r"([A-Ha-h])[.、．、:：]\s*")


def _split_option_markers(text: str) -> list[str]:
    if not text:
        return []
    matches = list(_OPTION_LABEL_RE.finditer(text))
    if not matches:
        return []

    start_idx = next((i for i, match in enumerate(matches) if match.group(1).upper() == "A"), None)
    if start_idx is None:
        return []

    selected: list[re.Match[str]] = []
    expected = 0
    for match in matches[start_idx:]:
        letter_ord = ord(match.group(1).upper()) - ord("A")
        if letter_ord == expected:
            selected.append(match)
            expected += 1
        elif letter_ord == 0 and expected >= 2:
            break
        if expected >= 8:
            break

    if len(selected) < 2:
        return []

    extracted: list[str] = []
    for index, match in enumerate(selected):
        body_start = match.end()
        body_end = selected[index + 1].start() if index + 1 < len(selected) else len(text)
        body = text[body_start:body_end].strip()
        body = _trim_at_next_question_label(body)
        if body:
            extracted.append(f"{match.group(1).upper()}. {body}")
    return extracted


def _find_options_region(content: str) -> str:
    first_option = re.search(
        r"(?:(?<=\n)|(?<=[。；!?！？])|\s)(A)[.、．、:：]\s*",
        content,
        re.I,
    )
    if not first_option:
        first_option = re.search(r"^(\s*)(A)[.、．、:：]\s*", content, re.I | re.M)
    if not first_option:
        return ""
    region = content[first_option.start(1) :]
    return _trim_at_next_question_label(region, skip=2)


def _normalize_options_list(options: list[str] | None) -> list[str] | None:
    if not options:
        return None
    flattened: list[str] = []
    for option in options:
        text = str(option).strip()
        if not text:
            continue
        marker_count = len(_OPTION_LABEL_RE.findall(text))
        if marker_count > 1:
            flattened.extend(_split_option_markers(text))
        else:
            flattened.append(text)
    if not flattened:
        return None

    result: list[str] = []
    expected = 0
    for option in flattened:
        match = re.match(r"^([A-Ha-h])[.、．、:：]", option)
        if not match:
            continue
        letter_ord = ord(match.group(1).upper()) - ord("A")
        if letter_ord == expected:
            result.append(option)
            expected += 1
        elif letter_ord == 0 and expected >= 2:
            break
        if expected >= 8:
            break
    if len(result) >= 2:
        return result
    return flattened[:8] if flattened else None


def _extract_options_from_segment(content: str) -> list[str] | None:
    region = _find_options_region(content)
    if region:
        extracted = _split_option_markers(region)
        if len(extracted) >= 2:
            return extracted

    options: list[str] = []
    for line in content.splitlines():
        stripped = line.strip()
        match = re.match(r"^([A-Ha-h])[.、．、:：]\s*(.+)$", stripped)
        if match:
            body = match.group(2).strip()
            if len(_OPTION_LABEL_RE.findall(body)) > 1:
                options.extend(_split_option_markers(f"{match.group(1).upper()}. {body}"))
            else:
                options.append(f"{match.group(1).upper()}. {body}")

    normalized = _normalize_options_list(options)
    if normalized and len(normalized) >= 2:
        return normalized

    matches = list(_OPTION_MARKER_RE.finditer(content))
    if not matches:
        return normalized

    extracted: list[str] = []
    for index, match in enumerate(matches):
        label = match.group(1).upper()
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(content)
        body = content[start:end].strip()
        body = _trim_at_next_question_label(body)
        if body:
            extracted.append(f"{label}. {body}")

    return _normalize_options_list(extracted) or normalized


def _option_image_count(options: list[str] | None) -> int:
    if not options:
        return 0
    return sum(len(IMAGE_MARKER_RE.findall(str(option))) for option in options)


def _options_completeness(options: list[str] | None) -> int:
    if not options:
        return 0
    normalized = _normalize_options_list(list(options)) or options
    score = sum(
        len(str(option)) + 50 * len(IMAGE_MARKER_RE.findall(str(option)))
        for option in normalized
    )
    if len(normalized) >= 4 and all(
        re.match(r"^[A-H][.、．、:：]", str(option), re.I) for option in normalized[:4]
    ):
        score += 500
    for option in options:
        if len(_OPTION_LABEL_RE.findall(str(option))) > 1:
            score -= 400
    if len(options) > len(normalized) + 1:
        score -= 300
    return score


def _reconcile_stem_from_raw(
    stem_raw: str | None,
    question_label: str | None,
    seq_no: int,
    raw_text: str,
    segments: list[dict[str, str | None]],
) -> tuple[str, list[str] | None]:
    segment = _find_raw_segment(question_label, seq_no, segments, raw_text)
    if segment:
        segment = _inline_image_markers(segment) or segment
        raw_options = _extract_options_from_segment(segment)
        segment_stem = _segment_to_stem(segment, question_label)
        return segment_stem, raw_options

    clipped_llm = _clip_stem_to_question_label(stem_raw, question_label) if stem_raw else ""
    clipped_llm = _clean_stem_garbage(clipped_llm)
    return clipped_llm, None


def _answer_looks_merged(answer: str | None, question_label: str | None) -> bool:
    if not answer:
        return False
    text = answer.strip()
    own = _normalize_question_label(question_label)
    labels: list[str] = []
    label_patterns = (
        *QUESTION_LABEL_PATTERNS[:2],
        re.compile(r"(?<=\s)(\d+-\d+)\s+"),
        re.compile(r"(?<=\s)(\d+)[.、．]\s+(?=[\u4e00-\u9fff(（])"),
    )
    for pattern in label_patterns:
        for match in pattern.finditer(text):
            label = _normalize_question_label(match.group(1))
            if label:
                labels.append(label)
    if own:
        return any(label != own for label in labels)
    return len(set(labels)) > 1


def _segment_to_answer(segment_content: str, question_label: str | None) -> str:
    content = (segment_content or "").strip()
    label = _normalize_question_label(question_label)
    if label:
        answer = _clip_stem_to_question_label(content, label)
    else:
        answer = _trim_at_next_question_label(content)
    answer = _strip_options_from_segment(answer)
    if label:
        answer = _clip_stem_to_question_label(answer, label)
    answer = _trim_at_next_question_label(answer, skip=8)
    answer = _compact_choice_answer(answer)
    return _clean_stem_garbage(answer)


_ANSWER_CHOICE_RE = re.compile(
    r"(?:[为应选]|需要|结论|答案)\s*[（(]\s*([A-Ha-h])\s*[）)]"
)
_ANSWER_SOLUTION_RE = re.compile(r"解\s*[：:]")


def _compact_choice_answer(text: str) -> str:
    if not text:
        return ""
    stripped = text.strip()
    if _ANSWER_SOLUTION_RE.search(stripped):
        return stripped
    match = _ANSWER_CHOICE_RE.search(stripped)
    if match:
        return match.group(1).upper()
    tail = re.search(r"[（(]\s*([A-Ha-h])\s*[）)]\s*[。．]?\s*$", stripped)
    if tail and re.search(r"[\u4e00-\u9fff]", stripped):
        return tail.group(1).upper()
    return stripped


def _reconcile_answer_from_raw(
    answer_raw: str | None,
    question_label: str | None,
    seq_no: int,
    raw_text: str,
    segments: list[dict[str, str | None]],
) -> str:
    segment = _find_raw_segment(question_label, seq_no, segments, raw_text)
    if segment:
        segment = _inline_image_markers(segment) or segment
        return _segment_to_answer(segment, question_label)
    if answer_raw:
        clipped = _clip_stem_to_question_label(str(answer_raw), question_label)
        clipped = _strip_options_from_segment(clipped)
        clipped = _clip_stem_to_question_label(clipped, question_label)
        clipped = _trim_at_next_question_label(clipped, skip=8)
        return _clean_stem_garbage(_compact_choice_answer(clipped))
    return ""


def _extract_text_node(state: ParseState) -> ParseState:
    request = ParseWordRequest.model_validate(state["request"])
    parse_mode = _resolve_parse_mode(request)
    blocks = extract_document_blocks(request.file_path)
    text, image_registry = _build_raw_text_with_vision(blocks, parse_mode)
    if not text.strip():
        raise ValueError("Word 文件中未提取到有效内容")
    state["raw_text"] = truncate_text(text)
    state["image_registry"] = image_registry
    return state


def _resolve_parse_mode(request: ParseWordRequest) -> str:
    mode = (request.parse_mode or "AUTO").upper()
    if mode in {"STEM", "ANSWER", "AUTO"}:
        return mode
    return "AUTO"


def _resolve_system_prompt(parse_mode: str) -> str:
    if parse_mode == "STEM":
        return PARSE_SYSTEM_PROMPT_STEM
    if parse_mode == "ANSWER":
        return PARSE_SYSTEM_PROMPT_ANSWER
    return PARSE_SYSTEM_PROMPT_AUTO


def _llm_parse_node(state: ParseState) -> ParseState:
    request = ParseWordRequest.model_validate(state["request"])
    parse_mode = _resolve_parse_mode(request)
    keywords = (
        request.hints.answer_section_keywords
        if request.hints
        else ["参考答案", "答案解析"]
    )

    mode_hint = {
        "STEM": "这是题干卷，只输出题干与选项，answerRaw 一律 null。",
        "ANSWER": "这是答案卷，只输出题号 seqNo 与 answerRaw/analysisRaw。",
        "AUTO": "题干与答案可能在同一文件，请尽量配对。",
    }[parse_mode]

    user_prompt = f"""解析模式：{parse_mode}
说明：{mode_hint}
答案区关键词参考：{", ".join(keywords)}

Word 原始文本：
---
{state["raw_text"]}
---{_format_image_ocr_appendix(state.get("image_registry") or {})}

请输出题目 JSON 数组。"""

    try:
        content = chat(user_prompt, system=_resolve_system_prompt(parse_mode))
        state["llm_json"] = _parse_llm_json(content)
    except Exception as exc:
        state["error"] = f"大模型解析失败: {exc}"
        state["llm_json"] = []
    return state


def _collect_item_images(
    row: dict[str, Any],
    parse_mode: str,
    image_registry: dict[int, dict[str, str]],
) -> list[dict[str, Any]] | None:
    found: list[dict[str, Any]] = []
    seen: set[int] = set()

    def collect_from_text(text: str, field: str, scope: str) -> None:
        for match in IMAGE_MARKER_RE.finditer(text):
            index = int(match.group(1))
            if index in seen or index not in image_registry:
                continue
            seen.add(index)
            reg = image_registry[index]
            found.append(
                {
                    "index": index,
                    "placeholder": reg["placeholder"],
                    "filePath": reg["file_path"],
                    "displayPath": reg["display_path"],
                    "scope": scope,
                    "position": field,
                }
            )

    for field in ("stemRaw", "answerRaw", "analysisRaw"):
        text = row.get(field)
        if not text:
            continue
        scope = "ANSWER" if field == "answerRaw" else "STEM"
        collect_from_text(str(text), field, scope)

    options = row.get("options")
    if isinstance(options, list):
        for index, option in enumerate(options):
            if option:
                collect_from_text(str(option), f"options[{index}]", "STEM")

    return found or None


def _build_document_images(image_registry: dict[int, dict[str, str]], parse_mode: str):
    from app.models.schemas import ParseWordImage

    images: list[ParseWordImage] = []
    for index in sorted(image_registry):
        reg = image_registry[index]
        images.append(
            ParseWordImage(
                index=index,
                placeholder=reg["placeholder"],
                file_path=reg["file_path"],
                display_path=reg["display_path"],
                scope=parse_mode,
                position="document",
            )
        )
    return images or None


def _match_llm_row_for_segment(
    segment: dict[str, str | None],
    seq_no: int,
    llm_rows: list[dict[str, Any]],
    used_indices: set[int],
) -> tuple[int | None, dict[str, Any]]:
    label = _normalize_question_label(segment.get("label"))
    if label:
        for index, row in enumerate(llm_rows):
            if index in used_indices:
                continue
            if _normalize_question_label(row.get("questionLabel")) == label:
                return index, row
    for index, row in enumerate(llm_rows):
        if index in used_indices:
            continue
        stem = str(row.get("stemRaw") or "")
        if label and label in stem:
            return index, row
    for index, row in enumerate(llm_rows):
        if index in used_indices:
            continue
        if int(row.get("seqNo") or 0) == seq_no:
            return index, row
    return None, {}


def _build_stem_items_from_segments(
    segments: list[dict[str, str | None]],
    llm_rows: list[dict[str, Any]],
    raw_text: str,
    image_registry: dict[int, dict[str, str]],
) -> list[ParseWordItem]:
    items: list[ParseWordItem] = []
    used_indices: set[int] = set()
    for seq_no, segment in enumerate(segments, start=1):
        index, row = _match_llm_row_for_segment(segment, seq_no, llm_rows, used_indices)
        if index is not None:
            used_indices.add(index)
        payload = dict(row) if row else {}
        payload["seqNo"] = seq_no
        if segment.get("label"):
            payload["questionLabel"] = segment["label"]
        payload["stemRaw"] = ""
        item = _to_parse_item(
            payload,
            seq_no,
            "STEM",
            image_registry,
            raw_text,
            segments,
            segment_method="word_split",
        )
        items.append(item)
    return items


def _build_answer_items_from_segments(
    segments: list[dict[str, str | None]],
    llm_rows: list[dict[str, Any]],
    raw_text: str,
    image_registry: dict[int, dict[str, str]],
) -> list[ParseWordItem]:
    items: list[ParseWordItem] = []
    used_indices: set[int] = set()
    for seq_no, segment in enumerate(segments, start=1):
        index, row = _match_llm_row_for_segment(segment, seq_no, llm_rows, used_indices)
        if index is not None:
            used_indices.add(index)
        payload = dict(row) if row else {}
        payload["seqNo"] = seq_no
        if segment.get("label"):
            payload["questionLabel"] = segment["label"]
        payload["stemRaw"] = ""
        payload["answerRaw"] = None
        item = _to_parse_item(
            payload,
            seq_no,
            "ANSWER",
            image_registry,
            raw_text,
            segments,
            segment_method="word_split",
        )
        items.append(item)
    return items


def _format_output_node(state: ParseState) -> ParseState:
    request = ParseWordRequest.model_validate(state["request"])
    parse_mode = _resolve_parse_mode(request)
    image_registry = state.get("image_registry") or {}
    raw_text = state.get("raw_text") or ""
    segments = _split_raw_into_questions(raw_text)
    llm_rows = state.get("llm_json") or []
    labeled_segments = [
        segment for segment in segments if _is_valid_question_label(segment.get("label"))
    ]
    items: list[ParseWordItem] = []
    if parse_mode == "STEM" and labeled_segments:
        items = _build_stem_items_from_segments(labeled_segments, llm_rows, raw_text, image_registry)
    elif parse_mode == "ANSWER" and labeled_segments:
        items = _build_answer_items_from_segments(labeled_segments, llm_rows, raw_text, image_registry)
    else:
        for idx, row in enumerate(llm_rows, start=1):
            item = _to_parse_item(row, idx, parse_mode, image_registry, raw_text, segments)
            items.append(item)
    state["items"] = items
    state["document_images"] = _build_document_images(image_registry, parse_mode)
    return state


def _is_escaped_quote(result: list[str]) -> bool:
    backslash_count = 0
    index = len(result) - 1
    while index >= 0 and result[index] == "\\":
        backslash_count += 1
        index -= 1
    return backslash_count % 2 == 1


def _repair_json_latex_in_strings(json_text: str) -> str:
    """在 JSON 字符串内修复 LaTeX 反斜杠。

    LLM 输出的 \\overline、\\frac、\\neq 等若只写单反斜杠，会导致：
    - 非法转义：\\o、\\c
    - 误判合法转义：\\f(form feed)、\\n、\\t、\\b、\\r 被当成 JSON 转义字符
    """
    result: list[str] = []
    in_string = False
    index = 0
    length = len(json_text)

    while index < length:
        char = json_text[index]

        if char == '"':
            if not in_string:
                in_string = True
                result.append(char)
                index += 1
                continue
            if _is_escaped_quote(result):
                result.append(char)
                index += 1
                continue
            in_string = False
            result.append(char)
            index += 1
            continue

        if in_string and char == "\\":
            if index + 1 >= length:
                result.append("\\\\")
                index += 1
                continue

            next_char = json_text[index + 1]
            if next_char in {'"', "\\", "/"}:
                result.append(char)
                result.append(next_char)
                index += 2
                continue

            if next_char == "u" and index + 5 < length:
                hex_part = json_text[index + 2 : index + 6]
                if all(ch in "0123456789abcdefABCDEF" for ch in hex_part):
                    result.append(json_text[index : index + 6])
                    index += 6
                    continue

            result.append("\\\\")
            index += 1
            continue

        result.append(char)
        index += 1

    return "".join(result)


def _repair_json_escapes_legacy(json_text: str) -> str:
    """兜底：修复字符串外的非法反斜杠。"""
    return re.sub(r'\\(?!["\\/bfnrtu])', r"\\\\", json_text)


def _load_json_array(json_text: str) -> list[dict[str, Any]]:
    errors: list[str] = []
    # 含 LaTeX 的 LLM 输出优先走字符串修复：\frac 的 \f、\neq 的 \n 等会被 raw 误判为合法转义
    repaired_latex = _repair_json_latex_in_strings(json_text)
    candidates = [
        ("latex_strings", repaired_latex),
        ("legacy_escapes", _repair_json_escapes_legacy(repaired_latex)),
        ("raw", json_text),
    ]

    for strategy, candidate in candidates:
        try:
            data = json.loads(candidate)
            if strategy != "raw":
                logger.warning("JSON 经 %s 修复后解析成功", strategy)
            if not isinstance(data, list):
                raise ValueError("模型返回不是 JSON 数组")
            return data
        except (json.JSONDecodeError, ValueError) as exc:
            errors.append(f"{strategy}: {exc}")

    snippet = json_text[:500].replace("\n", "\\n")
    logger.error("JSON 解析失败，片段: %s ...; 尝试: %s", snippet, "; ".join(errors))
    raise ValueError(f"模型返回 JSON 无法解析: {errors[-1].split(': ', 1)[-1]}")


def _parse_llm_json(content: str) -> list[dict[str, Any]]:
    text = content.strip()
    fence_match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
    if fence_match:
        text = fence_match.group(1).strip()

    start = text.find("[")
    end = text.rfind("]")
    if start == -1 or end == -1:
        raise ValueError("模型返回中未找到 JSON 数组")

    return _load_json_array(text[start : end + 1])


PLACEHOLDER_ANSWER_KEYWORDS = (
    "未给出具体",
    "未给出",
    "需依据",
    "需推导",
    "待补充",
    "无法确定",
    "需人工",
)


def _is_placeholder_answer(text: str | None) -> bool:
    if not text:
        return False
    normalized = text.strip()
    return any(keyword in normalized for keyword in PLACEHOLDER_ANSWER_KEYWORDS)


def _stem_looks_incomplete(stem_raw: str | None) -> bool:
    if not stem_raw:
        return True
    text = stem_raw.strip()
    if re.search(r"\(\d+\)", text) and "=" not in text and "\\overline" not in text and "[嵌入图片" not in text and "$" not in text:
        return True
    if "[嵌入图片" in text and "？" not in text and "?" not in text and "多少" not in text and "求" not in text:
        return True
    if IMAGE_MARKER_RE.search(text) and len(text) < 40:
        return True
    if re.search(r"(若|当|设|已知)", text):
        if not re.search(r"[？?]|多少|试求|求出|画出|设计|实现", text):
            return True
    if re.search(r"(\$[^$]+\$|\\overline\{)\s*$", text):
        if "？" not in text and "?" not in text and "多少" not in text:
            return True
    return False


def _to_parse_item(
    row: dict[str, Any],
    fallback_seq: int,
    parse_mode: str,
    image_registry: dict[int, dict[str, str]],
    raw_text: str = "",
    segments: list[dict[str, str | None]] | None = None,
    segment_method: str = "llm",
) -> ParseWordItem:
    confidence = row.get("confidenceScore")
    if confidence is not None:
        confidence = Decimal(str(confidence))

    answer_raw = row.get("answerRaw")
    stem_raw = normalize_latex_delimiters(str(row.get("stemRaw") or "").strip())
    stem_raw = _inline_image_markers(stem_raw) or ""
    if isinstance(answer_raw, str) and answer_raw.strip():
        answer_raw = _inline_image_markers(normalize_latex_delimiters(answer_raw.strip()))
    analysis_raw = row.get("analysisRaw")
    if isinstance(analysis_raw, str) and analysis_raw.strip():
        analysis_raw = normalize_latex_delimiters(analysis_raw.strip())
    question_label = row.get("questionLabel")
    warnings = list(row.get("warnings") or [])
    options = row.get("options")
    if isinstance(options, list):
        options = _normalize_options_list(list(options))
    else:
        options = None

    if parse_mode == "STEM":
        answer_raw = None
        reconciled_stem, raw_options = _reconcile_stem_from_raw(
            stem_raw,
            question_label,
            int(row.get("seqNo") or fallback_seq),
            raw_text,
            segments or _split_raw_into_questions(raw_text),
        )
        if reconciled_stem:
            if reconciled_stem != stem_raw:
                warnings.append("已用 Word 原文回填题干，请核对")
            stem_raw = reconciled_stem
        stem_raw = _clean_stem_garbage(stem_raw)
        if _stem_looks_merged(stem_raw, question_label):
            warnings.append("题干疑似合并了多道题，请人工核对")
            confidence = Decimal("0.40") if confidence is None else min(confidence, Decimal("0.40"))
        if raw_options:
            if _options_completeness(raw_options) >= _options_completeness(options):
                options = [
                    _inline_image_markers(option) or option
                    for option in raw_options
                ]
                warnings.append("已用 Word 原文回填选项，请核对")
        if not question_label and stem_raw:
            for pattern in (r"^(\d+-\d+)", r"^(\d+)[.、．]"):
                label_match = re.match(pattern, stem_raw)
                if label_match:
                    question_label = label_match.group(1)
                    break
        if _stem_looks_incomplete(stem_raw):
            warnings.append("题干可能缺少公式，请人工核对")
            confidence = Decimal("0.45") if confidence is None else min(confidence, Decimal("0.45"))
    elif parse_mode == "ANSWER":
        stem_raw = ""
        reconciled_answer = _reconcile_answer_from_raw(
            str(answer_raw) if answer_raw else None,
            question_label,
            int(row.get("seqNo") or fallback_seq),
            raw_text,
            segments or _split_raw_into_questions(raw_text),
        )
        if reconciled_answer:
            if answer_raw and reconciled_answer.strip() != str(answer_raw).strip():
                warnings.append("已用 Word 原文回填答案，请核对")
            answer_raw = reconciled_answer
        if _answer_looks_merged(str(answer_raw), question_label):
            warnings.append("答案疑似合并了多道题，请人工核对")
            confidence = Decimal("0.40") if confidence is None else min(confidence, Decimal("0.40"))
        if not answer_raw:
            warnings.append("未识别到答案")
            confidence = Decimal("0.50") if confidence is None else min(confidence, Decimal("0.50"))
        elif _is_placeholder_answer(str(answer_raw)):
            warnings.append("答案疑似占位说明而非实质内容")
            confidence = Decimal("0.35")
    elif parse_mode == "AUTO" and answer_raw:
        reconciled_answer = _reconcile_answer_from_raw(
            str(answer_raw),
            question_label,
            int(row.get("seqNo") or fallback_seq),
            raw_text,
            segments or _split_raw_into_questions(raw_text),
        )
        if reconciled_answer:
            if reconciled_answer.strip() != str(answer_raw).strip():
                warnings.append("已用 Word 原文回填答案，请核对")
            answer_raw = reconciled_answer
        if _answer_looks_merged(str(answer_raw), question_label):
            warnings.append("答案疑似合并了多道题，请人工核对")
            confidence = Decimal("0.40") if confidence is None else min(confidence, Decimal("0.40"))
    elif not answer_raw:
        warnings.append("未识别到答案，需人工确认")
        if confidence is None or confidence > Decimal("0.55"):
            confidence = Decimal("0.50")

    if answer_raw and _is_placeholder_answer(str(answer_raw)):
        warnings.append("答案疑似占位说明而非实质内容")
        confidence = Decimal("0.35")

    answer_source = "UNKNOWN"
    if answer_raw:
        answer_source = "ANSWER_SHEET" if parse_mode == "ANSWER" else "INLINE"

    from app.models.schemas import ParseWordImage

    row_for_images = dict(row)
    row_for_images["stemRaw"] = stem_raw
    if answer_raw:
        row_for_images["answerRaw"] = answer_raw
    if options:
        row_for_images["options"] = options
    raw_images = _collect_item_images(row_for_images, parse_mode, image_registry)
    images = [ParseWordImage.model_validate(img) for img in raw_images] if raw_images else None

    if options:
        options = [
            normalize_latex_delimiters(opt) if isinstance(opt, str) else opt
            for opt in options
        ]

    return ParseWordItem(
        seq_no=int(row.get("seqNo") or fallback_seq),
        question_label=question_label,
        stem_raw=stem_raw or None,
        answer_raw=answer_raw,
        analysis_raw=analysis_raw,
        options=options,
        question_type=row.get("questionType") or "UNKNOWN",
        difficulty=row.get("difficulty"),
        chapter=row.get("chapter"),
        knowledge_points=row.get("knowledgePoints"),
        confidence_score=confidence,
        warnings=warnings or None,
        images=images,
        agent_meta={
            "model": "doubao",
            "segmentMethod": segment_method,
            "parseMode": parse_mode,
            "answerSource": answer_source,
            "questionLabel": question_label,
        },
    )


def _build_summary(items: list[ParseWordItem]) -> ParseWordSummary:
    total = len(items)
    high = sum(
        1
        for item in items
        if item.confidence_score is not None and item.confidence_score >= Decimal("0.70")
    )
    needs_review = total - high
    return ParseWordSummary(
        total=total,
        high_confidence=high,
        needs_review=needs_review,
    )


def build_parse_graph():
    graph = StateGraph(ParseState)
    graph.add_node("extract_text", _extract_text_node)
    graph.add_node("llm_parse", _llm_parse_node)
    graph.add_node("format_output", _format_output_node)

    graph.set_entry_point("extract_text")
    graph.add_edge("extract_text", "llm_parse")
    graph.add_edge("llm_parse", "format_output")
    graph.add_edge("format_output", END)
    return graph.compile()


def _persist_batch_images(request: ParseWordRequest, image_registry: dict[int, dict[str, str]]) -> None:
    if not image_registry:
        return
    scope = _resolve_parse_mode(request)
    target_dir = get_upload_base_dir() / "images" / "batches" / request.batch_id / scope
    target_dir.mkdir(parents=True, exist_ok=True)
    for index in sorted(image_registry):
        reg = image_registry[index]
        src_candidates = [
            reg.get("display_path"),
            reg.get("file_path"),
        ]
        copied = False
        for candidate in src_candidates:
            if not candidate:
                continue
            src = Path(candidate)
            if not src.exists():
                continue
            try:
                if src.suffix.lower() == ".png":
                    shutil.copy2(src, target_dir / f"{index}.png")
                else:
                    display_png = ensure_display_png(str(src))
                    shutil.copy2(display_png, target_dir / f"{index}.png")
                copied = True
                break
            except Exception as exc:
                logger.warning("批次图片写入失败 index=%s src=%s: %s", index, src, exc)
        if not copied:
            logger.warning("批次图片未找到可用源文件: index=%s", index)


def run_parse_workflow(request: ParseWordRequest) -> ParseWordData:
    workflow = build_parse_graph()
    result = workflow.invoke(
        {
            "request": request.model_dump(by_alias=False),
            "raw_text": "",
            "image_registry": {},
            "llm_json": [],
            "items": [],
            "error": None,
        }
    )

    if result.get("error"):
        raise RuntimeError(result["error"])

    items: list[ParseWordItem] = result.get("items") or []
    if not items:
        raise RuntimeError("解析结果为空，未识别到任何题目")

    document_images = result.get("document_images")
    image_registry = result.get("image_registry") or {}
    _persist_batch_images(request, image_registry)
    return ParseWordData(
        batch_id=request.batch_id,
        items=items,
        document_images=document_images,
        parse_summary=_build_summary(items),
    )
