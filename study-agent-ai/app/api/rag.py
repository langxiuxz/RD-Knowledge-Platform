import logging
import hashlib
from typing import Any, Dict
from fastapi import APIRouter, BackgroundTasks, Depends, Path, Query

from app.clients.spring_boot import SpringBootClient
from app.core.config import get_settings
from app.core.security import require_internal_token
from app.embeddings.local import MODEL, embed
from app.loaders.document import load_document
from app.schemas.rag import IndexRequest, RetrieveRequest
from app.rag.vector_store import VectorRecord, vector_store
from app.splitter.text import split_pages

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/internal/rag", tags=["rag"])

def _client() -> SpringBootClient:
    return SpringBootClient(get_settings())

@router.post("/index", dependencies=[Depends(require_internal_token)])
async def index(request: IndexRequest, background_tasks: BackgroundTasks) -> Dict[str, Any]:
    background_tasks.add_task(_process_document, request)
    return {"code": 1, "msg": "success", "data": {"taskId": request.task_id}}

@router.post("/retrieve", dependencies=[Depends(require_internal_token)])
async def retrieve(request: RetrieveRequest) -> Dict[str, Any]:
    chunks = await vector_store.retrieve(request.project_id, request.knowledge_base_id,
                                         request.knowledge_snapshot_id, request.document_version_ids,
                                         request.query, request.top_k, request.score_threshold)
    return {"code": 1, "msg": "success", "data": {"chunks": [chunk.model_dump(by_alias=True) for chunk in chunks]}}

@router.delete("/document-versions/{version_id}", dependencies=[Depends(require_internal_token)])
async def delete_document_version(version_id: int = Path(gt=0, lt=2**63),
                                  projectId: int = Query(gt=0, lt=2**63),
                                  knowledgeBaseId: int = Query(gt=0, lt=2**63)) -> Dict[str, Any]:
    await vector_store.delete_document_version(projectId, knowledgeBaseId, version_id)
    return {"code": 1, "msg": "success", "data": None}

@router.delete("/knowledge-bases/{knowledge_base_id}", dependencies=[Depends(require_internal_token)])
async def delete_knowledge_base(knowledge_base_id: int = Path(gt=0, lt=2**63),
                                projectId: int = Query(gt=0, lt=2**63)) -> Dict[str, Any]:
    await vector_store.delete_knowledge_base(projectId, knowledge_base_id)
    return {"code": 1, "msg": "success", "data": None}

async def _process_document(request: IndexRequest) -> None:
    client = _client()
    try:
        await client.document_version_progress(request.document_version_id, request.task_id, "PARSING", 10)
        data = await client.download_file(request.file_url)
        if hashlib.sha256(data).hexdigest() != request.content_hash:
            raise ValueError("Document content hash mismatch")
        pages = load_document(data, request.file_type)
        await client.document_version_progress(request.document_version_id, request.task_id, "CHUNKING", 30)
        chunks = split_pages(pages)
        if not chunks:
            raise ValueError("Document contains no extractable text")
        await client.document_version_progress(request.document_version_id, request.task_id, "EMBEDDING", 60, len(chunks))
        await vector_store.delete_document_version(request.project_id, request.knowledge_base_id, request.document_version_id)
        records = []
        for global_index, chunk in enumerate(chunks):
            chunk_id = f"chunk_{request.document_version_id}_{global_index}"
            metadata = {"chunk_id": chunk_id, "project_id": request.project_id,
                        "knowledge_base_id": request.knowledge_base_id, "document_id": request.document_id,
                        "document_version_id": request.document_version_id, "content_hash": request.content_hash,
                        "file_name": request.file_name, "page": chunk.page, "chunk_index": global_index,
                        "embedding_model": MODEL}
            records.append(VectorRecord(embed(chunk.content), chunk.content, metadata))
        await vector_store.upsert(records)
        await client.document_version_progress(request.document_version_id, request.task_id, "READY", 100, len(records))
    except Exception as exc:
        logger.exception("document indexing failed document_id=%s task_id=%s", request.document_id, request.task_id)
        try:
            await client.document_version_progress(request.document_version_id, request.task_id, "FAILED", 0,
                                           error_message=str(exc)[:900] or "document processing failed")
        except Exception:
            logger.exception("document failure callback failed document_id=%s task_id=%s", request.document_id, request.task_id)
