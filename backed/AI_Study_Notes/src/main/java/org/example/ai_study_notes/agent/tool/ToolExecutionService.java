package org.example.ai_study_notes.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.middleware.MiddlewareChain;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 工具统一执行管线：注册检查 -> Schema 校验 -> 权限门禁 -> 执行 -> 结构化结果。
 */
@Slf4j
@Service
public class ToolExecutionService {

    private final ToolRegistry registry;
    private final SchemaValidator schemaValidator;
    private final MiddlewareChain middlewareChain;

    public ToolExecutionService(ToolRegistry registry, SchemaValidator schemaValidator, MiddlewareChain middlewareChain) {
        this.registry = registry;
        this.schemaValidator = schemaValidator;
        this.middlewareChain = middlewareChain;
    }

    /**
     * 执行工具。confirmed=true 表示已通过用户确认，可执行写/执行类工具。
     */
    public ToolResult execute(String toolName, Map<String, Object> args, ToolContext context, boolean confirmed) {
        long start = System.currentTimeMillis();
        ToolDefinition definition = registry.get(toolName);
        if (definition == null) {
            // 未知工具也走中间件（如循环检测），避免模型反复重试同一非法调用
            java.util.Optional<ToolResult> intercepted = middlewareChain.before(toolName, args, context);
            if (intercepted.isPresent()) {
                return intercepted.get().withDuration(start);
            }
            return ToolResult.unknownTool(toolName, start);
        }
        List<String> errors = schemaValidator.validate(definition.getInputSchema(), args);
        if (!errors.isEmpty()) {
            return ToolResult.error(toolName,
                    "参数校验失败: " + String.join("; ", errors),
                    ToolResultMeta.ErrorType.CONFIG,
                    ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                    start);
        }
        if (!confirmed && definition.getPermission().requiresConfirmation()) {
            return ToolResult.requiresConfirmation(toolName, args);
        }
        java.util.Optional<ToolResult> intercepted = middlewareChain.before(toolName, args, context);
        if (intercepted.isPresent()) {
            return intercepted.get().withDuration(start);
        }
        try {
            ToolResult result = definition.getExecutor().execute(args, context);
            result.setToolName(toolName);
            result.withDuration(start);
            middlewareChain.after(toolName, args, result, context);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败 tool={} args={}", toolName, args, e);
            return ToolResult.error(toolName,
                    "工具执行失败: " + e.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE,
                    start);
        }
    }
}
