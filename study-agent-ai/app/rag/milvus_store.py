"""Persistent Milvus adapter. Synchronous SDK calls run outside the ASGI event loop."""
import asyncio
import hashlib
import json
import logging
import math
from threading import Lock
from typing import List, Optional

from app.core.config import Settings
from app.core.exceptions import AIServiceError
from app.embeddings.local import DIMENSION, MODEL, embed
from app.rag.base import VectorRecord, VectorStoreAdapter
from app.schemas.rag import RetrievedChunk

logger = logging.getLogger(__name__)
OUTPUT_FIELDS = ["chunk_id", "document_id", "file_name", "page", "content"]


class MilvusVectorStore(VectorStoreAdapter):
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = None
        self._lock = Lock()

    async def initialize(self) -> None:
        await self._run("initialize", self._get_client)

    async def close(self) -> None:
        def close_client():
            with self._lock:
                if self._client is not None:
                    self._client.close()
                    self._client = None
        await self._run("close", close_client)

    async def _run(self, operation, function):
        try:
            return await asyncio.to_thread(function)
        except Exception as exc:
            # SDK errors may contain connection credentials; log only their type.
            logger.error("Milvus operation failed operation=%s error_type=%s", operation, type(exc).__name__)
            raise AIServiceError("Vector store temporarily unavailable or incompatible") from None

    def _get_client(self):
        with self._lock:
            if self._client is not None:
                return self._client
            from pymilvus import DataType, MilvusClient

            client = MilvusClient(uri=self._settings.milvus_uri,
                                  token=self._settings.milvus_token,
                                  db_name=self._settings.milvus_db_name,
                                  timeout=self._settings.milvus_timeout)
            collection = self._settings.milvus_collection
            timeout = self._settings.milvus_timeout
            try:
                if not client.has_collection(collection_name=collection, timeout=timeout):
                    schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
                    schema.add_field("id", DataType.VARCHAR, is_primary=True, max_length=64)
                    schema.add_field("chunk_id", DataType.VARCHAR, max_length=512)
                    for name in ("user_id", "knowledge_base_id", "document_id", "page", "chunk_index"):
                        schema.add_field(name, DataType.INT64)
                    schema.add_field("file_name", DataType.VARCHAR, max_length=1024)
                    schema.add_field("embedding_model", DataType.VARCHAR, max_length=512)
                    schema.add_field("content", DataType.VARCHAR, max_length=65535)
                    schema.add_field("metadata", DataType.JSON)
                    schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=DIMENSION)
                    indexes = MilvusClient.prepare_index_params()
                    indexes.add_index(field_name="embedding", index_name="embedding",
                                      index_type="AUTOINDEX", metric_type="COSINE")
                    try:
                        client.create_collection(collection_name=collection, schema=schema,
                                                 index_params=indexes, consistency_level="Strong", timeout=timeout)
                    except Exception:
                        # Another worker may have created the same collection concurrently.
                        if not client.has_collection(collection_name=collection, timeout=timeout):
                            raise
                self._validate_collection(client, DataType)
                client.load_collection(collection_name=collection, timeout=timeout)
            except Exception:
                client.close()
                raise
            self._client = client
            return client

    def _validate_collection(self, client, data_type) -> None:
        description = client.describe_collection(collection_name=self._settings.milvus_collection,
                                                 timeout=self._settings.milvus_timeout)
        fields = {field["name"]: field for field in description["fields"]}
        required = {"id": data_type.VARCHAR, "chunk_id": data_type.VARCHAR,
                    "file_name": data_type.VARCHAR, "content": data_type.VARCHAR,
                    "embedding_model": data_type.VARCHAR, "metadata": data_type.JSON,
                    "embedding": data_type.FLOAT_VECTOR}
        required.update({name: data_type.INT64 for name in
                         ("user_id", "knowledge_base_id", "document_id", "page", "chunk_index")})
        if any(name not in fields or fields[name]["type"] != expected for name, expected in required.items()):
            raise ValueError("Incompatible Milvus collection fields")
        if (description.get("auto_id") or not fields["id"].get("is_primary")
                or int(fields["embedding"].get("params", {}).get("dim", 0)) != DIMENSION):
            raise ValueError("Incompatible Milvus primary key or embedding dimension")
        index = client.describe_index(collection_name=self._settings.milvus_collection,
                                      index_name="embedding", timeout=self._settings.milvus_timeout)
        if index.get("metric_type") != "COSINE":
            raise ValueError("Incompatible Milvus similarity metric")

    @staticmethod
    def _positive_int64(value: int) -> int:
        if type(value) is not int or not 0 < value < 2**63:
            raise ValueError("Resource identifiers must be positive int64 values")
        return value

    @classmethod
    def _scope(cls, user_id: int, knowledge_base_id: int) -> str:
        # Format only validated integers, never raw caller strings, into expressions.
        cls._positive_int64(user_id)
        cls._positive_int64(knowledge_base_id)
        return f"user_id == {user_id} and knowledge_base_id == {knowledge_base_id}"

    @staticmethod
    def _varchar(value: str, max_bytes: int) -> str:
        if not isinstance(value, str) or not value or len(value.encode("utf-8")) > max_bytes:
            raise ValueError("Invalid vector record text length")
        return value

    @staticmethod
    def _row(record: VectorRecord) -> dict:
        metadata = record.metadata
        user_id, kb_id = metadata["user_id"], metadata["knowledge_base_id"]
        MilvusVectorStore._scope(user_id, kb_id)
        document_id = metadata["document_id"]
        MilvusVectorStore._positive_int64(document_id)
        vector = list(record.vector)
        if len(vector) != DIMENSION or any(not math.isfinite(value) for value in vector):
            raise ValueError("Invalid embedding dimension or values")
        model = metadata.get("embedding_model", MODEL)
        if model != MODEL:
            raise ValueError("Embedding model does not match this collection")
        chunk_id = MilvusVectorStore._varchar(metadata["chunk_id"], 512)
        file_name = MilvusVectorStore._varchar(metadata["file_name"], 1024)
        content = MilvusVectorStore._varchar(record.content, 65535)
        page = metadata.get("page")
        page = MilvusVectorStore._positive_int64(1 if page is None else page)
        chunk_index = metadata.get("chunk_index", 0)
        if type(chunk_index) is not int or not 0 <= chunk_index < 2**63:
            raise ValueError("Invalid chunk index")
        # A foreign tenant using the same chunk ID must never overwrite another row.
        key = hashlib.sha256(json.dumps([user_id, kb_id, chunk_id]).encode("utf-8")).hexdigest()
        return {"id": key, "chunk_id": chunk_id, "user_id": user_id, "knowledge_base_id": kb_id,
                "document_id": document_id, "file_name": file_name,
                "page": page, "chunk_index": chunk_index,
                "embedding_model": model, "metadata": dict(metadata),
                "content": content, "embedding": vector}

    async def upsert(self, records: List[VectorRecord]) -> None:
        if not records:
            return
        # Validate the whole input before writing any batches.
        rows = [self._row(record) for record in records]

        def write():
            client = self._get_client()
            for offset in range(0, len(rows), 128):
                client.upsert(collection_name=self._settings.milvus_collection, data=rows[offset:offset + 128],
                              timeout=self._settings.milvus_timeout)
            # Indexing only reports READY after Milvus has flushed the accepted rows.
            client.flush(collection_name=self._settings.milvus_collection, timeout=self._settings.milvus_timeout)
        await self._run("upsert", write)

    async def _delete(self, expression: str) -> None:
        def delete():
            client = self._get_client()
            client.delete(collection_name=self._settings.milvus_collection, filter=expression,
                          timeout=self._settings.milvus_timeout)
            client.flush(collection_name=self._settings.milvus_collection, timeout=self._settings.milvus_timeout)
        await self._run("delete", delete)

    async def delete_document(self, user_id: int, knowledge_base_id: int, document_id: int) -> None:
        scope = self._scope(user_id, knowledge_base_id)
        self._positive_int64(document_id)
        await self._delete(f"{scope} and document_id == {document_id}")

    async def delete_knowledge_base(self, user_id: int, knowledge_base_id: int) -> None:
        await self._delete(self._scope(user_id, knowledge_base_id))

    async def retrieve(self, user_id: int, knowledge_base_id: int, query: str, top_k: int,
                       score_threshold: Optional[float] = None) -> List[RetrievedChunk]:
        scope = self._scope(user_id, knowledge_base_id)
        if type(top_k) is not int or not 1 <= top_k <= 50:
            raise ValueError("top_k must be between 1 and 50")
        if score_threshold is not None and not -1 <= score_threshold <= 1:
            raise ValueError("score_threshold must be between -1 and 1")
        query_vector = embed(query)

        def search():
            return self._get_client().search(
                collection_name=self._settings.milvus_collection, data=[query_vector],
                anns_field="embedding", filter=scope, limit=top_k,
                output_fields=OUTPUT_FIELDS, search_params={"metric_type": "COSINE", "params": {}},
                consistency_level="Strong", timeout=self._settings.milvus_timeout)

        results = await self._run("search", search)
        chunks = []
        for hit in results[0]:
            score = float(hit["distance"])
            if score_threshold is not None and score < score_threshold:
                continue
            entity = hit["entity"]
            chunks.append(RetrievedChunk(chunkId=entity["chunk_id"], documentId=entity["document_id"],
                                         fileName=entity["file_name"], page=entity.get("page") or 1,
                                         content=entity["content"], score=max(-1.0, min(1.0, score))))
        return chunks
