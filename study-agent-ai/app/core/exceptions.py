from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse


class AIServiceError(Exception):
    def __init__(self, message: str, code: str = "AI_SERVICE_ERROR") -> None:
        super().__init__(message)
        self.code = code


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AIServiceError)
    async def handle_ai_error(_: Request, exc: AIServiceError) -> JSONResponse:
        return JSONResponse(status_code=503, content={"code": exc.code, "msg": str(exc), "data": None})

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, __: Exception) -> JSONResponse:
        return JSONResponse(status_code=500, content={"code": "AI_INTERNAL_ERROR", "msg": "AI service error", "data": None})
