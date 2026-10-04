from datetime import date
from typing import Any, Dict, List, Literal

from pydantic import BaseModel, Field, model_validator


class PlanProfile(BaseModel):
    weak_topics: List[str] = Field(default_factory=list, alias="weakTopics")
    learning_progress: List[Dict[str, Any]] = Field(default_factory=list, alias="learningProgress")
    recent_quiz_results: List[Dict[str, Any]] = Field(default_factory=list, alias="recentQuizResults")

    model_config = {"populate_by_name": True}


class PlanGenerateRequest(BaseModel):
    user_id: int = Field(alias="userId", gt=0)
    goal: str = Field(min_length=1, max_length=500)
    start_date: date = Field(alias="startDate")
    end_date: date = Field(alias="endDate")
    daily_minutes: int = Field(alias="dailyMinutes", ge=1, le=1440)
    profile: PlanProfile

    model_config = {"populate_by_name": True}

    @model_validator(mode="after")
    def validate_dates(self):
        if self.end_date < self.start_date:
            raise ValueError("endDate must not be before startDate")
        return self


class GeneratedPlanTask(BaseModel):
    date: date
    topic: str = Field(min_length=1, max_length=128)
    type: Literal["READ", "QUIZ", "REVIEW", "CHAT"]
    estimated_minutes: int = Field(alias="estimatedMinutes", ge=0, le=1440)

    model_config = {"populate_by_name": True}


class PlanGenerateResponse(BaseModel):
    title: str = Field(min_length=1, max_length=128)
    tasks: List[GeneratedPlanTask] = Field(min_length=1)
