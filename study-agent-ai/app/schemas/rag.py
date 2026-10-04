from pydantic import BaseModel, Field


class RetrieveRequest(BaseModel):
    user_id: int = Field(alias="userId", gt=0)
    knowledge_base_id: int = Field(alias="knowledgeBaseId", gt=0)
    query: str = Field(min_length=1, max_length=10000)
    top_k: int = Field(default=5, alias="topK", ge=1, le=50)

    model_config = {"populate_by_name": True}


class RetrievedChunk(BaseModel):
    chunk_id: str = Field(alias="chunkId")
    document_id: int = Field(alias="documentId")
    content: str
    metadata: dict = Field(default_factory=dict)

    model_config = {"populate_by_name": True}
