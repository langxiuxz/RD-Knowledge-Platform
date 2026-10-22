from typing import Any, List, Literal, Optional

from pydantic import BaseModel, Field


class ConversationMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=2000)


class AgentInvokeRequest(BaseModel):
    user_id: int = Field(alias="userId", gt=0)
    conversation_id: int = Field(alias="conversationId", gt=0)
    message_id: int = Field(alias="messageId", gt=0)
    content: str = Field(min_length=1, max_length=10000)
    mode: Literal["CHAT", "RAG", "AGENT"]
    knowledge_base_id: Optional[int] = Field(default=None, alias="knowledgeBaseId", gt=0)
    project_id: Optional[int] = Field(default=None, alias="projectId", gt=0)
    knowledge_snapshot_id: Optional[int] = Field(default=None, alias="knowledgeSnapshotId", gt=0)
    allowed_document_version_ids: List[int] = Field(default_factory=list, alias="allowedDocumentVersionIds", max_length=10000)
    stream: bool
    recent_messages: List["ConversationMessage"] = Field(default_factory=list, alias="recentMessages", max_length=8)

    model_config = {"populate_by_name": True}


class AgentCitation(BaseModel):
    chunk_id: str = Field(alias="chunkId")
    document_id: int = Field(alias="documentId")
    file_name: str = Field(alias="fileName")
    page: Optional[int] = None
    score: Optional[float] = None
    document_version_id: int = Field(alias="documentVersionId")
    knowledge_snapshot_id: int = Field(alias="knowledgeSnapshotId")

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
