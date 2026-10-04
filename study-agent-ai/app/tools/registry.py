import asyncio
from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Dict, Type

from pydantic import BaseModel, Field

from app.clients.spring_boot import SpringBootClient
from app.core.config import Settings


ALLOWED_TOOLS = frozenset({
    "get_learning_progress",
    "get_wrong_questions",
    "get_weak_topics",
    "get_recent_quiz_results",
})


class ToolArguments(BaseModel):
    limit: int = Field(default=5, ge=1, le=20)


ToolHandler = Callable[[int, ToolArguments], Awaitable[Any]]


@dataclass(frozen=True)
class ToolDefinition:
    arguments: Type[ToolArguments]
    handler: ToolHandler


class ToolRegistry:
    def __init__(self, timeout: float = 5.0) -> None:
        self._tools: Dict[str, ToolDefinition] = {}
        self._timeout = timeout

    def register(self, name: str, handler: ToolHandler,
                 arguments: Type[ToolArguments] = ToolArguments) -> None:
        if name not in ALLOWED_TOOLS:
            raise ValueError(f"Tool is not whitelisted: {name}")
        self._tools[name] = ToolDefinition(arguments, handler)

    async def execute(self, name: str, user_id: int, raw_arguments: Dict[str, Any]) -> Any:
        if name not in ALLOWED_TOOLS or name not in self._tools:
            raise ValueError(f"Tool is not available: {name}")
        definition = self._tools[name]
        arguments = definition.arguments.model_validate(raw_arguments)
        return await asyncio.wait_for(definition.handler(user_id, arguments), timeout=self._timeout)


def create_default_registry(settings: Settings) -> ToolRegistry:
    client = SpringBootClient(settings)
    registry = ToolRegistry(settings.tool_timeout)
    registry.register("get_learning_progress", lambda user_id, args: client.get_learning_progress(user_id, args.limit))
    registry.register("get_wrong_questions", lambda user_id, args: client.get_wrong_questions(user_id, args.limit))
    registry.register("get_weak_topics", lambda user_id, args: client.get_weak_topics(user_id, args.limit))
    registry.register("get_recent_quiz_results", lambda user_id, args: client.get_recent_quiz_results(user_id, args.limit))
    return registry
