from typing import Any, Dict, Optional
import httpx
from app.core.config import Settings

class SpringBootClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def _timeout(self) -> httpx.Timeout:
        return httpx.Timeout(self._settings.spring_boot_read_timeout, connect=self._settings.spring_boot_connect_timeout)

    async def health(self) -> Dict[str, Any]:
        return await self._request_json("GET", "/internal/health")

    async def download_file(self, file_url: str) -> bytes:
        async with httpx.AsyncClient(timeout=self._timeout()) as client:
            response = await client.get(file_url, headers={"X-Internal-Token": self._settings.internal_token})
            response.raise_for_status()
            return response.content

    async def document_progress(self, document_id: int, task_id: str, status: str, progress: int,
                                chunk_count: Optional[int] = None, error_message: Optional[str] = None) -> None:
        payload = {"taskId": task_id, "status": status, "progress": progress,
                   "chunkCount": chunk_count, "errorMessage": error_message}
        await self._request_json("POST", f"/internal/callbacks/documents/{document_id}/progress", payload)

    async def get_learning_progress(self, user_id: int, limit: int) -> Any:
        return await self._tool("learning-progress", user_id, limit)

    async def get_wrong_questions(self, user_id: int, limit: int) -> Any:
        return await self._tool("wrong-questions", user_id, limit)

    async def get_weak_topics(self, user_id: int, limit: int) -> Any:
        return await self._tool("weak-topics", user_id, limit)

    async def get_recent_quiz_results(self, user_id: int, limit: int) -> Any:
        return await self._tool("recent-quiz-results", user_id, limit)

    async def _tool(self, path: str, user_id: int, limit: int) -> Any:
        envelope = await self._request_json("POST", f"/internal/tools/{path}", {"userId": user_id, "limit": limit})
        return envelope.get("data")

    async def _request_json(self, method: str, path: str, payload: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        headers = {"X-Internal-Token": self._settings.internal_token}
        async with httpx.AsyncClient(base_url=self._settings.spring_boot_base_url, timeout=self._timeout()) as client:
            response = await client.request(method, path, json=payload, headers=headers)
            response.raise_for_status()
            return response.json()
