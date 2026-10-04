from app.graph.state import AgentState


async def finalize(state: AgentState) -> dict:
    answer = state.get("answer", "").strip()
    if not answer:
        answer = "暂时无法生成回答，请稍后重试。"
    return {"answer": answer, "pending_tool_calls": []}
