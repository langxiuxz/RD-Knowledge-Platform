from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app


def _headers(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    monkeypatch.setenv("LLM_PROVIDER", "none")
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    get_settings.cache_clear()
    return {"X-Internal-Token": "test-token"}


def test_generate_quiz_returns_valid_structured_questions(monkeypatch):
    response = TestClient(app).post("/internal/quiz/generate", headers=_headers(monkeypatch), json={
        "userId": 1,
        "topic": "Spring AOP",
        "questionCount": 4,
        "difficulty": "MEDIUM",
        "types": ["SINGLE", "MULTIPLE", "JUDGE", "SHORT"],
    })

    assert response.status_code == 200
    questions = response.json()["data"]["questions"]
    assert [item["type"] for item in questions] == ["SINGLE", "MULTIPLE", "JUDGE", "SHORT"]
    assert questions[1]["answer"] == ["A", "B"]


def test_generate_quiz_rejects_invalid_structured_output(monkeypatch):
    class FakeLlm:
        async def generate(self, _prompt):
            return (
                '{"questions":[{"type":"MULTIPLE","content":"bad","options":'
                '[{"key":"A","text":"a"},{"key":"B","text":"b"}],"answer":"A"}]}'
            )

    class FakeFactory:
        def __init__(self, _settings):
            pass

        def create(self):
            return FakeLlm()

    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.setenv("LLM_MODEL", "test-model")
    get_settings.cache_clear()
    monkeypatch.setattr("app.api.quiz.LLMFactory", FakeFactory)

    response = TestClient(app).post("/internal/quiz/generate",
        headers={"X-Internal-Token": "test-token"}, json={
            "userId": 1, "questionCount": 1, "difficulty": "EASY", "types": ["MULTIPLE"]
        })

    assert response.status_code == 422
    assert response.json()["code"] == "STRUCTURED_OUTPUT_INVALID"


def test_grade_short_uses_threshold(monkeypatch):
    response = TestClient(app).post("/internal/quiz/grade-short", headers=_headers(monkeypatch), json={
        "question": "什么是 IOC？",
        "referenceAnswer": "对象由容器负责创建和管理",
        "userAnswer": "对象由容器负责创建和管理",
    })

    assert response.status_code == 200
    assert response.json()["data"]["score"] == 1.0
    assert response.json()["data"]["correct"] is True


def test_generate_plan_returns_tasks_in_requested_range(monkeypatch):
    response = TestClient(app).post("/internal/plans/generate", headers=_headers(monkeypatch), json={
        "userId": 1,
        "goal": "复习 Java Web",
        "startDate": "2026-10-05",
        "endDate": "2026-10-07",
        "dailyMinutes": 60,
        "profile": {"weakTopics": ["MyBatis", "Spring AOP"]},
    })

    assert response.status_code == 200
    data = response.json()["data"]
    assert len(data["tasks"]) == 3
    assert all(task["estimatedMinutes"] == 60 for task in data["tasks"])
    assert data["tasks"][0]["date"] == "2026-10-05"
