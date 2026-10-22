import json

from fastapi import APIRouter, Depends
from pydantic import ValidationError

from app.core.config import get_settings
from app.core.exceptions import AIServiceError
from app.core.security import require_internal_token
from app.llm.factory import LLMFactory, is_remote_provider_configured
from app.llm.structured import parse_structured_output
from app.schemas.drill import DrillNormalizeRequest, DrillNormalizeResponse

router = APIRouter(prefix="/internal/drills", tags=["drill"], dependencies=[Depends(require_internal_token)])


@router.post("/normalize-action")
async def normalize_action(request: DrillNormalizeRequest) -> dict:
    try:
        result = await _normalize_action(request)
    except (ValueError, ValidationError, json.JSONDecodeError) as exc:
        raise AIServiceError("演练动作归一化结果校验失败", "STRUCTURED_OUTPUT_INVALID") from exc
    return {"code": 1, "msg": "success", "data": result.model_dump(by_alias=True)}


async def _normalize_action(request: DrillNormalizeRequest) -> DrillNormalizeResponse:
    settings = get_settings()
    if not is_remote_provider_configured(settings):
        return DrillNormalizeResponse(
            normalizedAction=None,
            matchedRuleCode=None,
            confidence=None,
            explanation="当前未配置 AI 模型，动作未匹配到规则。",
        )

    candidates = [rule.model_dump(by_alias=True) for rule in request.candidate_rules]
    prompt = (
        "将用户动作匹配到且仅匹配到给定候选规则。不能创建规则、修改规则代码或输出分数。"
        "若无可靠匹配，matchedRuleCode 和 normalizedAction 均为 null，confidence 为 null。"
        "只返回 JSON，字段为 normalizedAction、matchedRuleCode、confidence、explanation。"
        "若命中，normalizedAction 必须与 matchedRuleCode 完全相同。\n"
        f"Action: {request.action_text}\nCandidates: {json.dumps(candidates, ensure_ascii=False)}"
    )
    result = parse_structured_output(
        DrillNormalizeResponse, await LLMFactory(settings).create().generate(prompt)
    )
    allowed = {rule.code for rule in request.candidate_rules}
    if result.matched_rule_code is not None and result.matched_rule_code not in allowed:
        raise ValueError("matched rule is outside the supplied candidate set")
    return result
