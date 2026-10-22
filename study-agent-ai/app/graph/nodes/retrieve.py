from app.graph.state import AgentState
from app.rag.vector_store import VectorStoreAdapter

class RetrieveNode:
    def __init__(self, vector_store: VectorStoreAdapter, top_k: int) -> None:
        self._vector_store = vector_store
        self._top_k = top_k

    async def __call__(self, state: AgentState) -> dict:
        if not state.get("allowed_document_version_ids"):
            return {"retrieved_chunks": []}
        chunks = await self._vector_store.retrieve(
            state["project_id"], state["knowledge_base_id"], state["knowledge_snapshot_id"],
            state["allowed_document_version_ids"], state["content"], self._top_k, None
        )
        return {"retrieved_chunks": [chunk.model_dump(by_alias=True) for chunk in chunks]}
