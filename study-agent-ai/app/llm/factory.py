from abc import ABC, abstractmethod

from app.core.config import Settings


class LLM(ABC):
    @abstractmethod
    async def generate(self, prompt: str) -> str:
        raise NotImplementedError


class NoopLLM(LLM):
    async def generate(self, prompt: str) -> str:
        return ""


class LLMFactory:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    def create(self) -> LLM:
        # Phase 0 deliberately does not initialize a provider or require an API key.
        return NoopLLM()
