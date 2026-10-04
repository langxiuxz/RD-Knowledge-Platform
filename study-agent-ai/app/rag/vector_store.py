from abc import ABC, abstractmethod
from typing import List

from app.schemas.rag import RetrievedChunk


class VectorStoreAdapter(ABC):
    @abstractmethod
    async def retrieve(self, user_id: int, knowledge_base_id: int, query: str, top_k: int) -> List[RetrievedChunk]:
        raise NotImplementedError


class NoopVectorStore(VectorStoreAdapter):
    async def retrieve(self, user_id: int, knowledge_base_id: int, query: str, top_k: int) -> List[RetrievedChunk]:
        # The tenant filters are part of the adapter contract from Phase 0 onward.
        return []
