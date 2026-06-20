import logging
import traceback

from fastapi import APIRouter, Body

from app.models.schemas import (
    ApiResult,
    EmbedBatchData,
    EmbedBatchRequest,
    RagSearchData,
    RagSearchRequest,
)
from app.rag.service import embed_batch, rag_search

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1", tags=["rag"])


@router.post("/rag/search")
def search_questions(request: RagSearchRequest = Body(...)) -> ApiResult:
    try:
        data = rag_search(
            query=request.query,
            subject=request.subject,
            grade=request.grade,
            limit=request.limit,
        )
        return ApiResult(code=200, msg="success", data=RagSearchData.model_validate(data))
    except ValueError as exc:
        return ApiResult(code=400, msg=str(exc), data=None)
    except Exception as exc:
        logger.error("RAG 检索失败: %s\n%s", exc, traceback.format_exc())
        return ApiResult(code=50002, msg=f"RAG 检索失败: {exc}", data=None)


@router.post("/embed/batch")
def batch_embed(request: EmbedBatchRequest = Body(...)) -> ApiResult:
    try:
        payload = [item.model_dump(by_alias=True) for item in request.items]
        data = embed_batch(payload)
        return ApiResult(code=200, msg="success", data=EmbedBatchData.model_validate(data))
    except ValueError as exc:
        return ApiResult(code=400, msg=str(exc), data=None)
    except Exception as exc:
        logger.error("向量入库失败: %s\n%s", exc, traceback.format_exc())
        return ApiResult(code=50002, msg=f"向量入库失败: {exc}", data=None)
