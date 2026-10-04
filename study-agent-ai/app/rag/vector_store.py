from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence
import math

from app.embeddings.local import embed
from app.schemas.rag import RetrievedChunk

@dataclass(frozen=True)
class VectorRecord:
    vector: Sequence[float]
    content: str
    metadata: Dict[str, object]

class VectorStoreAdapter(ABC):
    @abstractmethod
    async def upsert(self, records: List[VectorRecord]) -> None: ...
    @abstractmethod
    async def delete_document(self, user_id: int, knowledge_base_id: int, document_id: int) -> None: ...
    @abstractmethod
    async def delete_knowledge_base(self, user_id: int, knowledge_base_id: int) -> None: ...
    @abstractmethod
    async def retrieve(self, user_id: int, knowledge_base_id: int, query: str, top_k: int,
                       score_threshold: Optional[float] = None) -> List[RetrievedChunk]: ...

class InMemoryVectorStore(VectorStoreAdapter):
    def __init__(self) -> None:
        self._records: Dict[str, VectorRecord] = {}

    async def upsert(self, records: List[VectorRecord]) -> None:
        for record in records:
            self._records[str(record.metadata["chunk_id"])] = record

    async def delete_document(self, user_id: int, knowledge_base_id: int, document_id: int) -> None:
        self._records = {key: value for key, value in self._records.items()
                         if not (value.metadata.get("user_id") == user_id
                                 and value.metadata.get("knowledge_base_id") == knowledge_base_id
                                 and value.metadata.get("document_id") == document_id)}

    async def delete_knowledge_base(self, user_id: int, knowledge_base_id: int) -> None:
        self._records = {key: value for key, value in self._records.items()
                         if not (value.metadata.get("user_id") == user_id
                                 and value.metadata.get("knowledge_base_id") == knowledge_base_id)}

    async def retrieve(self, user_id: int, knowledge_base_id: int, query: str, top_k: int,
                       score_threshold: Optional[float] = None) -> List[RetrievedChunk]:
        query_vector = embed(query)
        candidates = []
        for record in self._records.values():
            # Both filters are mandatory. No caller can retrieve another tenant's records.
            if record.metadata.get("user_id") != user_id or record.metadata.get("knowledge_base_id") != knowledge_base_id:
                continue
            score = _cosine(query_vector, record.vector)
            if score_threshold is not None and score < score_threshold:
                continue
            candidates.append((score, record))
        candidates.sort(key=lambda item: item[0], reverse=True)
        return [RetrievedChunk(chunkId=str(record.metadata["chunk_id"]),
                               documentId=int(record.metadata["document_id"]),
                               fileName=str(record.metadata["file_name"]),
                               page=int(record.metadata.get("page", 1)),
                               content=record.content, score=score)
                for score, record in candidates[:top_k]]

    def clear(self) -> None:
        self._records.clear()

def _cosine(left: Sequence[float], right: Sequence[float]) -> float:
    denominator = math.sqrt(sum(x * x for x in left)) * math.sqrt(sum(x * x for x in right))
    return sum(x * y for x, y in zip(left, right)) / denominator if denominator else 0.0

# The adapter is intentionally replaceable with Qdrant/Milvus/etc. later.
vector_store = InMemoryVectorStore()
