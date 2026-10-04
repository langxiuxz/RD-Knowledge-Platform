from dataclasses import dataclass
from typing import List
from app.loaders.document import DocumentPage

@dataclass(frozen=True)
class TextChunk:
    page: int
    index: int
    content: str

def split_pages(pages: List[DocumentPage], size: int = 1000, overlap: int = 100) -> List[TextChunk]:
    if size <= overlap:
        raise ValueError("chunk size must be greater than overlap")
    chunks: List[TextChunk] = []
    for page in pages:
        text = " ".join(page.text.split())
        if not text:
            continue
        start = 0
        index = 0
        while start < len(text):
            end = min(len(text), start + size)
            content = text[start:end].strip()
            if content:
                chunks.append(TextChunk(page.page, index, content))
                index += 1
            if end == len(text):
                break
            start = max(start + 1, end - overlap)
    return chunks
