"""Backend-independent vector store contract and record format."""
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence

from app.schemas.rag import RetrievedChunk


@dataclass(frozen=True)
class VectorRecord:
    vector: Sequence[float]
    content: str
    metadata: Dict[str, object]

class VectorStoreAdapter(ABC):
    async def initialize(self) -> None:
        """Prepare the backend before accepting requests."""

    async def close(self) -> None:
        """Release connections without deleting stored vectors."""

    @abstractmethod
    async def upsert(self, records: List[VectorRecord]) -> None: ...
    @abstractmethod
    async def delete_document(self, user_id: int, knowledge_base_id: int, document_id: int) -> None: ...
    @abstractmethod
    async def delete_knowledge_base(self, user_id: int, knowledge_base_id: int) -> None: ...
    @abstractmethod
    async def retrieve(self, user_id: int, knowledge_base_id: int, query: str, top_k: int,
                       score_threshold: Optional[float] = None) -> List[RetrievedChunk]: ...

