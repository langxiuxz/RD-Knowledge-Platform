from dataclasses import dataclass
from io import BytesIO
from typing import List
import re
import zipfile
import xml.etree.ElementTree as ET

@dataclass(frozen=True)
class DocumentPage:
    page: int
    text: str

def load_document(data: bytes, file_type: str) -> List[DocumentPage]:
    if file_type in {"txt", "md"}:
        return [DocumentPage(1, data.decode("utf-8-sig", errors="replace"))]
    if file_type == "docx":
        return [DocumentPage(1, _load_docx(data))]
    if file_type == "pdf":
        try:
            from pypdf import PdfReader
        except ImportError as exc:
            raise ValueError("PDF parser dependency is not installed") from exc
        pages = []
        for index, page in enumerate(PdfReader(BytesIO(data)).pages, start=1):
            pages.append(DocumentPage(index, page.extract_text() or ""))
        return pages or [DocumentPage(1, "")]
    raise ValueError("Unsupported document type")

def _load_docx(data: bytes) -> str:
    with zipfile.ZipFile(BytesIO(data)) as archive:
        xml = archive.read("word/document.xml")
    root = ET.fromstring(xml)
    text = " ".join(node.text or "" for node in root.iter() if node.tag.endswith("}t"))
    return re.sub(r"\s+", " ", text).strip()
