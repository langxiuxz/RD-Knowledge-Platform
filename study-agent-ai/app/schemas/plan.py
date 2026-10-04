from datetime import date
from typing import Dict, List

from pydantic import BaseModel, Field


class PlanProfile(BaseModel):
    weak_topics: List[str] = Field(default_factory=list, alias="weakTopics")
    learning_progress: List[Dict] = Field(default_factory=list, alias="learningProgress")
    recent_quiz_results: List[Dict] = Field(default_factory=list, alias="recentQuizResults")

    model_config = {"populate_by_name": True}


class PlanGenerateRequest(BaseModel):
    user_id: int = Field(alias="userId", gt=0)
    goal: str = Field(min_length=1, max_length=500)
    start_date: date = Field(alias="startDate")
    end_date: date = Field(alias="endDate")
    daily_minutes: int = Field(alias="dailyMinutes", ge=1, le=1440)
    profile: PlanProfile

    model_config = {"populate_by_name": True}
