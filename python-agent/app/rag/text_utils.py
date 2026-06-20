import re


def strip_html(text: str | None) -> str:
    if not text:
        return ""
    cleaned = re.sub(r"<[^>]+>", " ", text)
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned.strip()


def build_embed_text(
    stem: str | None,
    subject: str | None = None,
    grade: str | None = None,
    chapter: str | None = None,
    knowledge_points: list[str] | None = None,
) -> str:
    parts: list[str] = []
    if subject:
        parts.append(f"课程:{subject}")
    stem_text = strip_html(stem)
    if stem_text:
        parts.append(stem_text)
    if grade:
        parts.append(grade)
    if chapter:
        parts.append(chapter)
    if knowledge_points:
        parts.extend(knowledge_points)
    return " ".join(p for p in parts if p).strip()
