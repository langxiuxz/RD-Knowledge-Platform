from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app


def _payload():
    return {
        "assignmentId": 42,
        "pathVersion": 3,
        "knowledgeSnapshotId": 8,
        "facts": {"managerDecision": "PASS", "programScore": 86},
        "allowedSources": [{"documentVersionId": 901, "title": "故障手册"}],
    }


def _client(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "report-test-token")
    monkeypatch.setenv("LLM_PROVIDER", "none")
    get_settings.cache_clear()
    return TestClient(app)


def test_capability_summary_requires_internal_token(monkeypatch):
    response = _client(monkeypatch).post("/internal/reports/capability-summary", json=_payload())
    assert response.status_code == 401


def test_capability_summary_fallback_identifies_local_model(monkeypatch):
    response = _client(monkeypatch).post(
        "/internal/reports/capability-summary",
        headers={"X-Internal-Token": "report-test-token"},
        json=_payload(),
    )
    assert response.status_code == 200
    assert response.json()["data"]["modelName"] == "local-fallback"
    assert response.json()["data"]["recommendedSources"] == []


def test_capability_summary_uses_configured_model_and_allowed_sources(monkeypatch):
    class FakeLLM:
        async def generate(self, _prompt):
            return '{"summary":"结论","strengths":["定位清楚"],"risks":[],"recommendedSources":[{"documentVersionId":901,"title":"故障手册"}]}'

    class FakeFactory:
        def __init__(self, _settings):
            pass

        def create(self):
            return FakeLLM()

    monkeypatch.setenv("INTERNAL_TOKEN", "report-test-token")
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.setenv("LLM_MODEL", "test-report-model")
    get_settings.cache_clear()
    monkeypatch.setattr("app.api.report.LLMFactory", FakeFactory)
    response = TestClient(app).post(
        "/internal/reports/capability-summary",
        headers={"X-Internal-Token": "report-test-token"},
        json=_payload(),
    )
    assert response.status_code == 200
    assert response.json()["data"]["modelName"] == "test-report-model"
    assert response.json()["data"]["recommendedSources"] == _payload()["allowedSources"]


def test_capability_summary_rejects_recommendation_outside_fixed_snapshot(monkeypatch):
    class FakeLLM:
        async def generate(self, _prompt):
            return '{"summary":"结论","recommendedSources":[{"documentVersionId":999,"title":"未发布资料"}]}'

    class FakeFactory:
        def __init__(self, _settings):
            pass

        def create(self):
            return FakeLLM()

    monkeypatch.setenv("INTERNAL_TOKEN", "report-test-token")
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.setenv("LLM_MODEL", "test-report-model")
    get_settings.cache_clear()
    monkeypatch.setattr("app.api.report.LLMFactory", FakeFactory)
    response = TestClient(app).post(
        "/internal/reports/capability-summary",
        headers={"X-Internal-Token": "report-test-token"},
        json=_payload(),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "STRUCTURED_OUTPUT_INVALID"


def test_capability_summary_rejects_unstructured_output(monkeypatch):
    class FakeLLM:
        async def generate(self, _prompt):
            return "not json"

    class FakeFactory:
        def __init__(self, _settings):
            pass

        def create(self):
            return FakeLLM()

    monkeypatch.setenv("INTERNAL_TOKEN", "report-test-token")
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.setenv("LLM_MODEL", "test-report-model")
    get_settings.cache_clear()
    monkeypatch.setattr("app.api.report.LLMFactory", FakeFactory)
    response = TestClient(app).post(
        "/internal/reports/capability-summary",
        headers={"X-Internal-Token": "report-test-token"},
        json=_payload(),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "STRUCTURED_OUTPUT_INVALID"
