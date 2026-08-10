package org.example.ai_study_notes.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 模型层内部消息：支持 DeepSeek thinking 模式的 reasoning_content 回传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {

    /**
     * system / user / assistant / tool
     */
    private String role;

    private String content;

    /**
     * tool 角色的工具调用 ID
     */
    private String toolCallId;

    /**
     * assistant 角色的工具调用列表
     */
    private List<ToolCall> toolCalls;

    /**
     * assistant 角色的思考内容（DeepSeek thinking 模式必须原样回传）
     */
    private String reasoningContent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String name;
        private String arguments;
    }
}
