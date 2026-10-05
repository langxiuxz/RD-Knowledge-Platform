import re
from typing import Any

from app.clients.spring_boot import SpringBootClient
from app.core.config import Settings
from app.graph.state import AgentState

_FACT_TERMS = re.compile(r"(?i)(mastery|correct\s*rate|quiz|测验|错题|掌握度|正确率|分数|测验成绩)")
_STABLE = re.compile(r"(以后|今后|从现在起|请记住|记住|我的学习目标|我想系统学习|我希望长期学习|我喜欢|我更喜欢)")


def extract_memory_candidate(text: str, user_id: int, conversation_id: int, message_id: int) -> dict[str, Any] | None:
    text = text.strip()
    if not text or len(text) > 500 or not _STABLE.search(text) or _FACT_TERMS.search(text):
        return None
    if "解释" in text and any(cue in text for cue in ("以后", "今后", "请记住", "记住", "从现在起")):
        key = "explanation_preference"
        category = "EXPLANATION_PREFERENCE"
    elif any(cue in text for cue in ("学习目标", "我想学")):
        key, category = "learning_goal", "LEARNING_GOAL"
    elif any(cue in text for cue in ("我喜欢", "我更喜欢", "请记住", "以后", "今后", "从现在起")):
        key, category = "learning_style", "LEARNING_STYLE"
    else:
        return None
    return {"userId": user_id, "conversationId": conversation_id, "messageId": message_id,
            "category": category, "memoryKey": key, "value": text, "evidenceText": text}


def _select_relevant(rows: Any, query: str, limit: int = 5) -> list[dict[str, Any]]:
    if not isinstance(rows, list):
        return []
    terms = set(re.findall(r"[a-zA-Z0-9+#.]+|[\u4e00-\u9fff]{2,}", query.lower()))
    scored = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        key = str(row.get("memoryKey", ""))
        value = str(row.get("value", ""))
        searchable = f"{key} {value}".lower()
        score = sum(term in searchable for term in terms)
        if score or key in {"learning_goal", "learning_style"} or key == "explanation_preference":
            scored.append((score, str(row.get("updatedAt", "")), row))
    scored.sort(key=lambda item: (item[0], item[1]), reverse=True)
    return [row for _, _, row in scored[:limit]]


def build_context(settings: Settings):
    client = SpringBootClient(settings)

    async def node(state: AgentState) -> dict:
        candidate = extract_memory_candidate(state["content"], state["user_id"],
                                             state["conversation_id"], state["message_id"])
        # Memory is opportunistic; it must never make CHAT/RAG/AGENT unavailable.
        if candidate:
            try:
                await client.store_memory_candidate(candidate)
            except Exception:
                pass
        try:
            memories = await client.query_memories(state["user_id"], state["content"], 20)
        except Exception:
            memories = []
        return {"long_term_memories": _select_relevant(memories, state["content"])}

    return node
