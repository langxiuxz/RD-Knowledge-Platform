import json
from difflib import SequenceMatcher

from fastapi import APIRouter, Depends
from pydantic import ValidationError

from app.core.config import get_settings
from app.core.exceptions import AIServiceError
from app.core.security import require_internal_token
from app.llm.factory import LLMFactory, is_remote_provider_configured
from app.llm.structured import parse_structured_output
from app.rag.vector_store import vector_store
from app.schemas.quiz import (
    GeneratedQuestion,
    QuizGenerateRequest,
    QuizGenerateResponse,
    QuizOption,
    ShortGradeRequest,
    ShortGradeResponse,
)

router = APIRouter(prefix="/internal/quiz", tags=["quiz"])


@router.post("/generate", dependencies=[Depends(require_internal_token)])
async def generate(request: QuizGenerateRequest) -> dict:
    try:
        result = await _generate(request)
        if len(result.questions) != request.question_count:
            raise ValueError("generated question count does not match request")
        if any(question.type not in request.types for question in result.questions):
            raise ValueError("generated question type is outside requested types")
    except (ValueError, ValidationError, json.JSONDecodeError) as exc:
        raise AIServiceError("模型结构化输出校验失败", "STRUCTURED_OUTPUT_INVALID") from exc
    return {"code": 1, "msg": "success", "data": result.model_dump(by_alias=True)}


@router.post("/grade-short", dependencies=[Depends(require_internal_token)])
async def grade_short(request: ShortGradeRequest) -> dict:
    try:
        result = await _grade_short(request)
    except (ValueError, ValidationError, json.JSONDecodeError) as exc:
        raise AIServiceError("模型结构化输出校验失败", "STRUCTURED_OUTPUT_INVALID") from exc
    return {"code": 1, "msg": "success", "data": result.model_dump(by_alias=True)}


async def _generate(request: QuizGenerateRequest) -> QuizGenerateResponse:
    settings = get_settings()
    chunks = []
    if request.project_id is not None:
        chunks = await vector_store.retrieve(
            request.project_id, request.knowledge_base_id, request.knowledge_snapshot_id,
            request.document_version_ids, request.topic or "quiz", min(settings.rag_top_k, 8), None,
        )
        if not chunks:
            raise ValueError("no content found in the published knowledge snapshot")
    if not is_remote_provider_configured(settings):
        return _local_quiz(request, chunks)
    context = ""
    if request.project_id is None and request.knowledge_base_id is not None:
        chunks = await vector_store.retrieve(
            request.user_id, request.knowledge_base_id, request.topic or "quiz",
            min(settings.rag_top_k, 8), None,
        )
    context = "\n".join(chunk.content for chunk in chunks)
    refs = [{"documentVersionId": chunk.document_version_id,
             "knowledgeSnapshotId": chunk.knowledge_snapshot_id,
             "chunkId": chunk.chunk_id, "documentId": chunk.document_id,
             "fileName": chunk.file_name, "page": chunk.page} for chunk in chunks]
    prompt = (
        "Return JSON only with shape {\"questions\":[{\"type\":...,\"topic\":...,\"knowledgeKey\":...,"
        "\"content\":...,\"options\":[{\"key\":...,\"text\":...}],\"answer\":...,"
        "\"explanation\":...,\"sourceRefs\":...}]}. Use only provided context; each question must cite "
        "one or more supplied source references and use a stable knowledgeKey. Generate exactly " + str(request.question_count)
        + f" questions. Allowed types: {request.types}. Difficulty: {request.difficulty}. "
        + f"Topic: {request.topic or 'general study'}. Context: {context[:5000]} References: {json.dumps(refs, ensure_ascii=False)}"
    )
    text = await LLMFactory(settings).create().generate(prompt)
    result = parse_structured_output(QuizGenerateResponse, text)
    if request.project_id is not None:
        allowed = set(request.document_version_ids)
        for question in result.questions:
            if not question.knowledge_key or not question.source_refs:
                raise ValueError("V3 questions require knowledgeKey and sourceRefs")
            if any(ref.get("documentVersionId") not in allowed or
                   ref.get("knowledgeSnapshotId") != request.knowledge_snapshot_id for ref in question.source_refs):
                raise ValueError("question source is outside the supplied snapshot")
    return result


async def _grade_short(request: ShortGradeRequest) -> ShortGradeResponse:
    settings = get_settings()
    if not is_remote_provider_configured(settings):
        ratio = SequenceMatcher(None, request.reference_answer.lower(), request.user_answer.lower()).ratio()
        score = round(ratio, 4)
        return ShortGradeResponse(score=score, correct=score >= 0.6,
                                  feedback="答案基本正确" if score >= 0.6 else "请补充关键概念后重试")
    prompt = (
        "Grade the short answer. Return JSON only: {\"score\":0..1,\"correct\":boolean,"
        "\"feedback\":string}. correct must equal score >= 0.6.\n"
        f"Question: {request.question}\nReference: {request.reference_answer}\nAnswer: {request.user_answer}"
    )
    result = parse_structured_output(ShortGradeResponse,
                                     await LLMFactory(settings).create().generate(prompt))
    if result.correct != (result.score >= 0.6):
        raise ValueError("correct does not match threshold")
    return result


def _local_quiz(request: QuizGenerateRequest, chunks=()) -> QuizGenerateResponse:
    questions = []
    topic = request.topic or "综合知识"
    for index in range(request.question_count):
        question_type = request.types[index % len(request.types)]
        if question_type == "SINGLE":
            question = GeneratedQuestion(type=question_type, topic=topic,
                content=f"{topic} 基础概念题 {index + 1}",
                options=[QuizOption(key="A", text="正确描述"), QuizOption(key="B", text="错误描述")],
                answer="A", explanation="A 为符合该知识点的描述。")
        elif question_type == "MULTIPLE":
            question = GeneratedQuestion(type=question_type, topic=topic,
                content=f"{topic} 多选题 {index + 1}",
                options=[QuizOption(key="A", text="正确要点一"), QuizOption(key="B", text="正确要点二"),
                         QuizOption(key="C", text="干扰项")], answer=["A", "B"],
                explanation="A、B 均为正确要点。")
        elif question_type == "JUDGE":
            question = GeneratedQuestion(type=question_type, topic=topic,
                content=f"{topic} 判断题 {index + 1}",
                options=[QuizOption(key="true", text="正确"), QuizOption(key="false", text="错误")],
                answer="true", explanation="该陈述正确。")
        else:
            question = GeneratedQuestion(type=question_type, topic=topic,
                content=f"请简述 {topic} 的核心概念。", options=None,
                answer=f"说明 {topic} 的定义、作用和关键点。", explanation="覆盖核心定义和作用即可。")
        if request.project_id is not None:
            chunk = chunks[index % len(chunks)]
            question.knowledge_key = (request.topic or "general").strip().lower().replace(" ", ".")[:128]
            question.source_refs = [{"documentVersionId": chunk.document_version_id,
                "knowledgeSnapshotId": chunk.knowledge_snapshot_id, "chunkId": chunk.chunk_id,
                "documentId": chunk.document_id, "fileName": chunk.file_name, "page": chunk.page}]
        questions.append(question)
    return QuizGenerateResponse(questions=questions)
