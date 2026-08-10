package org.example.ai_study_notes.agent.contract;

/**
 * 消息类型，对应契约 messageTypes。
 */
public enum MessageType {
    TEXT("text"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
    FILE("file"),
    CONFIRMATION("confirmation"),
    CASE_PREVIEW("case_preview"),
    SYSTEM("system");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MessageType from(String value) {
        for (MessageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return TEXT;
    }
}
