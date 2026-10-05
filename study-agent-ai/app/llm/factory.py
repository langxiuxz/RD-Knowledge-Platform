from abc import ABC, abstractmethod

import httpx

from app.core.config import Settings


class LLM(ABC):
    @abstractmethod
    async def generate(self, prompt: str) -> str:
        raise NotImplementedError


class LocalFallbackLLM(LLM):
    async def generate(self, prompt: str) -> str:
        marker = "QUESTION:\n"
        question = prompt.split(marker, 1)[-1].split("\n", 1)[0].strip() if marker in prompt else prompt.strip()
        if "RAG_CONTEXT:" in prompt:
            context = prompt.split("RAG_CONTEXT:\n", 1)[-1].strip()
            if context:
                return f"根据知识库资料，关于“{question}”：\n{context[:600]}"
        if "TOOL_RESULTS:" in prompt:
            results = prompt.split("TOOL_RESULTS:\n", 1)[-1].strip()
            return f"根据你的学习数据，关于“{question}”，建议优先关注以下信息：\n{results[:800]}"
        return f"已收到你的问题：“{question}”。当前为本地开发模型，可在配置真实 LLM 后获得更完整的回答。"


class OpenAICompatibleLLM(LLM):
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def generate(self, prompt: str) -> str:
        headers = {"Authorization": f"Bearer {self._settings.llm_api_key}"}
        payload = {
            "model": self._settings.llm_model,
            "messages": [
                {"role": "system", "content": "You are StudyAgent. Answer accurately and concisely."},
                {"role": "user", "content": prompt},
            ],
            "stream": False,
        }
        timeout = httpx.Timeout(self._settings.llm_timeout)
        base_url = (self._settings.llm_base_url or "").rstrip("/")
        if not base_url:
            raise ValueError("LLM_BASE_URL is required for a remote LLM provider")
        async with httpx.AsyncClient(base_url=base_url, timeout=timeout) as client:
            response = await client.post("/chat/completions", headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
        return str(data["choices"][0]["message"]["content"]).strip()


REMOTE_PROVIDERS = {"openai", "openai-compatible", "deepseek"}


def is_remote_provider_configured(settings: Settings) -> bool:
    provider = (settings.llm_provider or "").strip().lower()
    return provider in REMOTE_PROVIDERS and bool(settings.llm_api_key and settings.llm_model)


class LLMFactory:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    def create(self) -> LLM:
        if is_remote_provider_configured(self.settings):
            return OpenAICompatibleLLM(self.settings)
        return LocalFallbackLLM()
