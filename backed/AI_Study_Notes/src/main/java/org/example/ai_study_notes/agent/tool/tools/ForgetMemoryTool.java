package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.memory.MemoryService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 删除长期记忆（需用户确认）。
 */
@Component
public class ForgetMemoryTool implements ToolExecutor {

    private final MemoryService memoryService;

    public ForgetMemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("forget_memory")
                .label("删除记忆")
                .description("删除一条用户长期记忆，需要 key")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("key", Map.of("type", "string", "description", "记忆键")),
                        "required", List.of("key")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("记忆")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        String key = Args.str(args, "key");
        memoryService.delete(context.getUserId(), key);
        return ToolResult.success("forget_memory", Map.of("key", key), "记忆已删除: " + key);
    }
}
