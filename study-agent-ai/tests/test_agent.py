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
        "projectId": None,
        "knowledgeSnapshotId": None,
        "allowedDocumentVersionIds": [],
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
        VectorRecord(embed("dependency injection"), "project context", {
            "chunk_id": "owned", "project_id": 11, "knowledge_base_id": 10,
            "document_version_id": 1000,
            "document_id": 100, "file_name": "owned.pdf", "page": 12, "chunk_index": 0,
        }),
        VectorRecord(embed("dependency injection"), "foreign project context", {
            "chunk_id": "foreign", "project_id": 12, "knowledge_base_id": 10,
            "document_version_id": 1000,
            "document_id": 200, "file_name": "foreign.pdf", "page": 3, "chunk_index": 0,
        }),
        VectorRecord(embed("dependency injection"), "unpublished context", {
            "chunk_id": "draft", "project_id": 11, "knowledge_base_id": 10,
            "document_version_id": 1001,
            "document_id": 100, "file_name": "draft.pdf", "page": 4, "chunk_index": 0,
        }),
    ]))

    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("RAG", knowledgeBaseId=10, projectId=11, knowledgeSnapshotId=501,
                      allowedDocumentVersionIds=[1000])
    )

    assert response.status_code == 200
    citations = response.json()["data"]["citations"]
    assert [item["chunkId"] for item in citations] == ["owned"]
    assert citations[0]["documentVersionId"] == 1000
    assert citations[0]["knowledgeSnapshotId"] == 501
    assert "foreign context" not in response.json()["data"]["answer"]


def _tool_call(call_id, name, arguments):
    import json
    return {"id": call_id, "type": "function", "function": {
        "name": name, "arguments": json.dumps(arguments, ensure_ascii=False)}}


class FakeRegistry:
    def __init__(self, handler):
        self.handler = handler

    def schemas(self, include_knowledge_search=False):
        return []

    async def execute(self, name, user_id, arguments):
        return await self.handler(name, user_id, arguments)


class SequenceLLM:
    def __init__(self, *responses):
        self.responses = list(responses)

    async def generate(self, prompt):
        return "generated"

    async def chat(self, messages, tools):
        return self.responses.pop(0)


def test_agent_autonomously_calls_multiple_tools_then_answers(monkeypatch):
    async def execute(name, user_id, arguments):
        assert user_id == 1
        if name == "get_topic_progress":
            return [{"topic": "Spring", "mastery": 0.4}]
        assert name == "get_wrong_questions"
        return [{"topic": "Spring", "content": "AOP proxy boundaries"}]

    monkeypatch.setattr("app.graph.builder.create_default_registry", lambda settings: FakeRegistry(execute))
    llm = SequenceLLM(
        {"content": None, "tool_calls": [_tool_call("1", "get_topic_progress", {"topic": "Spring"})]},
        {"content": None, "tool_calls": [_tool_call("2", "get_wrong_questions", {"topic": "Spring", "limit": 5})]},
        {"content": "Spring mastery is low; mistakes focus on AOP proxy boundaries.", "tool_calls": []},
    )
    monkeypatch.setattr("app.graph.builder.LLMFactory.create", lambda self: llm)
    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("AGENT", content="My Spring weak points")
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert [call["name"] for call in data["toolCalls"]] == ["get_topic_progress", "get_wrong_questions"]
    assert "AOP proxy boundaries" in data["answer"]


def test_general_question_does_not_force_tool_use(monkeypatch):
    async def execute(name, user_id, arguments):
        raise AssertionError("No tool should be called for a general knowledge question")

    monkeypatch.setattr("app.graph.builder.create_default_registry", lambda settings: FakeRegistry(execute))
    monkeypatch.setattr("app.graph.builder.LLMFactory.create", lambda self: SequenceLLM(
        {"content": "Polymorphism lets implementations share a common interface.", "tool_calls": []}
    ))
    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("AGENT", content="What is Java polymorphism?")
    )
    assert response.status_code == 200
    assert response.json()["data"]["toolCalls"] == []


def test_agent_tool_loop_stops_at_configured_step_limit(monkeypatch):
    from app.core.config import Settings

    async def execute(name, user_id, arguments):
        return [{"topic": "Spring"}]

    monkeypatch.setattr("app.graph.builder.create_default_registry", lambda settings: FakeRegistry(execute))
    llm = SequenceLLM(
        {"content": None, "tool_calls": [_tool_call("1", "get_learning_progress", {"limit": 5})]},
        {"content": None, "tool_calls": [_tool_call("2", "get_learning_progress", {"limit": 5})]},
    )
    monkeypatch.setattr("app.graph.builder.LLMFactory.create", lambda self: llm)
    monkeypatch.setattr("app.api.agent.get_settings", lambda: Settings(max_agent_steps=2))
    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("AGENT", content="Review my learning progress")
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert len(data["toolCalls"]) == 1
    assert data["answer"]


def test_agent_with_knowledge_base_can_autonomously_call_rag(monkeypatch):
    async def execute(name, user_id, arguments):
        return [{"topic": "Spring", "mastery": 0.4}]

    vector_store.clear()
    asyncio.run(vector_store.upsert([
        VectorRecord(embed("AOP"), "owned AOP notes", {
            "chunk_id": "combined", "project_id": 11, "knowledge_base_id": 10,
            "document_version_id": 1000,
            "document_id": 101, "file_name": "notes.pdf", "page": 5, "chunk_index": 0,
        })
    ]))
    monkeypatch.setattr("app.graph.builder.create_default_registry", lambda settings: FakeRegistry(execute))
    llm = SequenceLLM(
        {"content": None, "tool_calls": [_tool_call("1", "search_knowledge_base", {"query": "AOP"})]},
        {"content": "The notes explain AOP through proxies.", "tool_calls": []},
    )
    monkeypatch.setattr("app.graph.builder.LLMFactory.create", lambda self: llm)
    response = TestClient(app).post(
        "/internal/agent/invoke", headers=_headers(monkeypatch),
        json=_request("AGENT", content="Explain AOP using my notes", knowledgeBaseId=10,
                      projectId=11, knowledgeSnapshotId=501, allowedDocumentVersionIds=[1000])
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["citations"][0]["chunkId"] == "combined"
    assert data["citations"][0]["documentVersionId"] == 1000
    assert data["citations"][0]["knowledgeSnapshotId"] == 501
    assert "The notes explain AOP" in data["answer"]


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

