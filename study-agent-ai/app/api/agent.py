import asyncio
import json
from typing import AsyncIterator

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from app.core.config import get_settings
from app.core.security import require_internal_token
from app.graph.builder import AgentWorkflow
from app.schemas.agent import AgentInvokeRequest, AgentInvokeResponse

router = APIRouter(prefix="/internal/agent", tags=["agent"])


def _workflow() -> AgentWorkflow:
    return AgentWorkflow(get_settings())


@router.post("/invoke", dependencies=[Depends(require_internal_token)])
async def invoke(request: AgentInvokeRequest) -> dict:
    state = await _workflow().invoke(request)
    response = AgentInvokeResponse(
        answer=state["answer"], citations=state.get("citations", []),
        toolCalls=state.get("tool_calls", []), traceId=state["trace_id"],
    )
    return {"code": 1, "msg": "success", "data": response.model_dump(by_alias=True)}


@router.post("/stream", dependencies=[Depends(require_internal_token)])
async def stream(request: AgentInvokeRequest, http_request: Request) -> StreamingResponse:
    async def events() -> AsyncIterator[str]:
        workflow = _workflow()
        started = False
        final_state = None
        try:
            async for node, state in workflow.stream(request):
                if not started:
                    started = True
                    yield _event("start", {"traceId": state["trace_id"]})
                if await http_request.is_disconnected():
                    return
                yield _event("node", {"node": node, "status": "completed"})
                if node == "tool_node":
                    for call in state.get("tool_calls", []):
                        yield _event("tool", {"tool": call["name"], "status": call["status"]})
                final_state = state
            if final_state is None:
                raise RuntimeError("Agent graph returned no state")
            for citation in final_state.get("citations", []):
                yield _event("citation", citation)
            for token in _token_chunks(final_state["answer"]):
                if await http_request.is_disconnected():
                    return
                yield _event("token", {"content": token})
                await asyncio.sleep(0)
            yield _event("done", {"traceId": final_state["trace_id"], "finishReason": "stop"})
        except Exception:
            yield _event("error", {"code": 3001, "message": "AI服务不可用"})

    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})


def _event(name: str, data: object) -> str:
    return f"event: {name}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def _token_chunks(text: str, size: int = 12):
    for index in range(0, len(text), size):
        yield text[index:index + size]
