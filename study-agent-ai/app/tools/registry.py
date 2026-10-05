import asyncio
from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Dict, Type

from pydantic import BaseModel, Field

from app.clients.spring_boot import SpringBootClient
from app.core.config import Settings


ALLOWED_TOOLS = frozenset({
    "get_learning_progress",
    "get_topic_progress",
    "get_wrong_questions",
    "get_weak_topics",
    "get_recent_quiz_results",
    "search_knowledge_base",
})


class ToolArguments(BaseModel):
    limit: int = Field(default=5, ge=1, le=20)


class TopicArguments(ToolArguments):
    topic: str = Field(min_length=1, max_length=128)


class WrongQuestionArguments(ToolArguments):
    topic: str | None = Field(default=None, min_length=1, max_length=128)


class KnowledgeSearchArguments(BaseModel):
    query: str = Field(min_length=1, max_length=1000)


ToolHandler = Callable[[int, BaseModel], Awaitable[Any]]


@dataclass(frozen=True)
class ToolDefinition:
    arguments: Type[BaseModel]
    handler: ToolHandler


class ToolRegistry:
    def __init__(self, timeout: float = 5.0) -> None:
        self._tools: Dict[str, ToolDefinition] = {}
        self._timeout = timeout

    def register(self, name: str, handler: ToolHandler, arguments: Type[BaseModel] = ToolArguments) -> None:
        if name not in ALLOWED_TOOLS:
            raise ValueError(f"Tool is not whitelisted: {name}")
        self._tools[name] = ToolDefinition(arguments, handler)

    def schemas(self, include_knowledge_search: bool = False) -> list[dict[str, Any]]:
        descriptions = {
            "get_learning_progress": (ToolArguments, "Get a summary of the current user learning progress by topic. Do not use for general questions."),
            "get_topic_progress": (TopicArguments, "Get mastery, quiz accuracy, study minutes, and wrong-question count for a topic of the current user."),
            "get_wrong_questions": (WrongQuestionArguments, "Get recent wrong questions for the current user, optionally filtered by topic; limit 1 to 20."),
            "get_weak_topics": (ToolArguments, "Get weak topics for the current user."),
            "get_recent_quiz_results": (ToolArguments, "Get recent quiz results for the current user."),
        }
        result = []
        for name, (schema, description) in descriptions.items():
            result.append({"type": "function", "function": {
                "name": name, "description": description,
                "parameters": schema.model_json_schema(),
            }})
        if include_knowledge_search:
            result.append({"type": "function", "function": {
                "name": "search_knowledge_base",
                "description": "Search the bound personal knowledge base only when the user explicitly asks to use their notes or materials.",
                "parameters": KnowledgeSearchArguments.model_json_schema(),
            }})
        return result

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
    registry.register("get_topic_progress", lambda user_id, args: client.get_topic_progress(user_id, args.topic))
    registry.register("get_wrong_questions", lambda user_id, args: client.get_wrong_questions(user_id, args.limit, args.topic))
    registry.register("get_weak_topics", lambda user_id, args: client.get_weak_topics(user_id, args.limit))
    registry.register("get_recent_quiz_results", lambda user_id, args: client.get_recent_quiz_results(user_id, args.limit))
    return registry
