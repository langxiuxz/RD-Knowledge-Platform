from app.graph.state import AgentState


def build_graph():
    # LangGraph wiring is intentionally deferred to the Agent phase.
    return None


def initial_state(**values) -> AgentState:
    return AgentState(**values)
