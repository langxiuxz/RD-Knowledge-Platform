from typing import Dict, List, Optional, Sequence
import math

from app.embeddings.local import embed
from app.rag.base import VectorRecord, VectorStoreAdapter
from app.schemas.rag import RetrievedChunk

class InMemoryVectorStore(VectorStoreAdapter):
    def __init__(self) -> None:
        self._records: Dict[str, VectorRecord] = {}

    async def upsert(self, records: List[VectorRecord]) -> None:
        for record in records:
            key = f"{record.metadata['project_id']}:{record.metadata['knowledge_base_id']}:{record.metadata['document_version_id']}:{record.metadata['chunk_id']}"
            self._records[key] = record

    async def delete_document_version(self, project_id: int, knowledge_base_id: int, document_version_id: int) -> None:
        self._records = {key: value for key, value in self._records.items()
                         if not (value.metadata.get("project_id") == project_id
                                 and value.metadata.get("knowledge_base_id") == knowledge_base_id
                                 and value.metadata.get("document_version_id") == document_version_id)}

    async def delete_knowledge_base(self, project_id: int, knowledge_base_id: int) -> None:
        self._records = {key: value for key, value in self._records.items()
                         if not (value.metadata.get("project_id") == project_id
                                 and value.metadata.get("knowledge_base_id") == knowledge_base_id)}

    async def retrieve(self, project_id: int, knowledge_base_id: int, knowledge_snapshot_id: int,
                       document_version_ids: List[int], query: str, top_k: int,
                       score_threshold: Optional[float] = None) -> List[RetrievedChunk]:
        if not document_version_ids:
            return []
        allowed = set(document_version_ids)
        query_vector = embed(query)
        candidates = []
        for record in self._records.values():
            metadata = record.metadata
            if (metadata.get("project_id") != project_id
                    or metadata.get("knowledge_base_id") != knowledge_base_id
                    or metadata.get("document_version_id") not in allowed):
                continue
            score = _cosine(query_vector, record.vector)
            if score_threshold is not None and score < score_threshold:
                continue
            candidates.append((score, record))
        candidates.sort(key=lambda item: item[0], reverse=True)
        return [RetrievedChunk(chunkId=str(record.metadata["chunk_id"]),
                               documentId=int(record.metadata["document_id"]),
                               documentVersionId=int(record.metadata["document_version_id"]),
                               knowledgeSnapshotId=knowledge_snapshot_id,
                               fileName=str(record.metadata["file_name"]), page=int(record.metadata.get("page", 1)),
                               content=record.content, score=score)
                for score, record in candidates[:top_k]]

    def clear(self) -> None:
        self._records.clear()

def _cosine(left: Sequence[float], right: Sequence[float]) -> float:
    denominator = math.sqrt(sum(x * x for x in left)) * math.sqrt(sum(x * x for x in right))
    return sum(x * y for x, y in zip(left, right)) / denominator if denominator else 0.0

def create_vector_store(settings=None) -> VectorStoreAdapter:
    from app.core.config import get_settings

    settings = settings or get_settings()
    if settings.vector_store_provider in {"memory", "none"}:
        # "none" is retained as an explicit legacy development setting.
        return InMemoryVectorStore()
    if settings.vector_store_provider == "milvus":
        from app.rag.milvus_store import MilvusVectorStore
        return MilvusVectorStore(settings)
    raise ValueError("Unsupported vector store provider")


vector_store = create_vector_store()
