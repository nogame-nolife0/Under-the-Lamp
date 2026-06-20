from decimal import Decimal
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ApiResult(BaseModel):
    code: int = 200
    msg: str = "success"
    data: Any = None


class ParseWordHints(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    answer_section_keywords: list[str] = Field(
        default_factory=lambda: ["参考答案", "答案解析"],
        alias="answerSectionKeywords",
    )


class ParseWordRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    batch_id: str = Field(alias="batchId")
    file_path: str = Field(alias="filePath")
    subject: str | None = None
    grade: str | None = None
    parse_mode: str | None = Field(default="AUTO", alias="parseMode")
    hints: ParseWordHints | None = None


class ParseWordImage(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    index: int
    placeholder: str
    file_path: str = Field(alias="filePath")
    display_path: str | None = Field(default=None, alias="displayPath")
    scope: str | None = None
    position: str | None = None


class ParseWordItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    seq_no: int = Field(alias="seqNo")
    question_label: str | None = Field(default=None, alias="questionLabel")
    stem_raw: str | None = Field(default="", alias="stemRaw")
    answer_raw: str | None = Field(default=None, alias="answerRaw")
    analysis_raw: str | None = Field(default=None, alias="analysisRaw")
    options: list[str] | None = None
    question_type: str = Field(default="UNKNOWN", alias="questionType")
    difficulty: str | None = None
    chapter: str | None = None
    knowledge_points: list[str] | None = Field(default=None, alias="knowledgePoints")
    confidence_score: Decimal | None = Field(default=None, alias="confidenceScore")
    warnings: list[str] | None = None
    images: list[ParseWordImage] | None = None
    agent_meta: dict[str, Any] | None = Field(default=None, alias="agentMeta")


class ParseWordSummary(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    total: int = 0
    high_confidence: int = Field(default=0, alias="highConfidence")
    needs_review: int = Field(default=0, alias="needsReview")


class ParseWordData(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    batch_id: str = Field(alias="batchId")
    items: list[ParseWordItem] = Field(default_factory=list)
    document_images: list[ParseWordImage] | None = Field(default=None, alias="documentImages")
    parse_summary: ParseWordSummary = Field(
        default_factory=ParseWordSummary, alias="parseSummary"
    )


class HealthData(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str = "ok"
    version: str
    llm_configured: bool = Field(alias="llmConfigured")
    llm_model: str | None = Field(default=None, alias="llmModel")


class RagSearchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str
    subject: str | None = None
    grade: str | None = None
    limit: int | None = None


class RagSearchData(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    question_ids: list[int] = Field(default_factory=list, alias="questionIds")
    matched_count: int = Field(default=0, alias="matchedCount")
    compose_condition: dict[str, Any] = Field(default_factory=dict, alias="composeCondition")


class EmbedBatchItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    question_id: int = Field(alias="questionId")
    stem: str
    subject: str | None = None
    grade: str | None = None
    question_type: str | None = Field(default=None, alias="questionType")
    difficulty: str | None = None
    chapter: str | None = None
    knowledge_points: list[str] | None = Field(default=None, alias="knowledgePoints")


class EmbedBatchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    items: list[EmbedBatchItem] = Field(default_factory=list)


class EmbedBatchData(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    success_count: int = Field(default=0, alias="successCount")
    failed_ids: list[int] = Field(default_factory=list, alias="failedIds")
