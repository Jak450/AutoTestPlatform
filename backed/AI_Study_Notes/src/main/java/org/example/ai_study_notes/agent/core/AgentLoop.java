package org.example.ai_study_notes.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.audit.AuditService;
import org.example.ai_study_notes.agent.confirmation.AgentConfirmation;
import org.example.ai_study_notes.agent.confirmation.ConfirmationService;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.contract.EventType;
import org.example.ai_study_notes.agent.contract.StopReason;
import org.example.ai_study_notes.agent.context.ContextAssembler;
import org.example.ai_study_notes.agent.event.ConversationEventStream;
import org.example.ai_study_notes.agent.event.EventStreamService;
import org.example.ai_study_notes.agent.memory.MemoryService;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.example.ai_study_notes.agent.session.MessageService;
import org.example.ai_study_notes.agent.skill.AgentSkill;
import org.example.ai_study_notes.agent.skill.SkillService;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolExecutionService;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 主循环（turn 循环）：
 * 模型决策 -> 工具执行 -> 结果回写，直至无工具调用或达到轮数上限。
 * 写/执行类工具暂停等待用户确认，确认后由 ConfirmationController 触发 resume。
 */
@Slf4j
@Service
public class AgentLoop {

    private final AgentLlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final ConfirmationService confirmationService;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final EventStreamService eventStreamService;
    private final ContextAssembler contextAssembler;
    private final SystemPromptBuilder systemPromptBuilder;
    private final MemoryService memoryService;
    private final SkillService skillService;
    private final AuditService auditService;
    private final RunRegistry runRegistry;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public AgentLoop(AgentLlmClient llmClient,
                     ToolRegistry toolRegistry,
                     ToolExecutionService toolExecutionService,
                     ConfirmationService confirmationService,
                     MessageService messageService,
                     ConversationService conversationService,
                     EventStreamService eventStreamService,
                     ContextAssembler contextAssembler,
                     SystemPromptBuilder systemPromptBuilder,
                     MemoryService memoryService,
                     SkillService skillService,
                     AuditService auditService,
                     RunRegistry runRegistry,
                     ObjectMapper objectMapper,
                     AgentProperties properties) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
        this.confirmationService = confirmationService;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.eventStreamService = eventStreamService;
        this.contextAssembler = contextAssembler;
        this.systemPromptBuilder = systemPromptBuilder;
        this.memoryService = memoryService;
        this.skillService = skillService;
        this.auditService = auditService;
        this.runRegistry = runRegistry;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 启动一次 run。userText 为 null 表示确认后的续跑（不追加用户消息）。
     */
    public void run(Long conversationId, Long userId, String userText) {
        ConversationEventStream stream = eventStreamService.getOrCreate(conversationId);
        String runId = "run-" + UUID.randomUUID();
        stream.emit(EventType.AGENT_START.value(), Map.of("runId", runId, "conversationId", conversationId));
        auditService.log(userId, conversationId, runId, "agent_run_start",
                Map.of("userTextLength", userText == null ? 0 : userText.length()));
        try {
            if (userText != null) {
                AgentMessage userMessage = messageService.append(conversationId, "user", "text", userText, null);
                stream.emit(EventType.MESSAGE_START.value(), Map.of(
                        "messageId", userMessage.getId(), "role", "user", "type", "text"));
                stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                        "messageId", userMessage.getId(), "delta", userText, "role", "user", "type", "text"));
                stream.emit(EventType.MESSAGE_END.value(), Map.of("messageId", userMessage.getId()));
            }
            StopReason stopReason = loop(conversationId, userId, stream, runId);
            stream.emit(EventType.AGENT_END.value(), Map.of(
                    "runId", runId, "conversationId", conversationId, "stopReason", stopReason.value()));
            auditService.log(userId, conversationId, runId, "agent_run_end",
                    Map.of("stopReason", stopReason.value()));
        } catch (Exception e) {
            log.error("Agent run 失败 conversationId={}", conversationId, e);
            stream.emit(EventType.ERROR.value(), Map.of("runId", runId, "message", e.getMessage()));
            stream.emit(EventType.AGENT_END.value(), Map.of(
                    "runId", runId, "conversationId", conversationId,
                    "stopReason", StopReason.ERROR.value(), "errorMessage", e.getMessage()));
        } finally {
            runRegistry.finish(conversationId);
            conversationService.touch(conversationId);
        }
    }

    private StopReason loop(Long conversationId, Long userId, ConversationEventStream stream, String runId) {
        List<LlmMessage> messages = new ArrayList<>();
        List<String> memories = userId == null ? java.util.List.of() : memoryService.injectable(userId);
        List<String> skillBodies = skillService.activeSkills(conversationId).stream()
                .map(AgentSkill::getBody).toList();
        messages.add(LlmMessage.builder()
                .role("system")
                .content(systemPromptBuilder.build(memories, skillBodies))
                .build());
        messages.addAll(contextAssembler.toLlmMessages(conversationId));

        int maxTurns = properties.getLoop().getMaxTurns();
        for (int turn = 0; turn < maxTurns; turn++) {
            if (runRegistry.isCancelled(conversationId)) {
                return StopReason.ABORTED;
            }
            stream.emit(EventType.TURN_START.value(), Map.of("runId", runId, "turn", turn));

            // 流式输出：收到 token 即推 message_start/message_update，结束后统一持久化
            final String[] streamingMessageId = {null};
            final int currentTurn = turn;
            AgentLlmClient.LlmResponse response;
            try {
                response = llmClient.chatStream(
                        messages,
                        toolRegistry.activeDefinitions(conversationId, skillService.activatedTools(conversationId)),
                        delta -> {
                            if (delta == null || delta.isEmpty()) {
                                return;
                            }
                            if (streamingMessageId[0] == null) {
                                streamingMessageId[0] = "s-" + runId + "-" + currentTurn;
                                stream.emit(EventType.MESSAGE_START.value(), Map.of(
                                        "messageId", streamingMessageId[0], "role", "assistant", "type", "text"));
                            }
                            stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                                    "messageId", streamingMessageId[0], "delta", delta));
                        });
            } catch (Exception e) {
                log.error("LLM 调用失败 conversationId={}", conversationId, e);
                stream.emit(EventType.ERROR.value(), Map.of("runId", runId, "message", e.getMessage()));
                stream.emit(EventType.TURN_END.value(), Map.of("runId", runId, "turn", turn));
                return StopReason.ERROR;
            }

            List<LlmMessage.ToolCall> toolCalls = response.toolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                if (streamingMessageId[0] != null) {
                    stream.emit(EventType.MESSAGE_END.value(), Map.of("messageId", streamingMessageId[0]));
                }
                boolean awaitingConfirmation = false;
                for (LlmMessage.ToolCall call : toolCalls) {
                    Map<String, Object> args = parseArguments(call.getArguments());
                    stream.emit(EventType.TOOL_EXECUTION_START.value(), Map.of(
                            "toolCallId", call.getId(), "toolName", call.getName(), "status", "running"));

                    ToolResult result = toolExecutionService.execute(call.getName(), args,
                            ToolContext.builder().userId(userId).conversationId(conversationId).build(), false);

                    if (result.isRequiresConfirmation()) {
                        AgentConfirmation confirmation = confirmationService.create(
                                conversationId, call.getName(), args, call.getId());
                        messageService.append(conversationId, "assistant", "tool_call",
                                call.getArguments(),
                                toJson(toolMeta(call.getId(), call.getName(), args, "pending", response.reasoningContent())));
                        Map<String, Object> confirmMeta = new LinkedHashMap<>();
                        confirmMeta.put("confirmationId", confirmation.getId());
                        confirmMeta.put("toolName", call.getName());
                        confirmMeta.put("toolCallId", call.getId());
                        confirmMeta.put("payload", args);
                        AgentMessage confirmMessage = messageService.append(conversationId, "assistant", "confirmation",
                                "需要您确认操作: " + call.getName(), toJson(confirmMeta));
                        stream.emit(EventType.MESSAGE_START.value(), Map.of(
                                "messageId", confirmMessage.getId(), "role", "assistant", "type", "confirmation",
                                "confirmationId", confirmation.getId(), "toolName", call.getName()));
                        stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                                "messageId", confirmMessage.getId(), "delta", "需要您确认操作: " + call.getName(),
                                "confirmationId", confirmation.getId(), "toolName", call.getName(), "payload", args));
                        stream.emit(EventType.MESSAGE_END.value(), Map.of(
                                "messageId", confirmMessage.getId(), "confirmationId", confirmation.getId()));
                        stream.emit(EventType.TOOL_EXECUTION_END.value(), Map.of(
                                "toolCallId", call.getId(), "toolName", call.getName(),
                                "status", "awaiting_confirmation", "confirmationId", confirmation.getId(),
                                "payload", args));
                        awaitingConfirmation = true;
                        break;
                    }

                    String resultJson = toJson(result);
                    messageService.append(conversationId, "assistant", "tool_call",
                            call.getArguments(),
                            toJson(toolMeta(call.getId(), call.getName(), args, "done", response.reasoningContent())));
                    messageService.append(conversationId, "tool", "tool_result", resultJson,
                            toJson(toolMeta(call.getId(), call.getName(), args, result.getStatus().value(), null)));
                    messages.add(LlmMessage.builder()
                            .role("assistant")
                            .content(null)
                            .reasoningContent(response.reasoningContent())
                            .toolCalls(List.of(call))
                            .build());
                    messages.add(LlmMessage.builder()
                            .role("tool")
                            .content(resultJson)
                            .toolCallId(call.getId())
                            .build());
                    stream.emit(EventType.TOOL_EXECUTION_END.value(), Map.of(
                            "toolCallId", call.getId(), "toolName", call.getName(),
                            "status", result.getStatus().value(), "durationMs", result.getDurationMs()));
                }
                stream.emit(EventType.TURN_END.value(), Map.of("runId", runId, "turn", turn));
                if (awaitingConfirmation) {
                    return StopReason.AWAITING_CONFIRMATION;
                }
                continue;
            }

            String text = response.content() == null ? "" : response.content();
            Map<String, Object> meta = new LinkedHashMap<>();
            if (response.reasoningContent() != null) {
                meta.put("reasoningContent", response.reasoningContent());
            }
            messageService.append(conversationId, "assistant", "text", text,
                    meta.isEmpty() ? null : toJson(meta));
            if (streamingMessageId[0] == null) {
                // 极端情况：模型无任何文本增量（如空回复），补发完整事件
                streamingMessageId[0] = "s-" + runId + "-" + currentTurn;
                stream.emit(EventType.MESSAGE_START.value(), Map.of(
                        "messageId", streamingMessageId[0], "role", "assistant", "type", "text"));
                stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                        "messageId", streamingMessageId[0], "delta", text));
            }
            stream.emit(EventType.MESSAGE_END.value(), Map.of("messageId", streamingMessageId[0]));
            stream.emit(EventType.TURN_END.value(), Map.of("runId", runId, "turn", turn));
            return StopReason.STOP;
        }
        stream.emit(EventType.ERROR.value(), Map.of("runId", runId, "message", "达到最大轮数限制"));
        return StopReason.LOOP_CAPPED;
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            log.warn("工具参数解析失败: {}", arguments);
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> toolMeta(String toolCallId, String toolName, Map<String, Object> args,
                                         String status, String reasoningContent) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("toolCallId", toolCallId);
        meta.put("toolName", toolName);
        meta.put("status", status);
        meta.put("args", args);
        if (reasoningContent != null) {
            meta.put("reasoningContent", reasoningContent);
        }
        return meta;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
