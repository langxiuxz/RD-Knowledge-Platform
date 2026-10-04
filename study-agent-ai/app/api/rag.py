from fastapi import APIRouter, Depends

from app.core.security import require_internal_token
from app.schemas.rag import RetrieveRequest

router = APIRouter(prefix="/internal/rag", tags=["rag"])


@router.post("/retrieve", dependencies=[Depends(require_internal_token)])
async def retrieve(request: RetrieveRequest) -> dict:
    return {"code": 1, "msg": "success", "data": {"chunks": []}}
