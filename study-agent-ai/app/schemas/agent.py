from typing import Dict, List, Literal, Optional

from pydantic import BaseModel, Field


class AgentInvokeRequest(BaseModel):
    user_id: int = Field(alias="userId", gt=0)
    conversation_id: int = Field(alias="conversationId", gt=0)
    message_id: int = Field(alias="messageId", gt=0)
    content: str = Field(min_length=1, max_length=10000)
    mode: Literal["CHAT", "RAG", "AGENT"]
    knowledge_base_id: Optional[int] = Field(default=None, alias="knowledgeBaseId", gt=0)
    stream: bool

    model_config = {"populate_by_name": True}


class AgentInvokeResponse(BaseModel):
    answer: str
    citations: List[Dict] = Field(default_factory=list)
    tool_calls: List[Dict] = Field(default_factory=list, alias="toolCalls")
    trace_id: str = Field(alias="traceId")

    model_config = {"populate_by_name": True}
