from app.core.config import Settings
from app.llm.factory import LLMFactory, OpenAICompatibleLLM, is_remote_provider_configured


def test_deepseek_provider_is_case_insensitive():
    settings = Settings(
        llm_provider="DeepSeek",
        llm_api_key="test-key",
        llm_model="deepseek-chat",
        llm_base_url="https://api.deepseek.com",
    )

    assert is_remote_provider_configured(settings)
    assert isinstance(LLMFactory(settings).create(), OpenAICompatibleLLM)


def test_missing_deepseek_credentials_uses_local_fallback():
    settings = Settings(llm_provider="deepseek", llm_api_key="", llm_model="")

    assert not is_remote_provider_configured(settings)
    assert not isinstance(LLMFactory(settings).create(), OpenAICompatibleLLM)
