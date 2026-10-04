from typing import Dict, List

from app.graph.state import AgentState
from app.tools.registry import ToolRegistry


async def agent_reasoning(state: AgentState) -> dict:
    content = state["content"].lower()
    selected: List[str] = []
    keywords: Dict[str, tuple[str, ...]] = {
        "get_learning_progress": ("进度", "学习情况", "progress"),
        "get_wrong_questions": ("错题", "错误", "wrong"),
        "get_weak_topics": ("薄弱", "弱项", "weak"),
        "get_recent_quiz_results": ("测验", "成绩", "quiz"),
    }
    for name, words in keywords.items():
        if any(word in content for word in words):
            selected.append(name)
    if not selected:
        selected.append("get_learning_progress")
    return {"pending_tool_calls": [{"name": name, "arguments": {"limit": 5}} for name in selected]}


class ToolNode:
    def __init__(self, registry: ToolRegistry, max_calls: int) -> None:
        self._registry = registry
        self._max_calls = max_calls

    async def __call__(self, state: AgentState) -> dict:
        results = []
        for call in state.get("pending_tool_calls", [])[:self._max_calls]:
            name = call["name"]
            try:
                result = await self._registry.execute(name, state["user_id"], call.get("arguments", {}))
                results.append({"name": name, "status": "success", "result": result})
            except Exception as exc:
                results.append({"name": name, "status": "failed", "error": str(exc)[:300]})
        return {"tool_calls": results}
