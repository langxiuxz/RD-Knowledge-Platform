import hashlib
import math
from typing import List

DIMENSION = 64

def embed(text: str) -> List[float]:
    values = [0.0] * DIMENSION
    for token in text.lower().split():
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        for i in range(0, len(digest), 2):
            index = int.from_bytes(digest[i:i+2], "big") % DIMENSION
            values[index] += 1.0 if digest[i] % 2 else -1.0
    norm = math.sqrt(sum(value * value for value in values))
    return [value / norm for value in values] if norm else values
