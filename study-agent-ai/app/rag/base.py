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
    async def initialize(self) -> None: ...
    async def close(self) -> None: ...
    @abstractmethod
    async def upsert(self, records: List[VectorRecord]) -> None: ...
    @abstractmethod
    async def delete_document_version(self, project_id: int, knowledge_base_id: int, document_version_id: int) -> None: ...
    @abstractmethod
    async def delete_knowledge_base(self, project_id: int, knowledge_base_id: int) -> None: ...
    @abstractmethod
    async def retrieve(self, project_id: int, knowledge_base_id: int, knowledge_snapshot_id: int,
                       document_version_ids: List[int], query: str, top_k: int,
                       score_threshold: Optional[float] = None) -> List[RetrievedChunk]: ...
