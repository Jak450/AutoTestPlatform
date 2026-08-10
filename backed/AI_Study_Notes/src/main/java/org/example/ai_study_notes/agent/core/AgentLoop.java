package org.example.ai_study_notes.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.confirmation.AgentConfirmation;
import org.example.ai_study_notes.agent.confirmation.ConfirmationService;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.contract.EventType;
import org.example.ai_study_notes.agent.contract.StopReason;
import org.example.ai_study_notes.agent.context.ContextAssembler;
import org.example.ai_study_notes.agent.event.ConversationEventStream;
import org.example.ai_study_notes.agent.event.EventStreamService;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.example.ai_study_notes.agent.session.MessageService;
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

    private final AgentAiClient aiClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final ConfirmationService confirmationService;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final EventStreamService eventStreamService;
    private final ContextAssembler contextAssembler;
    private final SystemPromptBuilder systemPromptBuilder;
    private final RunRegistry runRegistry;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public AgentLoop(AgentAiClient aiClient,
                     ToolRegistry toolRegistry,
                     ToolExecutionService toolExecutionService,
                     ConfirmationService confirmationService,
                     MessageService messageService,
                     ConversationService conversationService,
                     EventStreamService eventStreamService,
                     ContextAssembler contextAssembler,
                     SystemPromptBuilder systemPromptBuilder,
                     RunRegistry runRegistry,
                     ObjectMapper objectMapper,
                     AgentProperties properties) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
        this.confirmationService = confirmationService;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.eventStreamService = eventStreamService;
        this.contextAssembler = contextAssembler;
        this.systemPromptBuilder = systemPromptBuilder;
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
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPromptBuilder.build()));
        messages.addAll(contextAssembler.toLlmMessages(conversationId));

        int maxTurns = properties.getLoop().getMaxTurns();
        for (int turn = 0; turn < maxTurns; turn++) {
            if (runRegistry.isCancelled(conversationId)) {
                return StopReason.ABORTED;
            }
            stream.emit(EventType.TURN_START.value(), Map.of("runId", runId, "turn", turn));

            ChatResponse response;
            try {
                response = aiClient.chat(messages, toolRegistry.toLlmToolSpecifications());
            } catch (Exception e) {
                log.error("LLM 调用失败 conversationId={}", conversationId, e);
                stream.emit(EventType.ERROR.value(), Map.of("runId", runId, "message", "模型调用失败: " + e.getMessage()));
                stream.emit(EventType.TURN_END.value(), Map.of("runId", runId, "turn", turn));
                return StopReason.ERROR;
            }

            AiMessage aiMessage = response.aiMessage();
            if (aiMessage.hasToolExecutionRequests()) {
                boolean awaitingConfirmation = false;
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    Map<String, Object> args = parseArguments(request.arguments());
                    stream.emit(EventType.TOOL_EXECUTION_START.value(), Map.of(
                            "toolCallId", request.id(), "toolName", request.name(), "status", "running"));

                    ToolResult result = toolExecutionService.execute(request.name(), args,
                            ToolContext.builder().userId(userId).conversationId(conversationId).build(), false);

                    if (result.isRequiresConfirmation()) {
                        AgentConfirmation confirmation = confirmationService.create(
                                conversationId, request.name(), args, request.id());
                        messageService.append(conversationId, "assistant", "tool_call",
                                request.arguments(), toJson(toolMeta(request.id(), request.name(), args, "pending")));
                        Map<String, Object> confirmMeta = new LinkedHashMap<>();
                        confirmMeta.put("confirmationId", confirmation.getId());
                        confirmMeta.put("toolName", request.name());
                        confirmMeta.put("toolCallId", request.id());
                        confirmMeta.put("payload", args);
                        AgentMessage confirmMessage = messageService.append(conversationId, "assistant", "confirmation",
                                "需要您确认操作: " + request.name(), toJson(confirmMeta));
                        stream.emit(EventType.MESSAGE_START.value(), Map.of(
                                "messageId", confirmMessage.getId(), "role", "assistant", "type", "confirmation",
                                "confirmationId", confirmation.getId(), "toolName", request.name()));
                        stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                                "messageId", confirmMessage.getId(), "delta", "需要您确认操作: " + request.name(),
                                "confirmationId", confirmation.getId(), "toolName", request.name(), "payload", args));
                        stream.emit(EventType.MESSAGE_END.value(), Map.of(
                                "messageId", confirmMessage.getId(), "confirmationId", confirmation.getId()));
                        stream.emit(EventType.TOOL_EXECUTION_END.value(), Map.of(
                                "toolCallId", request.id(), "toolName", request.name(),
                                "status", "awaiting_confirmation", "confirmationId", confirmation.getId(),
                                "payload", args));
                        awaitingConfirmation = true;
                        break;
                    }

                    String resultJson = toJson(result);
                    messageService.append(conversationId, "assistant", "tool_call",
                            request.arguments(), toJson(toolMeta(request.id(), request.name(), args, "done")));
                    messageService.append(conversationId, "tool", "tool_result", resultJson,
                            toJson(toolMeta(request.id(), request.name(), args, result.getStatus().value())));
                    messages.add(AiMessage.from(request));
                    messages.add(ToolExecutionResultMessage.from(request.id(), request.name(), resultJson));
                    stream.emit(EventType.TOOL_EXECUTION_END.value(), Map.of(
                            "toolCallId", request.id(), "toolName", request.name(),
                            "status", result.getStatus().value(), "durationMs", result.getDurationMs()));
                }
                stream.emit(EventType.TURN_END.value(), Map.of("runId", runId, "turn", turn));
                if (awaitingConfirmation) {
                    return StopReason.AWAITING_CONFIRMATION;
                }
                continue;
            }

            String text = aiMessage.text();
            AgentMessage assistantMessage = messageService.append(conversationId, "assistant", "text", text, null);
            stream.emit(EventType.MESSAGE_START.value(), Map.of(
                    "messageId", assistantMessage.getId(), "role", "assistant", "type", "text"));
            stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                    "messageId", assistantMessage.getId(), "delta", text));
            stream.emit(EventType.MESSAGE_END.value(), Map.of("messageId", assistantMessage.getId()));
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

    private Map<String, Object> toolMeta(String toolCallId, String toolName, Map<String, Object> args, String status) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("toolCallId", toolCallId);
        meta.put("toolName", toolName);
        meta.put("status", status);
        meta.put("args", args);
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
