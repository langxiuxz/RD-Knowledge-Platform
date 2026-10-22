import asyncio
from threading import Event
from unittest.mock import AsyncMock, Mock

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from pymilvus import DataType, MilvusClient

from app.api import rag
from app.core.config import Settings, get_settings
from app.core.exceptions import AIServiceError
from app.embeddings.local import DIMENSION, embed
from app.main import app
from app.rag.milvus_store import MilvusVectorStore
from app.rag.vector_store import InMemoryVectorStore, VectorRecord, create_vector_store


def record(project_id=1, kb_id=10, version_id=1000, content="owned", **metadata):
    return VectorRecord(embed(content), content, {
        "chunk_id": "shared-chunk", "project_id": project_id, "knowledge_base_id": kb_id,
        "document_id": 100, "document_version_id": version_id,
        "file_name": "笔记.txt", "page": 2, "chunk_index": 0, **metadata,
    })


@pytest.fixture
def backend(monkeypatch):
    settings = Settings(_env_file=None, vector_store_provider="milvus", milvus_timeout=2)
    client = Mock(spec=MilvusClient)
    client.has_collection.return_value = True
    types = {"id": DataType.VARCHAR, "chunk_id": DataType.VARCHAR,
             "file_name": DataType.VARCHAR, "content": DataType.VARCHAR,
             "embedding_model": DataType.VARCHAR, "embedding": DataType.FLOAT_VECTOR,
             "metadata": DataType.JSON}
    types.update({name: DataType.INT64 for name in
                  ("project_id", "knowledge_base_id", "document_id", "document_version_id", "page", "chunk_index")})
    client.describe_collection.return_value = {
        "auto_id": False,
        "fields": [{"name": name, "type": kind, "is_primary": name == "id",
                    "params": {"dim": DIMENSION} if name == "embedding" else {}}
                   for name, kind in types.items()],
    }
    client.describe_index.return_value = {"metric_type": "COSINE"}
    client.search.return_value = [[]]
    factory = Mock(return_value=client)
    factory.create_schema = MilvusClient.create_schema
    factory.prepare_index_params = MilvusClient.prepare_index_params
    monkeypatch.setattr("pymilvus.MilvusClient", factory)
    return MilvusVectorStore(settings), client, factory


async def test_creates_collection_once_and_reuses_it_on_reconnect(backend):
    store, client, factory = backend
    client.has_collection.return_value = False
    await store.initialize()
    schema = client.create_collection.call_args.kwargs["schema"].to_dict()
    fields = {field["name"]: field for field in schema["fields"]}
    assert fields["embedding"]["params"]["dim"] == DIMENSION
    assert fields["id"]["is_primary"]
    assert {"project_id", "knowledge_base_id", "document_id", "document_version_id", "metadata"} <= fields.keys()
    assert client.create_collection.call_args.kwargs["consistency_level"] == "Strong"
    await store.close()
    client.has_collection.return_value = True
    await store.initialize()
    assert factory.call_count == 2
    assert client.create_collection.call_count == 1
    client.drop_collection.assert_not_called()
    await store.close()


@pytest.mark.parametrize("mismatch", ["dimension", "field", "metric", "primary"])
async def test_incompatible_collection_fails_without_modifying_it(backend, mismatch):
    store, client, _ = backend
    fields = client.describe_collection.return_value["fields"]
    if mismatch == "dimension":
        next(f for f in fields if f["name"] == "embedding")["params"]["dim"] = 1536
    elif mismatch == "field":
        fields[:] = [f for f in fields if f["name"] != "project_id"]
    elif mismatch == "metric":
        client.describe_index.return_value = {"metric_type": "L2"}
    else:
        next(f for f in fields if f["name"] == "id")["is_primary"] = False
    with pytest.raises(AIServiceError, match="incompatible"):
        await store.initialize()
    client.upsert.assert_not_called()
    client.drop_collection.assert_not_called()
    client.close.assert_called_once()


async def test_upsert_uses_project_kb_version_scoped_keys_and_flushes_before_success(backend):
    store, client, _ = backend
    await store.upsert([record(), record(project_id=2), record(kb_id=11), record(version_id=1001)])
    rows = client.upsert.call_args.kwargs["data"]
    assert len({row["id"] for row in rows}) == 4
    assert rows[0]["metadata"]["chunk_id"] == "shared-chunk"
    assert rows[0]["embedding_model"] == "local-hash-v1"
    assert rows[0]["content"] == "owned"
    original_key = rows[0]["id"]
    await store.upsert([record(content="updated")])
    assert client.upsert.call_args.kwargs["data"][0]["id"] == original_key
    assert client.flush.call_count == 2
    assert client.upsert.call_args.kwargs["timeout"] == 2


