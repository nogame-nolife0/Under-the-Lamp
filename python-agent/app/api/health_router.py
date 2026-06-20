from fastapi import APIRouter

from app.config import get_settings
from app.models.schemas import ApiResult, HealthData

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> ApiResult:
    settings = get_settings()
    return ApiResult(
        data=HealthData(
            version=settings.agent_version,
            llm_configured=bool(settings.doubao_api_key and settings.doubao_model),
            llm_model=settings.doubao_model or None,
        )
    )
