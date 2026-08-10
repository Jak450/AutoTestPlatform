package org.example.ai_study_notes.agent.contract;

import java.util.List;

/**
 * Agent 契约常量，与 docs/agent-design/contracts/agent-contracts.json 保持一致。
 * 契约文件是唯一事实源，此类用于代码侧枚举比对（CONTRACT-1）。
 */
public final class AgentContract {

    public static final String CONTRACT_VERSION = "1.0.0";

    public static final List<String> MESSAGE_TYPES = List.of(
            "text", "tool_call", "tool_result", "file", "confirmation", "case_preview", "system");

    public static final List<String> EVENT_TYPES = List.of(
            "agent_start", "turn_start", "message_start", "message_update", "message_end",
            "tool_execution_start", "tool_execution_update", "tool_execution_end",
            "turn_end", "agent_end", "heartbeat");

    public static final List<String> TERMINAL_EVENTS = List.of("agent_end");

    public static final List<String> STOP_REASONS = List.of(
            "stop", "length", "toolUse", "error", "aborted",
            "token_capped", "loop_capped", "turn_capped", "awaiting_confirmation");

    public static final List<String> PERMISSIONS = List.of("read", "confirm_write", "confirm_execute", "deny");

    public static final List<String> ROLES = List.of("user", "admin");

    public static final int SESSION_RETENTION_DAYS = 7;

    public static final boolean SESSION_BRANCHING = false;

    public static final List<String> FILE_ALLOWED_EXTENSIONS = List.of(".md", ".pdf", ".doc", ".docx", ".txt");

    public static final long FILE_MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private AgentContract() {
    }
}
