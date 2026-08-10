package org.example.ai_study_notes.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.audit.AuditService;
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
    private final AuditService auditService;

    public ToolExecutionService(ToolRegistry registry, SchemaValidator schemaValidator,
                                MiddlewareChain middlewareChain, AuditService auditService) {
        this.registry = registry;
        this.schemaValidator = schemaValidator;
        this.middlewareChain = middlewareChain;
        this.auditService = auditService;
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
            ToolResult result = intercepted.get().withDuration(start);
            audit(context, toolName, result);
            return result;
        }
        try {
            ToolResult result = definition.getExecutor().execute(args, context);
            result.setToolName(toolName);
            result.withDuration(start);
            middlewareChain.after(toolName, args, result, context);
            audit(context, toolName, result);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败 tool={} args={}", toolName, args, e);
            ToolResult result = ToolResult.error(toolName,
                    "工具执行失败: " + e.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE,
                    start);
            audit(context, toolName, result);
            return result;
        }
    }

    private void audit(ToolContext context, String toolName, ToolResult result) {
        if (context == null) {
            return;
        }
        try {
            auditService.log(context.getUserId(), context.getConversationId(), null, "tool_executed",
                    java.util.Map.of(
                            "toolName", toolName,
                            "status", result.getStatus() == null ? null : result.getStatus().value(),
                            "durationMs", result.getDurationMs(),
                            "message", result.getMessage() == null ? "" : result.getMessage()));
        } catch (Exception e) {
            log.warn("工具审计失败 {}", toolName);
        }
    }
}
