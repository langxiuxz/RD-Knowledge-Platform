from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app


def test_internal_health_requires_token(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    response = TestClient(app).get("/internal/health")
    assert response.status_code == 401


def test_internal_health_returns_up(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", "test-token")
    get_settings.cache_clear()
    response = TestClient(app).get("/internal/health", headers={"X-Internal-Token": "test-token"})
    assert response.status_code == 200
    assert response.json()["status"] == "UP"
