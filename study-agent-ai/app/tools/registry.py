from collections.abc import Callable
from typing import Dict


ALLOWED_TOOLS = frozenset({
    "get_learning_progress",
    "get_wrong_questions",
    "get_weak_topics",
    "get_recent_quiz_results",
})


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: Dict[str, Callable] = {}

    def register(self, name: str, handler: Callable) -> None:
        if name not in ALLOWED_TOOLS:
            raise ValueError(f"Tool is not whitelisted: {name}")
        self._tools[name] = handler

    def get(self, name: str) -> Callable:
        if name not in self._tools:
            raise KeyError(name)
        return self._tools[name]
