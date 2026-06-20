VALID_QUESTION_TYPES = {
    "SINGLE_CHOICE",
    "MULTI_CHOICE",
    "TRUE_FALSE",
    "FILL_BLANK",
    "SHORT_ANSWER",
    "CALCULATION",
    "ESSAY",
}

TYPE_ALIASES = {
    "单选题": "SINGLE_CHOICE",
    "单选": "SINGLE_CHOICE",
    "多选题": "MULTI_CHOICE",
    "多选": "MULTI_CHOICE",
    "判断题": "TRUE_FALSE",
    "判断": "TRUE_FALSE",
    "填空题": "FILL_BLANK",
    "填空": "FILL_BLANK",
    "简答题": "SHORT_ANSWER",
    "简答": "SHORT_ANSWER",
    "计算题": "CALCULATION",
    "计算": "CALCULATION",
    "论述题": "ESSAY",
    "论述": "ESSAY",
}


def normalize_question_type(value: str | None) -> str | None:
    if not value:
        return None
    text = str(value).strip().upper()
    if text in VALID_QUESTION_TYPES:
        return text
    return TYPE_ALIASES.get(str(value).strip())


def normalize_type_counts(raw: object) -> dict[str, int] | None:
    if not isinstance(raw, dict) or not raw:
        return None
    normalized: dict[str, int] = {}
    for key, value in raw.items():
        question_type = normalize_question_type(str(key))
        if not question_type:
            continue
        try:
            count = int(value)
        except (TypeError, ValueError):
            continue
        if count <= 0:
            continue
        normalized[question_type] = normalized.get(question_type, 0) + count
    return normalized or None


def dedupe_by_chapter(
    candidates: list[tuple[int, dict]],
    limit: int,
    max_per_chapter: int = 1,
    exclude_ids: set[int] | None = None,
) -> list[int]:
    exclude_ids = exclude_ids or set()
    chapter_count: dict[str, int] = {}
    selected: list[int] = []
    for question_id, metadata in candidates:
        if question_id in exclude_ids or question_id in selected:
            continue
        chapter = str(metadata.get("chapter") or "").strip()
        chapter_key = chapter if chapter else f"__none__{question_id}"
        used = chapter_count.get(chapter_key, 0)
        if used >= max_per_chapter:
            continue
        chapter_count[chapter_key] = used + 1
        selected.append(question_id)
        if len(selected) >= limit:
            break
    return selected
