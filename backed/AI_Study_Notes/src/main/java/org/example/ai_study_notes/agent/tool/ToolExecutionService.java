package org.example.ai_study_notes.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.audit.AuditService;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.execution.AgentToolExecution;
import org.example.ai_study_notes.agent.execution.ToolExecutionMapper;
import org.example.ai_study_notes.agent.middleware.MiddlewareChain;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具统一执行管线：注册检查 -> Schema 校验 -> 权限门禁 -> 执行 -> 结构化结果。
 */
@Slf4j
@Service
public class ToolExecutionService {

    /** 幂等去重只应用于写类工具（可重复执行的测试/查询不参与，避免误拦截有意重跑） */
    private static final Set<ToolPermission> IDEMPOTENT_PERMISSIONS =
            Set.of(ToolPermission.CONFIRM_WRITE, ToolPermission.AUTO_WRITE);
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);

    private final ToolRegistry registry;
    private final SchemaValidator schemaValidator;
    private final MiddlewareChain middlewareChain;
    private final AuditService auditService;
    private final ToolExecutionMapper executionMapper;
    private final ObjectMapper objectMapper;

    public ToolExecutionService(ToolRegistry registry, SchemaValidator schemaValidator,
                                MiddlewareChain middlewareChain, AuditService auditService,
                                ToolExecutionMapper executionMapper, ObjectMapper objectMapper) {
        this.registry = registry;
        this.schemaValidator = schemaValidator;
        this.middlewareChain = middlewareChain;
        this.auditService = auditService;
        this.executionMapper = executionMapper;
        this.objectMapper = objectMapper;
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
        AgentToolExecution execution = null;
        boolean idempotent = context != null && context.getConversationId() != null
                && definition.getPermission() != null
                && IDEMPOTENT_PERMISSIONS.contains(definition.getPermission());
        if (idempotent) {
            execution = beginOrReplay(toolName, args, context);
            if (execution == null) {
                return ToolResult.error(toolName, "该操作正在执行中，请勿重复提交",
                        ToolResultMeta.ErrorType.CONFIG,
                        ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                        start);
            }
            if ("success".equals(execution.getStatus()) && isRecent(execution)) {
                return replay(toolName, execution);
            }
        }
        java.util.Optional<ToolResult> intercepted = middlewareChain.before(toolName, args, context);
        if (intercepted.isPresent()) {
            ToolResult result = intercepted.get().withDuration(start);
            finish(execution, result);
            audit(context, toolName, result);
            return result;
        }
        try {
            ToolResult result = definition.getExecutor().execute(args, context);
            result.setToolName(toolName);
            result.withDuration(start);
            middlewareChain.after(toolName, args, result, context);
            finish(execution, result);
            audit(context, toolName, result);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败 tool={} args={}", toolName, args, e);
            ToolResult result = ToolResult.error(toolName,
                    "工具执行失败: " + e.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE,
                    start);
            finish(execution, result);
            audit(context, toolName, result);
            return result;
        }
    }

    /**
     * 幂等开始：尝试插入 running 记录；唯一键冲突时读取已有记录。
     * 返回 null 表示已有一条 running 记录（并发/重传中，不应再次执行）。
     */
    private AgentToolExecution beginOrReplay(String toolName, Map<String, Object> args, ToolContext context) {
        String hash = sha256(toJson(args));
        AgentToolExecution row = AgentToolExecution.builder()
                .conversationId(context.getConversationId())
                .toolCallId(context.getToolCallId())
                .toolName(toolName)
                .payloadHash(hash)
                .status("running")
                .build();
        try {
            executionMapper.insert(row);
            return row;
        } catch (DuplicateKeyException e) {
            AgentToolExecution existing = executionMapper.selectOne(new LambdaQueryWrapper<AgentToolExecution>()
                    .eq(AgentToolExecution::getConversationId, context.getConversationId())
                    .eq(AgentToolExecution::getToolName, toolName)
                    .eq(AgentToolExecution::getPayloadHash, hash));
            if (existing == null) {
                return row;
            }
            if ("running".equals(existing.getStatus())) {
                return null;
            }
            if ("success".equals(existing.getStatus()) && isRecent(existing)) {
                return existing;
            }
            // 失败或过期成功：允许重试，重置为 running
            AgentToolExecution update = new AgentToolExecution();
            update.setId(existing.getId());
            update.setStatus("running");
            update.setToolCallId(context.getToolCallId());
            update.setResult(null);
            executionMapper.updateById(update);
            existing.setStatus("running");
            existing.setResult(null);
            return existing;
        }
    }

    private void finish(AgentToolExecution execution, ToolResult result) {
        if (execution == null || execution.getId() == null) {
            return;
        }
        try {
            AgentToolExecution update = new AgentToolExecution();
            update.setId(execution.getId());
            update.setStatus(result.getStatus() == ToolResultMeta.Status.SUCCESS ? "success" : "failed");
            update.setResult(serializeResult(result));
            executionMapper.updateById(update);
        } catch (Exception e) {
            log.warn("工具执行记录写入失败 id={}", execution.getId());
        }
    }

    private ToolResult replay(String toolName, AgentToolExecution execution) {
        try {
            Map<String, Object> stored = objectMapper.readValue(execution.getResult(), Map.class);
            String status = String.valueOf(stored.getOrDefault("status", "ERROR"));
            String message = String.valueOf(stored.getOrDefault("message", ""));
            if ("SUCCESS".equals(status)) {
                return ToolResult.success(toolName, stored.get("data"), message + "（幂等重放，未重复执行）");
            }
            return ToolResult.error(toolName, message + "（幂等重放，未重复执行）",
                    ToolResultMeta.ErrorType.valueOf(String.valueOf(stored.getOrDefault("errorType", "INTERNAL"))),
                    ToolResultMeta.RecommendedNextAction.valueOf(
                            String.valueOf(stored.getOrDefault("recommendedNextAction", "TRY_ALTERNATIVE"))),
                    System.currentTimeMillis());
        } catch (Exception e) {
            return ToolResult.error(toolName, "幂等重放失败: " + e.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE,
                    System.currentTimeMillis());
        }
    }

    private String serializeResult(ToolResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", result.getStatus() == null ? null : result.getStatus().name());
        map.put("data", result.getData());
        map.put("message", result.getMessage());
        map.put("errorType", result.getErrorType() == null ? null : result.getErrorType().name());
        map.put("recommendedNextAction",
                result.getRecommendedNextAction() == null ? null : result.getRecommendedNextAction().name());
        return toJson(map);
    }

    private boolean isRecent(AgentToolExecution execution) {
        LocalDateTime base = execution.getUpdatedAt() != null ? execution.getUpdatedAt() : execution.getCreatedAt();
        return base != null && base.isAfter(LocalDateTime.now().minus(DEDUP_WINDOW));
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("payload hash 计算失败", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
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
