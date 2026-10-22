"""Opt-in real server test; only creates and drops a randomly named test collection."""
import asyncio
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import pytest

from app.core.config import Settings
from app.embeddings.local import embed
from app.rag.milvus_store import MilvusVectorStore
from app.rag.vector_store import VectorRecord

pytestmark = pytest.mark.skipif(not os.getenv("MILVUS_TEST_URI"), reason="MILVUS_TEST_URI is not configured")


def test_persistence_across_python_processes_and_scoped_idempotent_deletes():
    from pymilvus import MilvusClient

    settings = Settings(_env_file=None, vector_store_provider="milvus",
                        milvus_uri=os.environ["MILVUS_TEST_URI"],
                        milvus_token=os.getenv("MILVUS_TEST_TOKEN", ""),
                        milvus_db_name=os.getenv("MILVUS_TEST_DB_NAME", "default"),
                        milvus_collection="study_agent_test_" + uuid4().hex,
                        milvus_timeout=30)
    store = MilvusVectorStore(settings)

    def record(project_id, kb_id, content):
        return VectorRecord(embed("persistence test"), content, {
            "chunk_id": "same-chunk", "project_id": project_id, "knowledge_base_id": kb_id,
            "document_id": 100, "document_version_id": 1000,
            "file_name": "持久化.txt", "page": 1, "chunk_index": 0,
        })

    async def write_and_disconnect():
        try:
            await store.initialize()
            await store.upsert([record(1, 10, "old"), record(2, 10, "foreign"), record(1, 11, "other-kb")])
            await store.upsert([record(1, 10, "persisted")])
        finally:
            await store.close()

    async def delete_and_verify():
        try:
            for _ in range(2):
                await store.delete_document_version(1, 10, 1000)
            assert await store.retrieve(1, 10, 501, [1000], "persistence test", 5) == []
            assert len(await store.retrieve(2, 10, 501, [1000], "persistence test", 5)) == 1
            assert len(await store.retrieve(1, 11, 501, [1000], "persistence test", 5)) == 1
            for _ in range(2):
                await store.delete_knowledge_base(2, 10)
            assert await store.retrieve(2, 10, 501, [1000], "persistence test", 5) == []
            assert len(await store.retrieve(1, 11, 501, [1000], "persistence test", 5)) == 1
        finally:
            await store.close()

    try:
        asyncio.run(write_and_disconnect())
        # The second process cannot access the first process's Python memory.
        script = """
import asyncio, json, os
from app.core.config import Settings
from app.rag.milvus_store import MilvusVectorStore
async def main():
    store = MilvusVectorStore(Settings(_env_file=None, **json.loads(os.environ['TEST_MILVUS_SETTINGS'])))
    try:
        await store.initialize()
        chunks = await store.retrieve(1, 10, 501, [1000], 'persistence test', 5, 0.6)
        print(json.dumps([chunk.model_dump(by_alias=True) for chunk in chunks]))
    finally:
        await store.close()
asyncio.run(main())
"""
        env = dict(os.environ, TEST_MILVUS_SETTINGS=settings.model_dump_json())
        result = subprocess.run([sys.executable, "-c", script], env=env, text=True,
                                capture_output=True, timeout=120,
                                cwd=Path(__file__).resolve().parents[2])
        assert result.returncode == 0, "Milvus subprocess failed (inspect server logs)"
        chunks = json.loads(result.stdout.strip().splitlines()[-1])
        assert len(chunks) == 1
        assert chunks[0]["content"] == "persisted"
        assert chunks[0]["fileName"] == "持久化.txt"
        asyncio.run(delete_and_verify())
    finally:
        client = MilvusClient(uri=settings.milvus_uri, token=settings.milvus_token,
                              db_name=settings.milvus_db_name, timeout=30)
        try:
            if client.has_collection(settings.milvus_collection):
                client.drop_collection(settings.milvus_collection, timeout=30)
        finally:
            client.close()
