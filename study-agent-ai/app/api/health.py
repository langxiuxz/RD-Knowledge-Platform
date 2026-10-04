from fastapi import APIRouter, Depends

from app.core.security import require_internal_token

router = APIRouter(prefix="/internal", tags=["health"])


@router.get("/health", dependencies=[Depends(require_internal_token)])
async def health() -> dict:
    return {"status": "UP", "service": "study-agent-ai"}
