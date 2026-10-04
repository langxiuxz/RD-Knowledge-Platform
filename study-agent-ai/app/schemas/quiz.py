from pydantic import BaseModel, Field


class ShortGradeRequest(BaseModel):
    question: str = Field(min_length=1)
    reference_answer: str = Field(alias="referenceAnswer", min_length=1)
    user_answer: str = Field(alias="userAnswer", min_length=1)

    model_config = {"populate_by_name": True}


class ShortGradeResponse(BaseModel):
    score: float = Field(ge=0, le=1)
    is_correct: bool = Field(alias="isCorrect")
    feedback: str

    model_config = {"populate_by_name": True}
