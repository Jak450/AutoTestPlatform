package org.example.ai_study_notes.agent.contract;

/**
 * 终止原因，对应契约 stopReasons。
 */
public enum StopReason {
    STOP("stop"),
    LENGTH("length"),
    TOOL_USE("toolUse"),
    ERROR("error"),
    ABORTED("aborted"),
    TOKEN_CAPPED("token_capped"),
    LOOP_CAPPED("loop_capped"),
    TURN_CAPPED("turn_capped"),
    AWAITING_CONFIRMATION("awaiting_confirmation");

    private final String value;

    StopReason(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
