package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写操作前必须存在同会话的读取标记（ReadBeforeWrite）。
 * 简化版：本会话最近 N 条工具调用中至少有一次查询类工具。
 */
@Component
public class ReadBeforeWriteMiddleware implements Middleware {

    private static final int RECENT_READ_WINDOW = 10;
    /**
     * 用户内容类写入无需"先查询平台数据"：知识文档是自包含内容，不涉及平台实体。
     */
    private static final Set<String> NO_READ_REQUIRED = Set.of(
            "save_knowledge", "delete_knowledge");

    @Override
    public int order() {
        return 4;
    }

    private final ToolRegistry toolRegistry;
    private final Map<Long, Set<String>> recentReads = new ConcurrentHashMap<>();

    public ReadBeforeWriteMiddleware(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public Optional<ToolResult> before(String toolName, Map<String, Object> args, ToolContext context) {
        ToolDefinition definition = toolRegistry.get(toolName);
        if (definition == null || definition.getPermission() != ToolPermission.CONFIRM_WRITE) {
            return Optional.empty();
        }
        if (NO_READ_REQUIRED.contains(toolName)) {
            return Optional.empty();
        }
        Set<String> reads = recentReads.computeIfAbsent(context.getConversationId(), k -> new HashSet<>());
        synchronized (reads) {
            if (reads.isEmpty()) {
                return Optional.of(ToolResult.error(toolName,
                        "写操作前请先执行查询（如 list_projects / list_use_cases）确认目标数据",
                        ToolResultMeta.ErrorType.PERMISSION,
                        ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                        System.currentTimeMillis()));
            }
        }
        return Optional.empty();
    }

    @Override
    public void after(String toolName, Map<String, Object> args, ToolResult result, ToolContext context) {
        ToolDefinition definition = toolRegistry.get(toolName);
        if (definition != null && definition.getPermission() == ToolPermission.READ) {
            Set<String> reads = recentReads.computeIfAbsent(context.getConversationId(), k -> new HashSet<>());
            synchronized (reads) {
                reads.add(toolName);
                if (reads.size() > RECENT_READ_WINDOW) {
                    reads.clear();
                }
            }
        }
    }
}