@pytest.mark.parametrize("bad_record", [
    VectorRecord([1.0], "bad", record().metadata),
    VectorRecord([float("nan")] * DIMENSION, "bad", record().metadata),
    record(embedding_model="different-model"),
    record(project_id="1 or project_id > 0"),
    record(project_id=True),
    record(document_id=2**63),
    record(file_name="文" * 400),
    record(chunk_id=""),
    record(page=0),
    record(chunk_index=-1),
    record(content="a" * 65536),
])
async def test_invalid_records_do_not_partially_write(backend, bad_record):
    store, client, factory = backend
    with pytest.raises(ValueError):
        await store.upsert([record(), bad_record])
    factory.assert_not_called()
    client.upsert.assert_not_called()


async def test_search_preserves_scope_threshold_and_response_contract(backend):
    store, client, _ = backend
    entity = {"chunk_id": "shared-chunk", "document_id": 100, "file_name": "笔记.txt",
              "document_version_id": 1000, "page": 2, "content": "owned"}
    client.search.return_value = [[{"distance": 0.9, "entity": entity},
                                   {"distance": 0.3, "entity": entity}]]
    chunks = await store.retrieve(1, 10, 501, [1000], "owned", 5, 0.6)
    assert [chunk.model_dump(by_alias=True) for chunk in chunks] == [
        {"chunkId": "shared-chunk", "documentId": 100, "documentVersionId": 1000,
         "knowledgeSnapshotId": 501, "fileName": "笔记.txt",
         "page": 2, "content": "owned", "score": 0.9}]
    args = client.search.call_args.kwargs
    assert args["filter"] == "project_id == 1 and knowledge_base_id == 10 and document_version_id in [1000]"
    assert args["consistency_level"] == "Strong"
    assert args["search_params"]["metric_type"] == "COSINE"
    assert args["timeout"] == 2
    assert args["data"] == [embed("owned")]


async def test_deletes_always_scope_project_kb_and_document_version(backend):
    store, client, _ = backend
    for _ in range(2):
        await store.delete_document_version(1, 10, 1000)
        assert client.delete.call_args.kwargs["filter"] == (
            "project_id == 1 and knowledge_base_id == 10 and document_version_id == 1000")
    await store.delete_knowledge_base(1, 10)
    assert client.delete.call_args.kwargs["filter"] == "project_id == 1 and knowledge_base_id == 10"
    assert client.flush.call_count == 3


async def test_sdk_timeout_is_sanitized_and_does_not_return_empty_success(backend, caplog):
    store, client, _ = backend
    client.search.side_effect = TimeoutError("http://secret-user:secret-password@host")
    with pytest.raises(AIServiceError) as error:
        await store.retrieve(1, 10, 501, [1000], "query", 5)
    assert error.value.code == "AI_SERVICE_ERROR"
    assert "secret-password" not in str(error.value)
    assert "secret-password" not in caplog.text


async def test_sync_sdk_does_not_block_event_loop(backend):
    store, client, _ = backend
    entered, release = Event(), Event()

    def slow_search(**kwargs):
        entered.set()
        if not release.wait(timeout=3):
            raise TimeoutError("test failed to release SDK thread")
        return [[]]

    client.search.side_effect = slow_search
    task = asyncio.create_task(store.retrieve(1, 10, 501, [1000], "query", 5))
    try:
        # This coroutine must keep running while the SDK search is blocked.
        for _ in range(100):
            if entered.is_set():
                break
            await asyncio.sleep(0.01)
        assert entered.is_set()
        assert not task.done()
    finally:
        release.set()
        await task


async def test_connection_failure_never_falls_back_to_memory(backend):
    store, client, factory = backend
    factory.side_effect = ConnectionError("secret-token")
    with pytest.raises(AIServiceError):
        await store.initialize()
    with pytest.raises(AIServiceError):
        await store.upsert([record()])
    client.upsert.assert_not_called()
    assert store._client is None


def test_provider_selection_and_validation():
    assert isinstance(create_vector_store(Settings(_env_file=None, vector_store_provider="milvus")), MilvusVectorStore)
    for provider in ("none", "memory"):
        assert isinstance(create_vector_store(Settings(_env_file=None, vector_store_provider=provider)), InMemoryVectorStore)
    with pytest.raises(ValidationError):
        Settings(_env_file=None, vector_store_provider="milvuz")
    with pytest.raises(ValidationError):
        Settings(_env_file=None, milvus_collection="unsafe-name; drop collection")
    assert "private-secret" not in repr(Settings(_env_file=None, milvus_token="private-secret"))


def test_api_maps_milvus_failure_to_existing_error_contract(backend, monkeypatch, authenticated):
    store, client, _ = backend
    client.search.side_effect = TimeoutError("private-secret")
    monkeypatch.setattr(rag, "vector_store", store)
    response = TestClient(app).post("/internal/rag/retrieve", headers={"X-Internal-Token": "test-token"},
                                    json={"projectId": 1, "knowledgeBaseId": 10, "knowledgeSnapshotId": 501,
                                          "documentVersionIds": [1000], "query": "query"})
    assert response.status_code == 503
    assert response.json()["code"] == "AI_SERVICE_ERROR"
    assert "private-secret" not in response.text


