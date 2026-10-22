from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app


def _request(monkeypatch, payload):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    return TestClient(app).post(
        "/internal/drills/normalize-action",
        headers={"X-Internal-Token": "test-token"},
        json=payload,
    )


def _payload():
    return {
        "scenarioId": 12,
        "actionText": "检查消费组 lag 和异常分区",
        "candidateRules": [{
            "code": "CHECK_CONSUMER_LAG",
            "title": "查看消费延迟",
            "aliases": ["查看 consumer lag"],
        }],
    }


def test_normalize_action_requires_internal_token(monkeypatch):
    response = TestClient(app).post("/internal/drills/normalize-action", json=_payload())
    assert response.status_code == 401


def test_normalize_action_returns_unmatched_without_configured_model(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "none")
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    response = _request(monkeypatch, _payload())
    assert response.status_code == 200
    assert response.json()["data"]["matchedRuleCode"] is None
    assert "score" not in response.json()["data"]


def test_normalize_action_rejects_rule_outside_candidate_set(monkeypatch):
    class FakeLlm:
        async def generate(self, _prompt):
            return (
                '{"normalizedAction":"INVENTED_RULE","matchedRuleCode":"INVENTED_RULE",'
                '"confidence":0.99,"explanation":"not allowed"}'
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
    monkeypatch.setattr("app.api.drill.LLMFactory", FakeFactory)

    response = TestClient(app).post(
        "/internal/drills/normalize-action",
        headers={"X-Internal-Token": "test-token"},
        json=_payload(),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "STRUCTURED_OUTPUT_INVALID"


def test_normalize_action_rejects_ai_score_field(monkeypatch):
    class FakeLlm:
        async def generate(self, _prompt):
            return (
                '{"normalizedAction":"CHECK_CONSUMER_LAG",'
                '"matchedRuleCode":"CHECK_CONSUMER_LAG","confidence":0.9,'
                '"explanation":"matched","points":100}'
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
    monkeypatch.setattr("app.api.drill.LLMFactory", FakeFactory)

    response = TestClient(app).post(
        "/internal/drills/normalize-action",
        headers={"X-Internal-Token": "test-token"},
        json=_payload(),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "STRUCTURED_OUTPUT_INVALID"


def test_normalize_action_rejects_duplicate_candidate_codes(monkeypatch):
    payload = _payload()
    payload["candidateRules"].append(payload["candidateRules"][0])
    response = _request(monkeypatch, payload)
    assert response.status_code == 422
