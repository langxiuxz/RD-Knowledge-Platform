import json
from datetime import timedelta

from fastapi import APIRouter, Depends
from pydantic import ValidationError

from app.core.config import get_settings
from app.core.exceptions import AIServiceError
from app.core.security import require_internal_token
from app.llm.factory import LLMFactory
from app.llm.structured import parse_structured_output
from app.schemas.plan import GeneratedPlanTask, PlanGenerateRequest, PlanGenerateResponse

router = APIRouter(prefix="/internal/plans", tags=["plans"])


@router.post("/generate", dependencies=[Depends(require_internal_token)])
async def generate(request: PlanGenerateRequest) -> dict:
    try:
        result = await _generate(request)
        if any(task.date < request.start_date or task.date > request.end_date for task in result.tasks):
            raise ValueError("task date is outside plan range")
        if any(task.estimated_minutes > request.daily_minutes for task in result.tasks):
            raise ValueError("task exceeds daily minutes")
    except (ValueError, ValidationError, json.JSONDecodeError) as exc:
        raise AIServiceError("模型结构化输出校验失败", "STRUCTURED_OUTPUT_INVALID") from exc
    return {"code": 1, "msg": "success", "data": result.model_dump(by_alias=True, mode="json")}


async def _generate(request: PlanGenerateRequest) -> PlanGenerateResponse:
    settings = get_settings()
    if settings.llm_provider.lower() not in {"openai", "openai-compatible"} or not settings.llm_api_key:
        topics = request.profile.weak_topics or ["综合复习"]
        days = (request.end_date - request.start_date).days + 1
        tasks = [GeneratedPlanTask(date=request.start_date + timedelta(days=index),
                                   topic=topics[index % len(topics)], type="REVIEW",
                                   estimatedMinutes=request.daily_minutes)
                 for index in range(days)]
        return PlanGenerateResponse(title=request.goal[:128], tasks=tasks)
    prompt = (
        "Return JSON only with shape {\"title\":string,\"tasks\":[{\"date\":YYYY-MM-DD,"
        "\"topic\":string,\"type\":READ|QUIZ|REVIEW|CHAT,\"estimatedMinutes\":integer}]}. "
        f"Goal: {request.goal}; range: {request.start_date} to {request.end_date}; "
        f"daily limit: {request.daily_minutes}; profile: {request.profile.model_dump(by_alias=True)}"
    )
    return parse_structured_output(PlanGenerateResponse,
                                   await LLMFactory(settings).create().generate(prompt))

