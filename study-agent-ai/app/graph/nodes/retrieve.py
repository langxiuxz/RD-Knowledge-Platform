from app.graph.state import AgentState
from app.rag.vector_store import InMemoryVectorStore


class RetrieveNode:
    def __init__(self, vector_store: InMemoryVectorStore, top_k: int) -> None:
        self._vector_store = vector_store
        self._top_k = top_k

    async def __call__(self, state: AgentState) -> dict:
        chunks = await self._vector_store.retrieve(
            state["user_id"], state["knowledge_base_id"], state["content"], self._top_k, None
        )
        return {"retrieved_chunks": [chunk.model_dump(by_alias=True) for chunk in chunks]}
