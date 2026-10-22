import asyncio
import logging

from typing import Any, Dict, Optional
import httpx
from app.core.config import Settings

logger = logging.getLogger(__name__)


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
        for attempt in range(1, self._settings.document_callback_attempts + 1):
            try:
                envelope = await self._request_json(
                    "POST", f"/internal/callbacks/documents/{document_id}/progress", payload)
                if envelope.get("code") != 1:
                    raise ValueError("Document progress callback was not acknowledged")
                return
            except (httpx.TransportError, httpx.HTTPStatusError) as exc:
                # Authentication, missing resources and invalid transitions need intervention,
                # not repeated delivery. Only temporary transport/server failures are retried.
                retryable = isinstance(exc, httpx.TransportError) or (
                    exc.response.status_code in (408, 429) or exc.response.status_code >= 500)
                if not retryable or attempt == self._settings.document_callback_attempts:
                    raise
                logger.warning("Retrying document callback document_id=%s task_id=%s status=%s attempt=%s",
                               document_id, task_id, status, attempt)
                await asyncio.sleep(min(self._settings.document_callback_retry_delay * 2 ** (attempt - 1), 5.0))

    async def document_version_progress(self, version_id: int, task_id: str, status: str, progress: int,
                                        chunk_count: Optional[int] = None, error_message: Optional[str] = None) -> None:
        payload = {"taskId": task_id, "status": status, "progress": progress,
                   "chunkCount": chunk_count, "errorMessage": error_message}
        for attempt in range(1, self._settings.document_callback_attempts + 1):
            try:
                envelope = await self._request_json(
                    "POST", f"/internal/callbacks/document-versions/{version_id}/progress", payload)
                if envelope.get("code") != 1:
                    raise ValueError("Document version progress callback was not acknowledged")
                return
            except (httpx.TransportError, httpx.HTTPStatusError) as exc:
                retryable = isinstance(exc, httpx.TransportError) or (
                    exc.response.status_code in (408, 429) or exc.response.status_code >= 500)
                if not retryable or attempt == self._settings.document_callback_attempts:
                    raise
                logger.warning("Retrying version callback version_id=%s task_id=%s status=%s attempt=%s",
                               version_id, task_id, status, attempt)
                await asyncio.sleep(min(self._settings.document_callback_retry_delay * 2 ** (attempt - 1), 5.0))

    async def get_learner_profile(self, user_id: int) -> Any:
        envelope = await self._request_json("POST", "/internal/tools/learner-profile", {"userId": user_id, "limit": 1})
        return envelope.get("data")

    async def query_memories(self, user_id: int, query: str, limit: int = 20) -> Any:
        envelope = await self._request_json("POST", "/internal/tools/memories/query",
                                            {"userId": user_id, "query": query[:1000], "limit": limit})
        return envelope.get("data")

    async def store_memory_candidate(self, candidate: Dict[str, Any]) -> bool:
        envelope = await self._request_json("POST", "/internal/tools/memories/candidates", candidate)
        return bool(envelope.get("data"))

    async def get_learning_progress(self, user_id: int, limit: int) -> Any:
        return await self._tool("learning-progress", user_id, limit)

    async def get_topic_progress(self, user_id: int, topic: str) -> Any:
        # Reuse the frozen learning-progress Bridge contract; do not add query fields.
        rows = await self.get_learning_progress(user_id, 20)
        needle = topic.casefold()
        return [row for row in rows or [] if needle in str(row.get("topic", "")).casefold()]

    async def get_wrong_questions(self, user_id: int, limit: int, topic: Optional[str] = None) -> Any:
        # Topic filtering is applied to the existing user-scoped result, preserving API.md.
        rows = await self._tool("wrong-questions", user_id, 20 if topic else limit)
        if topic:
            needle = topic.casefold()
            rows = [row for row in rows or [] if needle in str(row.get("topic", "")).casefold()]
        return (rows or [])[:limit]

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