@pytest.fixture
def authenticated(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    yield {"X-Internal-Token": "test-token"}
    get_settings.cache_clear()


@pytest.mark.parametrize("url", [
    "/internal/rag/document-versions/1000?projectId=1&knowledgeBaseId=10",
    "/internal/rag/knowledge-bases/10?projectId=1",
])
def test_delete_requires_internal_token(backend, monkeypatch, authenticated, url):
    store, client, _ = backend
    monkeypatch.setattr(rag, "vector_store", store)
    assert TestClient(app).delete(url).status_code == 401
    client.delete.assert_not_called()


@pytest.mark.parametrize("url", [
    "/internal/rag/document-versions/0?projectId=1&knowledgeBaseId=10",
    "/internal/rag/document-versions/1000?projectId=-1&knowledgeBaseId=10",
    "/internal/rag/document-versions/1000?projectId=1&knowledgeBaseId=0",
    f"/internal/rag/document-versions/{2**63}?projectId=1&knowledgeBaseId=10",
    "/internal/rag/document-versions/1000?projectId=1",
    "/internal/rag/knowledge-bases/-1?projectId=1",
    f"/internal/rag/knowledge-bases/10?projectId={2**63}",
])
def test_delete_rejects_invalid_ids_before_calling_milvus(backend, monkeypatch, authenticated, url):
    store, client, _ = backend
    monkeypatch.setattr(rag, "vector_store", store)
    response = TestClient(app).delete(url, headers=authenticated)
    assert response.status_code == 422
    client.delete.assert_not_called()


@pytest.mark.parametrize("failure", [None, "upsert", "flush"])
async def test_document_ready_requires_successful_persistent_write(backend, monkeypatch, failure):
    from app.schemas.rag import IndexRequest

    store, client, _ = backend
    bridge = Mock()
    bridge.download_file = AsyncMock(return_value=b"persistent document text")
    bridge.document_version_progress = AsyncMock()
    if failure == "flush":
        client.flush.side_effect = [None, TimeoutError("private-connection-secret")]
    elif failure:
        client.upsert.side_effect = TimeoutError("private-connection-secret")
    monkeypatch.setattr(rag, "_client", lambda: bridge)
    monkeypatch.setattr(rag, "vector_store", store)
    import hashlib
    data = b"persistent document text"
    bridge.download_file = AsyncMock(return_value=data)
    request = IndexRequest(projectId=1, knowledgeBaseId=10, documentId=100, documentVersionId=1000,
                           taskId="task-persist", fileUrl="http://spring/internal/files/note.txt",
                           fileType="txt", fileName="note.txt", contentHash=hashlib.sha256(data).hexdigest())

    await rag._process_document(request)

    callbacks = bridge.document_version_progress.call_args_list
    statuses = [call.args[2] for call in callbacks]
    if failure:
        assert statuses == ["PARSING", "CHUNKING", "EMBEDDING", "FAILED"]
        assert callbacks[-1].kwargs["error_message"]
        assert "private-connection-secret" not in callbacks[-1].kwargs["error_message"]
    else:
        assert statuses == ["PARSING", "CHUNKING", "EMBEDDING", "READY"]
        assert callbacks[-1].args[3:] == (100, 1)
        assert client.flush.call_count == 2  # Scoped replacement delete, then upsert.
        row = client.upsert.call_args.kwargs["data"][0]
        assert row["document_id"] == 100
        assert row["document_version_id"] == 1000
        assert row["embedding_model"] == "local-hash-v1"


def test_milvus_adapter_imports_independently_with_production_provider():
    import os
    from pathlib import Path
    import subprocess
    import sys

    script = ("from app.rag.milvus_store import MilvusVectorStore; "
              "from app.rag.vector_store import vector_store; "
              "assert isinstance(vector_store, MilvusVectorStore)")
    result = subprocess.run([sys.executable, "-c", script],
                            env=dict(os.environ, VECTOR_STORE_PROVIDER="milvus"),
                            cwd=Path(__file__).resolve().parents[1],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stderr


@pytest.mark.parametrize("fail_initialize", [False, True])
def test_lifespan_initializes_and_closes_store(monkeypatch, fail_initialize):
    from importlib import import_module

    main = import_module("app.main")
    store = Mock()
    store.initialize = AsyncMock(side_effect=AIServiceError("database unavailable") if fail_initialize else None)
    store.close = AsyncMock()
    monkeypatch.setattr(main, "vector_store", store)
    if fail_initialize:
        with pytest.raises(AIServiceError, match="database unavailable"):
            with TestClient(app):
                pytest.fail("Startup should reject an unavailable persistent backend")
    else:
        with TestClient(app):
            store.initialize.assert_awaited_once()
            store.close.assert_not_awaited()
    store.close.assert_awaited_once()
