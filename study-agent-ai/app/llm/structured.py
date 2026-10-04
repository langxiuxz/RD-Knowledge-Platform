import json
import re
from typing import Type, TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


def validate_structured_output(model: Type[T], payload: object) -> T:
    return model.model_validate(payload)


def parse_structured_output(model: Type[T], text: str) -> T:
    cleaned = text.strip()
    fenced = re.fullmatch(r"```(?:json)?\s*(.*?)\s*```", cleaned, re.DOTALL | re.IGNORECASE)
    if fenced:
        cleaned = fenced.group(1)
    return validate_structured_output(model, json.loads(cleaned))
