import json
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import ValidationError

from app.clients import spring_boot
from app.clients.spring_boot import SpringBootClient
from app.core.config import Settings


def client():
    return SpringBootClient(Settings(_env_file=None, internal_token="test-token",
                                     document_callback_attempts=3, document_callback_retry_delay=0.25))


def status_error(status):
    request = httpx.Request("POST", "http://spring/internal/callbacks/documents/1/progress")
    return httpx.HTTPStatusError("callback failed", request=request, response=httpx.Response(status, request=request))


@pytest.mark.parametrize("failure", [httpx.ConnectError("offline"), httpx.ReadTimeout("timeout"),
                                     status_error(408), status_error(429), status_error(503)])
async def test_progress_retries_transient_failure_with_identical_payload(monkeypatch, failure):
    bridge = client()
    request = AsyncMock(side_effect=[failure, failure, {"code": 1}])
    monkeypatch.setattr(bridge, "_request_json", request)
    sleep = AsyncMock()
    monkeypatch.setattr(spring_boot.asyncio, "sleep", sleep)

    await bridge.document_progress(1, "task-1", "READY", 100, 3)

    assert request.await_count == 3
    assert request.await_args_list[0] == request.await_args_list[1] == request.await_args_list[2]
    assert [call.args[0] for call in sleep.await_args_list] == [0.25, 0.5]


@pytest.mark.parametrize("status", [400, 401, 403, 404, 409, 422])
async def test_progress_does_not_retry_permanent_failure(monkeypatch, status):
    bridge = client()
    request = AsyncMock(side_effect=status_error(status))
    monkeypatch.setattr(bridge, "_request_json", request)
    with pytest.raises(httpx.HTTPStatusError):
        await bridge.document_progress(1, "task-1", "PARSING", 10)
    assert request.await_count == 1


async def test_progress_stops_after_maximum_deliveries(monkeypatch):
    bridge = client()
    request = AsyncMock(side_effect=httpx.ReadTimeout("offline"))
    monkeypatch.setattr(bridge, "_request_json", request)
    monkeypatch.setattr(spring_boot.asyncio, "sleep", AsyncMock())
    with pytest.raises(httpx.ReadTimeout):
        await bridge.document_progress(1, "task-1", "FAILED", 0, error_message="indexing failed")
    assert request.await_count == 3


async def test_progress_requires_business_acknowledgement(monkeypatch):
    bridge = client()
    request = AsyncMock(return_value={"code": 0})
    monkeypatch.setattr(bridge, "_request_json", request)
    with pytest.raises(ValueError, match="not acknowledged"):
        await bridge.document_progress(1, "task-1", "READY", 100)
    assert request.await_count == 1


async def test_progress_transport_sends_authenticated_unchanged_contract(monkeypatch):
    bridge = client()
    received = []

    def handle(request):
        received.append(request)
        return httpx.Response(503 if len(received) == 1 else 200, json={"code": 1})

    original_client = httpx.AsyncClient
    monkeypatch.setattr(spring_boot.httpx, "AsyncClient", lambda **kwargs: original_client(
        transport=httpx.MockTransport(handle), **kwargs))
    monkeypatch.setattr(spring_boot.asyncio, "sleep", AsyncMock())
    await bridge.document_progress(1, "task-1", "READY", 100, 3)

    assert len(received) == 2
    assert received[0].headers["X-Internal-Token"] == "test-token"
    assert json.loads(received[0].content) == json.loads(received[1].content) == {
        "taskId": "task-1", "status": "READY", "progress": 100, "chunkCount": 3, "errorMessage": None}


@pytest.mark.parametrize("overrides", [{"document_callback_attempts": 0}, {"document_callback_attempts": 6},
                                       {"document_callback_retry_delay": 0}])
def test_callback_retry_configuration_rejects_invalid_values(overrides):
    with pytest.raises(ValidationError):
        Settings(_env_file=None, **overrides)
