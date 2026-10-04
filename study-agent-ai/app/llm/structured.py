from typing import Type, TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


def validate_structured_output(model: Type[T], payload: object) -> T:
    return model.model_validate(payload)
