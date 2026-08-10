package org.example.ai_study_notes.agent.tool;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行上下文：绑定当前用户与会话。
 */
@Data
@Builder
public class ToolContext {

    private Long userId;
    private Long conversationId;
    private String toolCallId;
}
