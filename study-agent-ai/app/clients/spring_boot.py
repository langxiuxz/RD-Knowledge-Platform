from typing import Any, Dict

import httpx

from app.core.config import Settings


class SpringBootClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def health(self) -> Dict[str, Any]:
        headers = {"X-Internal-Token": self._settings.internal_token}
        async with httpx.AsyncClient(
            base_url=self._settings.spring_boot_base_url,
            timeout=httpx.Timeout(self._settings.spring_boot_read_timeout, connect=self._settings.spring_boot_connect_timeout),
        ) as client:
            response = await client.get("/internal/health", headers=headers)
            response.raise_for_status()
            return response.json()
