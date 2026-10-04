from typing import Any, List, Literal, Optional

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


class AgentCitation(BaseModel):
    chunk_id: str = Field(alias="chunkId")
    document_id: int = Field(alias="documentId")
    file_name: str = Field(alias="fileName")
    page: Optional[int] = None
    score: Optional[float] = None

    model_config = {"populate_by_name": True}


class AgentToolCall(BaseModel):
    name: str
    status: Literal["success", "failed"]
    result: Optional[Any] = None
    error: Optional[str] = None


class AgentInvokeResponse(BaseModel):
    answer: str
    citations: List[AgentCitation] = Field(default_factory=list)
    tool_calls: List[AgentToolCall] = Field(default_factory=list, alias="toolCalls")
    trace_id: str = Field(alias="traceId")

    model_config = {"populate_by_name": True}
