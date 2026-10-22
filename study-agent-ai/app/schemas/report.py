from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ReportSource(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    document_version_id: int = Field(alias="documentVersionId", gt=0)
    title: str = Field(min_length=1, max_length=256)


class CapabilitySummaryRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    assignment_id: int = Field(alias="assignmentId", gt=0)
    path_version: int = Field(alias="pathVersion", ge=0)
    knowledge_snapshot_id: int = Field(alias="knowledgeSnapshotId", gt=0)
    facts: dict = Field(min_length=1)
    allowed_sources: List[ReportSource] = Field(default_factory=list, alias="allowedSources", max_length=100)

    @model_validator(mode="after")
    def unique_sources(self):
        ids = [item.document_version_id for item in self.allowed_sources]
        if len(ids) != len(set(ids)):
            raise ValueError("allowed source versions must be unique")
        return self


class CapabilitySummaryResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    summary: str = Field(min_length=1, max_length=4000)
    strengths: List[str] = Field(default_factory=list, max_length=20)
    risks: List[str] = Field(default_factory=list, max_length=20)
    recommended_sources: List[ReportSource] = Field(default_factory=list, alias="recommendedSources", max_length=20)
    model_name: Optional[str] = Field(default=None, alias="modelName", max_length=128)


class ModelCapabilitySummary(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    summary: str = Field(min_length=1, max_length=4000)
    strengths: List[str] = Field(default_factory=list, max_length=20)
    risks: List[str] = Field(default_factory=list, max_length=20)
    recommended_sources: List[ReportSource] = Field(default_factory=list, alias="recommendedSources", max_length=20)
