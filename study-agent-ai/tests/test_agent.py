import asyncio

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.embeddings.local import embed
from app.main import app
from app.rag.vector_store import VectorRecord, vector_store
from app.tools.registry import ToolRegistry


def _headers(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    monkeypatch.setenv("LLM_PROVIDER", "none")
    get_settings.cache_clear()
    return {"X-Internal-Token": "test-token"}


def _request(mode, **overrides):
    body = {
        "userId": 1,
        "conversationId": 301,
        "messageId": 9000,
        "content": "Explain dependency injection",
        "mode": mode,
        "knowledgeBaseId": None,
        "stream": False,
    }
    body.update(overrides)
    return body


def test_chat_invoke_returns_answer(monkeypatch):
    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch), json=_request("CHAT")
    )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["answer"]
    assert data["citations"] == []
    assert data["toolCalls"] == []
    assert data["traceId"].startswith("trace_")


def test_rag_invoke_returns_only_owned_citation(monkeypatch):
    vector_store.clear()
    asyncio.run(vector_store.upsert([
        VectorRecord(embed("dependency injection"), "owned context", {
            "chunk_id": "owned", "user_id": 1, "knowledge_base_id": 10,
            "document_id": 100, "file_name": "owned.pdf", "page": 12, "chunk_index": 0,
        }),
        VectorRecord(embed("dependency injection"), "foreign context", {
            "chunk_id": "foreign", "user_id": 2, "knowledge_base_id": 10,
            "document_id": 200, "file_name": "foreign.pdf", "page": 3, "chunk_index": 0,
        }),
    ]))

    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("RAG", knowledgeBaseId=10)
    )

    assert response.status_code == 200
    citations = response.json()["data"]["citations"]
    assert [item["chunkId"] for item in citations] == ["owned"]
    assert "foreign context" not in response.json()["data"]["answer"]


def test_agent_invokes_whitelisted_tool(monkeypatch):
    class FakeRegistry:
        async def execute(self, name, user_id, arguments):
            assert user_id == 1
            assert name == "get_wrong_questions"
            return [{"questionId": 9}]

    monkeypatch.setattr("app.graph.builder.create_default_registry", lambda settings: FakeRegistry())
    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("AGENT", content="分析我的错题")
    )

    assert response.status_code == 200
    calls = response.json()["data"]["toolCalls"]
    assert calls == [{"name": "get_wrong_questions", "status": "success",
                      "result": [{"questionId": 9}], "error": None}]


def test_agent_with_knowledge_base_retrieves_and_invokes_tool(monkeypatch):
    class FakeRegistry:
        async def execute(self, name, user_id, arguments):
            return [{"topic": "dependency injection", "mastery": 0.4}]

    vector_store.clear()
    asyncio.run(vector_store.upsert([
        VectorRecord(embed("dependency injection progress"), "owned combined context", {
            "chunk_id": "combined", "user_id": 1, "knowledge_base_id": 10,
            "document_id": 101, "file_name": "combined.pdf", "page": 5, "chunk_index": 0,
        })
    ]))
    monkeypatch.setattr("app.graph.builder.create_default_registry", lambda settings: FakeRegistry())

    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("AGENT", content="分析我的学习进度", knowledgeBaseId=10)
    )

    assert response.status_code == 200
    data = response.json()["data"]
    assert [item["chunkId"] for item in data["citations"]] == ["combined"]
    assert data["toolCalls"][0]["name"] == "get_learning_progress"
    assert "owned combined context" in data["answer"]


def test_stream_emits_start_token_and_done(monkeypatch):
    with TestClient(app).stream(
        "POST", "/internal/agent/stream", headers=_headers(monkeypatch),
        json=_request("CHAT", stream=True)
    ) as response:
        body = "".join(response.iter_text())

    assert response.status_code == 200
    assert "event: start" in body
    assert "event: token" in body
    assert "event: done" in body


@pytest.mark.asyncio
async def test_tool_registry_rejects_non_whitelisted_tool():
    registry = ToolRegistry()
    with pytest.raises(ValueError, match="not whitelisted"):
        registry.register("run_sql", lambda user_id, arguments: None)

