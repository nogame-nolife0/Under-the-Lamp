import logging
import traceback

from fastapi import APIRouter, Body
from openai import APIConnectionError, APITimeoutError, RateLimitError

from app.graph_workflow import run_parse_workflow
from app.models.schemas import ApiResult, ParseWordRequest

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1", tags=["parse"])


@router.post("/parse-word")
def parse_word(request: ParseWordRequest = Body(...)) -> ApiResult:
    try:
        logger.info("开始解析 Word: %s, mode=%s", request.file_path, request.parse_mode)
        data = run_parse_workflow(request)
        logger.info("解析完成: %s 题", len(data.items))
        return ApiResult(code=200, msg="success", data=data)
    except FileNotFoundError as exc:
        return ApiResult(code=40001, msg=str(exc), data=None)
    except ValueError as exc:
        return ApiResult(code=40001, msg=str(exc), data=None)
    except RuntimeError as exc:
        msg = str(exc)
        if "为空" in msg:
            return ApiResult(code=40003, msg=msg, data=None)
        return ApiResult(code=50002, msg=msg, data=None)
    except APITimeoutError as exc:
        logger.error("豆包 API 超时: %s", exc)
        return ApiResult(code=50001, msg=f"豆包 API 调用超时，图片较多时请稍后重试: {exc}", data=None)
    except APIConnectionError as exc:
        logger.error("豆包 API 连接失败: %s", exc)
        return ApiResult(code=50001, msg=f"无法连接豆包 API，请检查网络与 DOUBAO_API_KEY: {exc}", data=None)
    except RateLimitError as exc:
        logger.error("豆包 API 限流: %s", exc)
        return ApiResult(code=50003, msg=f"豆包 API 限流，请稍后重试: {exc}", data=None)
    except Exception as exc:
        logger.error("解析异常: %s\n%s", exc, traceback.format_exc())
        return ApiResult(code=50003, msg=f"解析失败: {exc}", data=None)
