import pytest

from app.embeddings.local import embed
from app.rag.vector_store import InMemoryVectorStore, VectorRecord


@pytest.mark.asyncio
async def test_retrieve_requires_project_kb_and_snapshot_version_scope():
    store = InMemoryVectorStore()
    await store.upsert([
        VectorRecord(embed("spring dependency injection"), "project-a content", {
            "chunk_id": "a-1", "project_id": 1, "knowledge_base_id": 10,
            "document_version_id": 1000,
            "document_id": 100, "file_name": "a.txt", "page": 1, "chunk_index": 0,
        }),
        VectorRecord(embed("spring dependency injection"), "project-b content", {
            "chunk_id": "b-1", "project_id": 2, "knowledge_base_id": 10,
            "document_version_id": 1000,
            "document_id": 200, "file_name": "b.txt", "page": 1, "chunk_index": 0,
        }),
        VectorRecord(embed("spring dependency injection"), "other-kb content", {
            "chunk_id": "a-2", "project_id": 1, "knowledge_base_id": 11,
            "document_version_id": 1000,
            "document_id": 101, "file_name": "other.txt", "page": 1, "chunk_index": 0,
        }),
        VectorRecord(embed("spring dependency injection"), "unpublished version", {
            "chunk_id": "a-3", "project_id": 1, "knowledge_base_id": 10,
            "document_version_id": 1001,
            "document_id": 100, "file_name": "a.txt", "page": 2, "chunk_index": 1,
        }),
    ])

    result = await store.retrieve(1, 10, 501, [1000], "dependency injection", 10)

    assert [chunk.chunk_id for chunk in result] == ["a-1"]
    assert all(chunk.document_id == 100 for chunk in result)
    assert result[0].document_version_id == 1000
    assert result[0].knowledge_snapshot_id == 501
    assert await store.retrieve(1, 10, 502, [], "dependency injection", 10) == []


@pytest.mark.asyncio
async def test_delete_document_is_scoped_and_idempotent():
    store = InMemoryVectorStore()
    await store.upsert([VectorRecord(embed("same"), "owned", {
        "chunk_id": "owned", "project_id": 1, "knowledge_base_id": 10, "document_version_id": 1000,
        "document_id": 100, "file_name": "a.txt", "page": 1,
    }), VectorRecord(embed("same"), "foreign", {
        "chunk_id": "foreign", "project_id": 2, "knowledge_base_id": 10, "document_version_id": 1000,
        "document_id": 100, "file_name": "b.txt", "page": 1,
    })])

    await store.delete_document_version(1, 10, 1000)
    await store.delete_document_version(1, 10, 1000)

    result = await store.retrieve(2, 10, 600, [1000], "same", 10)
    assert [chunk.chunk_id for chunk in result] == ["foreign"]
