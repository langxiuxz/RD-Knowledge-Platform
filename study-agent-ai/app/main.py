from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import agent, health, plan, quiz, rag
from app.core.config import get_settings
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging


@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_logging()
    yield


settings = get_settings()
app = FastAPI(title=settings.app_name, version="0.0.1", lifespan=lifespan)
register_exception_handlers(app)
app.include_router(health.router)
app.include_router(agent.router)
app.include_router(rag.router)
app.include_router(quiz.router)
app.include_router(plan.router)
