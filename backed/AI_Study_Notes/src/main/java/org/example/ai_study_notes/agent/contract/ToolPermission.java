package org.example.ai_study_notes.agent.contract;

/**
 * 工具权限级别，对应契约 permissions。
 */
public enum ToolPermission {
    READ("read"),
    CONFIRM_WRITE("confirm_write"),
    CONFIRM_EXECUTE("confirm_execute"),
    DENY("deny");

    private final String value;

    ToolPermission(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean requiresConfirmation() {
        return this == CONFIRM_WRITE || this == CONFIRM_EXECUTE;
    }
}
