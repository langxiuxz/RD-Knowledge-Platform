from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app
from app.rag.vector_store import vector_store, VectorRecord
from app.embeddings.local import embed
from app.api import rag
from app.schemas.rag import IndexRequest


def test_retrieve_requires_internal_token(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    response = TestClient(app).post("/internal/rag/retrieve", json={
        "projectId": 1, "knowledgeBaseId": 10, "knowledgeSnapshotId": 501,
        "documentVersionIds": [1000], "query": "hello"
    })
    assert response.status_code == 401


def test_retrieve_returns_contract_and_isolation(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    vector_store.clear()
    import asyncio
    asyncio.run(vector_store.upsert([VectorRecord(embed("hello world"), "private text", {
        "chunk_id": "chunk-1", "project_id": 1, "knowledge_base_id": 10, "document_version_id": 1000,
        "document_id": 100, "file_name": "note.txt", "page": 2, "chunk_index": 0,
    }), VectorRecord(embed("hello world"), "foreign text", {
        "chunk_id": "chunk-2", "project_id": 2, "knowledge_base_id": 10, "document_version_id": 1000,
        "document_id": 200, "file_name": "foreign.txt", "page": 1, "chunk_index": 0,
    }), VectorRecord(embed("hello world"), "unpublished text", {
        "chunk_id": "chunk-3", "project_id": 1, "knowledge_base_id": 10, "document_version_id": 1001,
        "document_id": 100, "file_name": "draft.txt", "page": 1, "chunk_index": 0,
    })]))

    response = TestClient(app).post("/internal/rag/retrieve", headers={"X-Internal-Token": "test-token"}, json={
        "projectId": 1, "knowledgeBaseId": 10, "knowledgeSnapshotId": 501,
        "documentVersionIds": [1000], "query": "hello", "topK": 5
    })

    assert response.status_code == 200
    chunks = response.json()["data"]["chunks"]
    assert chunks == [{"chunkId": "chunk-1", "documentId": 100, "fileName": "note.txt",
                       "documentVersionId": 1000, "knowledgeSnapshotId": 501,
                       "page": 2, "content": "private text", "score": chunks[0]["score"]}]


def test_retrieve_with_empty_version_allowlist_returns_no_chunks(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    response = TestClient(app).post("/internal/rag/retrieve", headers={"X-Internal-Token": "test-token"}, json={
        "projectId": 1, "knowledgeBaseId": 10, "knowledgeSnapshotId": 501,
        "documentVersionIds": [], "query": "hello", "topK": 5
    })
    assert response.status_code == 200
    assert response.json()["data"]["chunks"] == []


async def test_empty_document_reports_failed(monkeypatch):
    class FakeSpringBootClient:
        def __init__(self):
            self.callbacks = []

        async def download_file(self, file_url):
            return b"   \n\t"

        async def document_version_progress(self, document_version_id, task_id, status, progress,
                                            chunk_count=None, error_message=None):
            self.callbacks.append((status, progress, chunk_count, error_message))

    client = FakeSpringBootClient()
    monkeypatch.setattr(rag, "_client", lambda: client)
    import hashlib
    request = IndexRequest(projectId=1, knowledgeBaseId=10, documentId=100, documentVersionId=1000,
                           taskId="task-empty", fileUrl="http://spring/internal/files/empty.txt",
                           fileType="txt", fileName="empty.txt",
                           contentHash=hashlib.sha256(b"   \n\t").hexdigest())

    await rag._process_document(request)

    assert [item[0] for item in client.callbacks] == ["PARSING", "CHUNKING", "FAILED"]
    assert "no extractable text" in client.callbacks[-1][3]
