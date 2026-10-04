from uuid import uuid4

from fastapi import APIRouter, Depends

from app.core.security import require_internal_token
from app.schemas.agent import AgentInvokeRequest, AgentInvokeResponse

router = APIRouter(prefix="/internal/agent", tags=["agent"])


@router.post("/invoke", dependencies=[Depends(require_internal_token)])
async def invoke(request: AgentInvokeRequest) -> dict:
    response = AgentInvokeResponse(answer="", citations=[], toolCalls=[], traceId=str(uuid4()))
    return {"code": 1, "msg": "success", "data": response.model_dump(by_alias=True)}
