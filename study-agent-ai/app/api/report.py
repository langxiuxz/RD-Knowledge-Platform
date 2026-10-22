import json

from fastapi import APIRouter, Depends
from pydantic import ValidationError

from app.core.config import get_settings
from app.core.exceptions import AIServiceError
from app.core.security import require_internal_token
from app.llm.factory import LLMFactory, is_remote_provider_configured
from app.llm.structured import parse_structured_output
from app.schemas.report import CapabilitySummaryRequest, CapabilitySummaryResponse, ModelCapabilitySummary

router = APIRouter(prefix="/internal/reports", tags=["reports"], dependencies=[Depends(require_internal_token)])


@router.post("/capability-summary")
async def capability_summary(request: CapabilitySummaryRequest) -> dict:
    try:
        data = await _summarize(request)
    except (ValueError, ValidationError, json.JSONDecodeError) as exc:
        raise AIServiceError("能力报告摘要结构校验失败", "STRUCTURED_OUTPUT_INVALID") from exc
    return {"code": 1, "msg": "success", "data": data.model_dump(by_alias=True)}


async def _summarize(request: CapabilitySummaryRequest) -> CapabilitySummaryResponse:
    settings = get_settings()
    allowed = {source.document_version_id: source for source in request.allowed_sources}
    if not is_remote_provider_configured(settings):
        result = CapabilitySummaryResponse(
            summary="报告已依据固定评估事实生成；当前未配置 AI 摘要服务。",
            model_name="local-fallback",
        )
    else:
        prompt = (
            "根据提供的固定事实生成能力总结。不得推断或输出 PASS/NEEDS_WORK、分数、任务完成状态或新的事实。"
            "strengths/risks 只能解释事实。recommendedSources 只能选择 allowedSources 中的 documentVersionId。"
            "只返回 JSON，字段 summary、strengths、risks、recommendedSources。\n"
            f"Facts: {json.dumps(request.facts, ensure_ascii=False)}\n"
            f"Allowed sources: {json.dumps([s.model_dump(by_alias=True) for s in request.allowed_sources], ensure_ascii=False)}"
        )
        raw = await LLMFactory(settings).create().generate(prompt)
        model_result = parse_structured_output(ModelCapabilitySummary, raw)
        result = CapabilitySummaryResponse(
            summary=model_result.summary,
            strengths=model_result.strengths,
            risks=model_result.risks,
            recommendedSources=model_result.recommended_sources,
            model_name=settings.llm_model,
        )
    for source in result.recommended_sources:
        expected = allowed.get(source.document_version_id)
        if expected is None or expected.title != source.title:
            raise ValueError("recommended source is outside the supplied published source set")
    return result
