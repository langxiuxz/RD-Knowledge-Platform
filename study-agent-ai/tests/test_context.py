from app.graph.nodes.context import _select_relevant, extract_memory_candidate


def test_explicit_java_explanation_preference_is_candidate():
    candidate = extract_memory_candidate("以后解释 Java 多给代码例子", 8, 301, 9001)
    assert candidate["category"] == "EXPLANATION_PREFERENCE"
    assert candidate["memoryKey"] == "explanation_preference"
    assert candidate["value"] == "以后解释 Java 多给代码例子"


def test_explicit_preference_update_uses_same_key():
    first = extract_memory_candidate("以后解释 Java 多给代码例子", 8, 301, 9001)
    updated = extract_memory_candidate("以后解释 Java 请详细一点", 8, 302, 9002)
    assert first["memoryKey"] == updated["memoryKey"] == "explanation_preference"
    assert updated["value"] != first["value"]


def test_general_question_and_business_facts_do_not_become_memory():
    assert extract_memory_candidate("什么是Java多态？", 8, 301, 9001) is None
    assert extract_memory_candidate("以后解释时记住我的 mastery 是 80", 8, 301, 9001) is None


def test_memory_selection_is_bounded_and_relevant():
    rows = [
        {"memoryKey": "explanation_preference", "value": "以后解释 Java 多给代码例子", "updatedAt": "2026-01"},
        {"memoryKey": "explanation_python", "value": "以后解释 Python 多给例子", "updatedAt": "2026-02"},
        {"memoryKey": "learning_goal", "value": "我的学习目标是掌握后端开发", "updatedAt": "2026-03"},
    ]
    selected = _select_relevant(rows, "Java 多态是什么", 2)
    assert len(selected) <= 2
    assert selected[0]["memoryKey"] == "explanation_preference"

def test_transient_instruction_does_not_become_long_term_memory():
    assert extract_memory_candidate("请用表格总结这段内容", 8, 301, 9001) is None
    assert extract_memory_candidate("请多给一个 Java 例子", 8, 301, 9001) is None
