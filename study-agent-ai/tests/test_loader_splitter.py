from io import BytesIO
import zipfile

from pypdf import PdfWriter

from app.loaders.document import load_document
from app.splitter.text import split_pages


def test_text_loader_and_chunk_metadata():
    pages = load_document(("a " * 1200).encode("utf-8"), "txt")
    chunks = split_pages(pages, size=100, overlap=10)

    assert pages[0].page == 1
    assert len(chunks) > 1
    assert chunks[0].page == 1
    assert chunks[0].index == 0
    assert chunks[0].content


def test_markdown_loader():
    pages = load_document("# Title\n\ncontent".encode("utf-8"), "md")
    assert pages[0].text == "# Title\n\ncontent"


def test_invalid_type_rejected():
    try:
        load_document(b"data", "exe")
    except ValueError as exc:
        assert "Unsupported" in str(exc)
    else:
        raise AssertionError("unsupported document type was accepted")


def test_docx_loader_reads_document_text():
    buffer = BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("word/document.xml", (
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            '<w:body><w:p><w:r><w:t>StudyAgent DOCX</w:t></w:r></w:p></w:body></w:document>'
        ))

    pages = load_document(buffer.getvalue(), "docx")

    assert pages[0].text == "StudyAgent DOCX"


def test_pdf_loader_accepts_valid_pdf():
    buffer = BytesIO()
    writer = PdfWriter()
    writer.add_blank_page(width=100, height=100)
    writer.write(buffer)

    pages = load_document(buffer.getvalue(), "pdf")

    assert len(pages) == 1
    assert pages[0].page == 1
