package org.example.ai_study_notes.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.audit.AuditService;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.confirmation.AgentConfirmation;
import org.example.ai_study_notes.agent.confirmation.ConfirmationService;
import org.example.ai_study_notes.agent.contract.EventType;
import org.example.ai_study_notes.agent.core.AgentLoop;
import org.example.ai_study_notes.agent.core.RunRegistry;
import org.example.ai_study_notes.agent.event.ConversationEventStream;
import org.example.ai_study_notes.agent.event.EventStreamService;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.example.ai_study_notes.agent.session.MessageService;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolExecutionService;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 用户确认 API：批准后执行工具并续跑 Agent 汇总，拒绝则记录拒绝结果。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/conversations/{conversationId}/confirmations")
public class ConfirmationController {

    private final ConversationService conversationService;
    private final ConfirmationService confirmationService;
    private final ToolExecutionService toolExecutionService;
    private final MessageService messageService;
    private final EventStreamService eventStreamService;
    private final AgentLoop agentLoop;
    private final RunRegistry runRegistry;
    private final ObjectMapper objectMapper;
    private final Executor agentExecutor;
    private final AuditService auditService;

    public ConfirmationController(ConversationService conversationService,
                                  ConfirmationService confirmationService,
                                  ToolExecutionService toolExecutionService,
                                  MessageService messageService,
                                  EventStreamService eventStreamService,
                                  AgentLoop agentLoop,
                                  RunRegistry runRegistry,
                                  ObjectMapper objectMapper,
                                  AuditService auditService,
                                  @Qualifier("agentExecutor") Executor agentExecutor) {
        this.conversationService = conversationService;
        this.confirmationService = confirmationService;
        this.toolExecutionService = toolExecutionService;
        this.messageService = messageService;
        this.eventStreamService = eventStreamService;
        this.agentLoop = agentLoop;
        this.runRegistry = runRegistry;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.agentExecutor = agentExecutor;
    }

    @PostMapping("/{confirmationId}")
    public Result<Map<String, Object>> respond(@PathVariable("conversationId") Long conversationId,
                                               @PathVariable("confirmationId") Long confirmationId,
                                               @RequestBody ConfirmRequest request) {
        Long userId = UserContext.userId();
        if (conversationService.getOwned(userId, conversationId) == null) {
            return Result.error("会话不存在或无权访问");
        }
        String decision = request.getDecision();
        if (decision == null || decision.isBlank()) {
            return Result.error("decision 不能为空");
        }
        try {
            if ("rejected".equalsIgnoreCase(decision)) {
                ConfirmationService.PendingToolCall pending = confirmationService.reject(conversationId, confirmationId);
                appendRejectedResult(conversationId, pending);
                auditService.log(userId, conversationId, null, "confirmation_rejected",
                        Map.of("confirmationId", confirmationId,
                                "toolName", pending == null ? "unknown" : pending.getToolName()));
                resume(conversationId, userId);
                return Result.success(Map.of("status", "rejected"));
            }
            if (!"approved".equalsIgnoreCase(decision)) {
                return Result.error("decision 必须是 approved 或 rejected");
            }
            ConfirmationService.PendingToolCall pending = confirmationService.approve(conversationId, confirmationId);
            if (pending == null) {
                return Result.error("确认已过期或已处理，请重新发起操作");
            }
            ToolResult result = toolExecutionService.execute(pending.getToolName(), pending.getArgs(),
                    ToolContext.builder().userId(userId).conversationId(conversationId)
                            .toolCallId(pending.getToolCallId()).build(), true);
            auditService.log(userId, conversationId, null, "confirmation_approved",
                    Map.of("confirmationId", confirmationId, "toolName", pending.getToolName(),
                            "status", result.getStatus().value()));

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("toolCallId", pending.getToolCallId());
            meta.put("toolName", pending.getToolName());
            meta.put("status", result.getStatus().value());
            meta.put("confirmed", true);
            messageService.append(conversationId, "tool", "tool_result", toJson(result), toJson(meta));

            ConversationEventStream stream = eventStreamService.getOrCreate(conversationId);
            stream.emit(EventType.TOOL_EXECUTION_END.value(), Map.of(
                    "toolCallId", pending.getToolCallId(),
                    "toolName", pending.getToolName(),
                    "status", result.getStatus().value(),
                    "durationMs", result.getDurationMs(),
                    "confirmed", true));
            resume(conversationId, userId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "approved");
            data.put("toolResult", result);
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private void appendRejectedResult(Long conversationId, ConfirmationService.PendingToolCall pending) {
        String toolCallId = pending == null ? null : pending.getToolCallId();
        String toolName = pending == null ? "unknown" : pending.getToolName();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("toolCallId", toolCallId);
        meta.put("toolName", toolName);
        meta.put("status", "rejected");
        meta.put("confirmed", false);
        messageService.append(conversationId, "tool", "tool_result",
                toJson(Map.of("toolName", toolName, "status", "rejected", "message", "用户拒绝了该操作")), toJson(meta));
    }

    private void resume(Long conversationId, Long userId) {
        if (runRegistry.isRunning(conversationId)) {
            return;
        }
        runRegistry.start(conversationId);
        agentExecutor.execute(() -> agentLoop.run(conversationId, userId, null));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Data
    public static class ConfirmRequest {
        private String decision;
    }
}
