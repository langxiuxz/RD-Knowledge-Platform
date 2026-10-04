import pytest

from app.embeddings.local import embed
from app.rag.vector_store import InMemoryVectorStore, VectorRecord


@pytest.mark.asyncio
async def test_retrieve_requires_user_and_knowledge_base_scope():
    store = InMemoryVectorStore()
    await store.upsert([
        VectorRecord(embed("spring dependency injection"), "user-a content", {
            "chunk_id": "a-1", "user_id": 1, "knowledge_base_id": 10,
            "document_id": 100, "file_name": "a.txt", "page": 1, "chunk_index": 0,
        }),
        VectorRecord(embed("spring dependency injection"), "user-b content", {
            "chunk_id": "b-1", "user_id": 2, "knowledge_base_id": 20,
            "document_id": 200, "file_name": "b.txt", "page": 1, "chunk_index": 0,
        }),
        VectorRecord(embed("spring dependency injection"), "other-kb content", {
            "chunk_id": "a-2", "user_id": 1, "knowledge_base_id": 11,
            "document_id": 101, "file_name": "other.txt", "page": 1, "chunk_index": 0,
        }),
    ])

    result = await store.retrieve(1, 10, "dependency injection", 10)

    assert [chunk.chunk_id for chunk in result] == ["a-1"]
    assert all(chunk.document_id == 100 for chunk in result)


@pytest.mark.asyncio
async def test_delete_document_is_scoped_and_idempotent():
    store = InMemoryVectorStore()
    await store.upsert([VectorRecord(embed("same"), "owned", {
        "chunk_id": "owned", "user_id": 1, "knowledge_base_id": 10,
        "document_id": 100, "file_name": "a.txt", "page": 1,
    }), VectorRecord(embed("same"), "foreign", {
        "chunk_id": "foreign", "user_id": 2, "knowledge_base_id": 20,
        "document_id": 100, "file_name": "b.txt", "page": 1,
    })])

    await store.delete_document(1, 10, 100)
    await store.delete_document(1, 10, 100)

    result = await store.retrieve(2, 20, "same", 10)
    assert [chunk.chunk_id for chunk in result] == ["foreign"]
