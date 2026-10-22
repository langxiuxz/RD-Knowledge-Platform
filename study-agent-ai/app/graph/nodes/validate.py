from app.graph.state import AgentState


async def validate_input(state: AgentState) -> dict:
    content = state.get("content", "").strip()
    if not content:
        raise ValueError("content must not be blank")
    if state.get("mode") == "RAG" and not all(state.get(key) for key in
            ("project_id", "knowledge_base_id", "knowledge_snapshot_id")):
        raise ValueError("project, knowledge base, and snapshot are required in RAG mode")
    return {
        "content": content,
        "messages": [{"role": "user", "content": content}],
        "retrieved_chunks": [],
        "pending_tool_calls": [],
        "tool_calls": [],
        "citations": [],
        "error": None,
    }
