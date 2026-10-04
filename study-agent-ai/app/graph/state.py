from typing import Optional, TypedDict


class AgentState(TypedDict, total=False):
    user_id: int
    conversation_id: int
    message_id: int
    content: str
    mode: str
    knowledge_base_id: Optional[int]
    answer: str
    trace_id: str
