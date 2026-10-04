from typing import List, Literal, Optional, Union

from pydantic import BaseModel, Field, model_validator

QuestionType = Literal["SINGLE", "MULTIPLE", "JUDGE", "SHORT"]


class QuizGenerateRequest(BaseModel):
    user_id: int = Field(alias="userId", gt=0)
    topic: Optional[str] = Field(default=None, max_length=255)
    knowledge_base_id: Optional[int] = Field(default=None, alias="knowledgeBaseId", gt=0)
    question_count: int = Field(alias="questionCount", ge=1, le=50)
    difficulty: Literal["EASY", "MEDIUM", "HARD"]
    types: List[QuestionType] = Field(min_length=1)

    model_config = {"populate_by_name": True}


class QuizOption(BaseModel):
    key: str = Field(min_length=1, max_length=16)
    text: str = Field(min_length=1, max_length=1000)


class GeneratedQuestion(BaseModel):
    type: QuestionType
    topic: Optional[str] = Field(default=None, max_length=128)
    content: str = Field(min_length=1)
    options: Optional[List[QuizOption]] = None
    answer: Union[str, List[str]]
    explanation: Optional[str] = None

    @model_validator(mode="after")
    def validate_question(self):
        if self.type in {"SINGLE", "MULTIPLE"}:
            if not self.options or len(self.options) < 2:
                raise ValueError("choice question requires at least two options")
            if self.type == "MULTIPLE" and not isinstance(self.answer, list):
                raise ValueError("multiple choice answer must be an array")
            keys = {item.key for item in self.options}
            answers = self.answer if isinstance(self.answer, list) else [self.answer]
            if not answers or any(str(answer) not in keys for answer in answers):
                raise ValueError("answer must reference an option key")
            if self.type == "SINGLE" and len(answers) != 1:
                raise ValueError("single choice requires one answer")
        elif self.type == "JUDGE" and str(self.answer).lower() not in {"true", "false"}:
            raise ValueError("judge answer must be true or false")
        elif self.type == "SHORT" and not str(self.answer).strip():
            raise ValueError("short reference answer is required")
        return self


class QuizGenerateResponse(BaseModel):
    questions: List[GeneratedQuestion] = Field(min_length=1)


class ShortGradeRequest(BaseModel):
    question: str = Field(min_length=1)
    reference_answer: str = Field(alias="referenceAnswer", min_length=1)
    user_answer: str = Field(alias="userAnswer", min_length=1)

    model_config = {"populate_by_name": True}


class ShortGradeResponse(BaseModel):
    score: float = Field(ge=0, le=1)
    correct: bool
    feedback: str = Field(min_length=1)
