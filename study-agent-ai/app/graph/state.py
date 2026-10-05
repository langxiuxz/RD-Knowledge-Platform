from typing import Any, Dict, List, Optional, TypedDict


class AgentState(TypedDict, total=False):
    trace_id: str
    user_id: int
    conversation_id: int
    message_id: int
    content: str
    mode: str
    knowledge_base_id: Optional[int]
    messages: List[Dict[str, str]]
    retrieved_chunks: List[Dict[str, Any]]
    pending_tool_calls: List[Dict[str, Any]]
    agent_steps: int
    tool_calls: List[Dict[str, Any]]
    recent_tool_calls: List[Dict[str, Any]]
    answer: str
    citations: List[Dict[str, Any]]
    error: Optional[str]
