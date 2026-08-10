package org.example.ai_study_notes.agent.contract;

/**
 * SSE 事件类型，对应契约 eventTypes。
 */
public enum EventType {
    AGENT_START("agent_start"),
    TURN_START("turn_start"),
    MESSAGE_START("message_start"),
    MESSAGE_UPDATE("message_update"),
    MESSAGE_END("message_end"),
    TOOL_EXECUTION_START("tool_execution_start"),
    TOOL_EXECUTION_UPDATE("tool_execution_update"),
    TOOL_EXECUTION_END("tool_execution_end"),
    TURN_END("turn_end"),
    AGENT_END("agent_end"),
    HEARTBEAT("heartbeat"),
    ERROR("error");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
