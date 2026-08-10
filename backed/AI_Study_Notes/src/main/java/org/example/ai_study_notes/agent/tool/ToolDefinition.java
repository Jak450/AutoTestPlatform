package org.example.ai_study_notes.agent.tool;

import lombok.Builder;
import lombok.Data;
import org.example.ai_study_notes.agent.contract.ToolPermission;

import java.util.Map;

/**
 * 工具定义：名称/描述/参数 Schema/权限/执行器。定义与 handler 分离。
 */
@Data
@Builder
public class ToolDefinition {

    private String name;
    private String label;
    private String description;
    private Map<String, Object> inputSchema;
    private ToolPermission permission;
    private String category;
    private boolean activeByDefault;
    private String version;
    private boolean sequential;
    private ToolExecutor executor;

    public interface ToolExecutor {
        ToolDefinition definition();

        ToolResult execute(Map<String, Object> args, ToolContext context);
    }
}
