package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.memory.MemoryEntry;
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
 * 写入长期记忆（需用户确认，覆盖已有 key 需 overwrite=true）。
 */
@Component
public class SaveMemoryTool implements ToolExecutor {

    private final MemoryService memoryService;

    public SaveMemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("save_memory")
                .label("保存记忆")
                .description("保存一条用户长期记忆，需要 key 与 content；覆盖已有 key 时 overwrite 必须为 true；tags 为标签数组（可选）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "key", Map.of("type", "string", "description", "记忆键，如 default_project_id"),
                                "content", Map.of("type", "string", "description", "记忆内容"),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                                "overwrite", Map.of("type", "boolean", "description", "是否覆盖已有记忆")),
                        "required", List.of("key", "content")))
                .permission(ToolPermission.AUTO_WRITE)
                .category("记忆")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        MemoryEntry entry = memoryService.save(
                context.getUserId(),
                Args.str(args, "key"),
                Args.str(args, "content"),
                Args.str(args, "tags") == null ? null : List.of(Args.str(args, "tags").split(",")),
                Args.bool(args, "overwrite", false),
                context.getConversationId());
        return ToolResult.success("save_memory",
                Map.of("key", entry.getMemKey(), "version", entry.getVersion()),
                "记忆已保存: " + entry.getMemKey());
    }
}
