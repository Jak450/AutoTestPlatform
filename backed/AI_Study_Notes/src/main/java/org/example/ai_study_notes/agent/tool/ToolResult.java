package org.example.ai_study_notes.agent.tool;

import lombok.Builder;
import lombok.Data;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;

import java.util.Map;

/**
 * 工具执行结果，携带结构化元数据，前端按元数据渲染。
 */
@Data
@Builder
public class ToolResult {

    private String toolName;
    private ToolResultMeta.Status status;
    private ToolResultMeta.ErrorType errorType;
    private ToolResultMeta.RecommendedNextAction recommendedNextAction;
    private ToolResultMeta.Source source;
    private Object data;
    private String message;
    private long durationMs;
    private boolean requiresConfirmation;
    private Long confirmationId;

    public static ToolResult success(String toolName, Object data, String message) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ToolResultMeta.Status.SUCCESS)
                .recommendedNextAction(ToolResultMeta.RecommendedNextAction.CONTINUE)
                .source(ToolResultMeta.Source.TOOL_RETURN)
                .data(data)
                .message(message)
                .build();
    }

    public static ToolResult error(String toolName, String message,
                                   ToolResultMeta.ErrorType errorType,
                                   ToolResultMeta.RecommendedNextAction nextAction,
                                   long startMillis) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ToolResultMeta.Status.ERROR)
                .errorType(errorType)
                .recommendedNextAction(nextAction)
                .source(ToolResultMeta.Source.EXCEPTION)
                .message(message)
                .durationMs(System.currentTimeMillis() - startMillis)
                .build();
    }

    public static ToolResult unknownTool(String toolName, long startMillis) {
        return error(toolName, "未注册的工具: " + toolName,
                ToolResultMeta.ErrorType.CONFIG,
                ToolResultMeta.RecommendedNextAction.STOP,
                startMillis);
    }

    public static ToolResult requiresConfirmation(String toolName, Map<String, Object> args) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ToolResultMeta.Status.PARTIAL_SUCCESS)
                .recommendedNextAction(ToolResultMeta.RecommendedNextAction.STOP)
                .source(ToolResultMeta.Source.TOOL_RETURN)
                .data(args)
                .message("需要用户确认")
                .requiresConfirmation(true)
                .build();
    }

    public ToolResult withConfirmationId(Long confirmationId) {
        this.confirmationId = confirmationId;
        return this;
    }

    public ToolResult withDuration(long startMillis) {
        this.durationMs = System.currentTimeMillis() - startMillis;
        return this;
    }
}
