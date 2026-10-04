from typing import AsyncIterator, Optional, Tuple
from uuid import uuid4

from langgraph.graph import END, START, StateGraph

from app.core.config import Settings
from app.graph.nodes.finalize import finalize
from app.graph.nodes.generate import GenerateAnswerNode
from app.graph.nodes.retrieve import RetrieveNode
from app.graph.nodes.router import route_after_retrieve, route_mode, router
from app.graph.nodes.tools import ToolNode, agent_reasoning
from app.graph.nodes.validate import validate_input
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.rag.vector_store import vector_store
from app.schemas.agent import AgentInvokeRequest
from app.tools.registry import create_default_registry


def build_graph(settings: Settings):
    builder = StateGraph(AgentState)
    builder.add_node("validate_input", validate_input)
    builder.add_node("router", router)
    builder.add_node("retrieve_context", RetrieveNode(vector_store, settings.rag_top_k))
    builder.add_node("agent_reasoning", agent_reasoning)
    builder.add_node("tool_node", ToolNode(create_default_registry(settings), settings.tool_max_calls))
    builder.add_node("generate_answer", GenerateAnswerNode(LLMFactory(settings).create()))
    builder.add_node("finalize", finalize)
    builder.add_edge(START, "validate_input")
    builder.add_edge("validate_input", "router")
    builder.add_conditional_edges("router", route_mode, {
        "CHAT": "generate_answer",
        "RAG": "retrieve_context",
        "AGENT": "agent_reasoning",
        "AGENT_RAG": "retrieve_context",
    })
    builder.add_conditional_edges("retrieve_context", route_after_retrieve, {
        "RAG": "generate_answer",
        "AGENT": "agent_reasoning",
    })
    builder.add_edge("agent_reasoning", "tool_node")
    builder.add_edge("tool_node", "generate_answer")
    builder.add_edge("generate_answer", "finalize")
    builder.add_edge("finalize", END)
    return builder.compile()


def initial_state(request: AgentInvokeRequest, trace_id: Optional[str] = None) -> AgentState:
    return AgentState(
        trace_id=trace_id or f"trace_{uuid4().hex}",
        user_id=request.user_id,
        conversation_id=request.conversation_id,
        message_id=request.message_id,
        content=request.content,
        mode=request.mode,
        knowledge_base_id=request.knowledge_base_id,
    )


class AgentWorkflow:
    def __init__(self, settings: Settings) -> None:
        self._graph = build_graph(settings)

    async def invoke(self, request: AgentInvokeRequest) -> AgentState:
        return await self._graph.ainvoke(initial_state(request))

    async def stream(self, request: AgentInvokeRequest) -> AsyncIterator[Tuple[str, AgentState]]:
        state = initial_state(request)
        accumulated: AgentState = AgentState(**state)
        async for update in self._graph.astream(state, stream_mode="updates"):
            for node_name, values in update.items():
                if values:
                    accumulated.update(values)
                yield node_name, AgentState(**accumulated)
