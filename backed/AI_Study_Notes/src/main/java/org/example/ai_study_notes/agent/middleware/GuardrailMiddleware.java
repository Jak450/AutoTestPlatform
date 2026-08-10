package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Guardrail：工具执行前鉴权，deny 权限一律拒绝。
 */
@Component
public class GuardrailMiddleware implements Middleware {

    private final ToolRegistry toolRegistry;

    public GuardrailMiddleware(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Optional<ToolResult> before(String toolName, Map<String, Object> args, ToolContext context) {
        ToolDefinition definition = toolRegistry.get(toolName);
        if (definition != null && definition.getPermission() == ToolPermission.DENY) {
            return Optional.of(ToolResult.error(toolName, "该工具已被拒绝执行（deny）",
                    ToolResultMeta.ErrorType.PERMISSION,
                    ToolResultMeta.RecommendedNextAction.STOP,
                    System.currentTimeMillis()));
        }
        return Optional.empty();
    }
}
