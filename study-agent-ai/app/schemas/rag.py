from typing import Annotated, List, Optional
from pydantic import BaseModel, Field

class RetrieveRequest(BaseModel):
    project_id: int = Field(alias="projectId", gt=0, lt=2**63)
    knowledge_base_id: int = Field(alias="knowledgeBaseId", gt=0, lt=2**63)
    knowledge_snapshot_id: int = Field(alias="knowledgeSnapshotId", gt=0, lt=2**63)
    document_version_ids: List[Annotated[int, Field(gt=0, lt=2**63)]] = Field(alias="documentVersionIds", max_length=10000)
    query: str = Field(min_length=1, max_length=10000)
    top_k: int = Field(default=5, alias="topK", ge=1, le=50)
    score_threshold: Optional[float] = Field(default=None, alias="scoreThreshold", ge=-1, le=1)
    model_config = {"populate_by_name": True}

class IndexRequest(BaseModel):
    project_id: int = Field(alias="projectId", gt=0, lt=2**63)
    knowledge_base_id: int = Field(alias="knowledgeBaseId", gt=0, lt=2**63)
    document_id: int = Field(alias="documentId", gt=0, lt=2**63)
    document_version_id: int = Field(alias="documentVersionId", gt=0, lt=2**63)
    task_id: str = Field(alias="taskId", min_length=1, max_length=64)
    file_url: str = Field(alias="fileUrl", min_length=1, max_length=2048)
    file_type: str = Field(alias="fileType", pattern="^(pdf|docx|txt|md)$")
    file_name: str = Field(alias="fileName", min_length=1, max_length=255)
    content_hash: str = Field(alias="contentHash", pattern="^[a-f0-9]{64}$")
    model_config = {"populate_by_name": True}

class RetrievedChunk(BaseModel):
    chunk_id: str = Field(alias="chunkId")
    document_id: int = Field(alias="documentId")
    document_version_id: int = Field(alias="documentVersionId")
    knowledge_snapshot_id: int = Field(alias="knowledgeSnapshotId")
    file_name: str = Field(alias="fileName")
    page: int = Field(ge=1)
    content: str
    score: float
    model_config = {"populate_by_name": True}
