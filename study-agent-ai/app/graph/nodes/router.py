from app.graph.state import AgentState


async def router(state: AgentState) -> dict:
    return {"mode": state["mode"]}


def route_mode(state: AgentState) -> str:
    if state["mode"] == "AGENT" and state.get("knowledge_base_id"):
        return "AGENT_RAG"
    return state["mode"]


def route_after_retrieve(state: AgentState) -> str:
    return "AGENT" if state["mode"] == "AGENT" else "RAG"
