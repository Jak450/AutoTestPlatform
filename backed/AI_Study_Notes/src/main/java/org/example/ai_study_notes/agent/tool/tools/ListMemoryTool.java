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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询当前用户的长期记忆。
 */
@Component
public class ListMemoryTool implements ToolExecutor {

    private final MemoryService memoryService;

    public ListMemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_memory")
                .label("记忆列表")
                .description("列出/搜索当前用户的长期记忆（偏好、约定等），可选 query 关键字")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "搜索关键字（可选）")),
                        "required", List.of()))
                .permission(ToolPermission.READ)
                .category("记忆")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<MemoryEntry> entries = memoryService.list(context.getUserId(), Args.str(args, "query"));
        List<Map<String, Object>> memories = new ArrayList<>();
        for (MemoryEntry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", entry.getMemKey());
            map.put("content", entry.getContentMd());
            map.put("tags", entry.getTags());
            map.put("confirmed", entry.getConfirmed());
            map.put("version", entry.getVersion());
            memories.add(map);
        }
        return ToolResult.success("list_memory", memories, "共 " + memories.size() + " 条记忆");
    }
}
