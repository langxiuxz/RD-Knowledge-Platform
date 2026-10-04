from typing import Optional

from fastapi import Header, HTTPException, status

from app.core.config import get_settings


def require_internal_token(x_internal_token: Optional[str] = Header(default=None)) -> None:
    expected = get_settings().internal_token
    if not expected or x_internal_token != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid internal token")
