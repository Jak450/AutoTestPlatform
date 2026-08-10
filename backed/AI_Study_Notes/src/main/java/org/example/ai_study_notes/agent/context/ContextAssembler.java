package org.example.ai_study_notes.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.core.LlmMessage;
import org.example.ai_study_notes.agent.session.AgentConversation;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.example.ai_study_notes.agent.session.MessageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 双消息模型转换：内部消息(AgentMessage) -> 模型层消息(LlmMessage)。
 * 只转换 text/tool_call/tool_result，UI 专用消息（confirmation/case_preview/file）被过滤。
 * 压缩摘要以 system 消息注入。
 */
@Slf4j
@Service
public class ContextAssembler {

    private static final int MAX_CONTEXT_MESSAGES = 60;

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public ContextAssembler(MessageService messageService,
                            ConversationService conversationService,
                            ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    public List<LlmMessage> toLlmMessages(Long conversationId) {
        List<AgentMessage> messages = messageService.list(conversationId);
        List<LlmMessage> llm = new ArrayList<>();
        AgentConversation conversation = conversationService.get(conversationId);
        if (conversation != null
                && conversation.getContextSummary() != null
                && !conversation.getContextSummary().isBlank()
                && messages.size() > MAX_CONTEXT_MESSAGES) {
            llm.add(LlmMessage.builder()
                    .role("system")
                    .content("会话历史摘要（压缩产物）:\n" + conversation.getContextSummary())
                    .build());
        }
        int start = 0;
        if (messages.size() > MAX_CONTEXT_MESSAGES) {
            start = messages.size() - MAX_CONTEXT_MESSAGES;
            // 压缩点不允许落在 tool_result 上
            while (start < messages.size() && "tool".equals(messages.get(start).getRole())) {
                start++;
            }
            if (start >= messages.size()) {
                start = messages.size() - 1;
            }
        }
        for (int i = start; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            try {
                switch (message.getRole()) {
                    case "user" -> {
                        if ("text".equals(message.getType()) || "file".equals(message.getType())) {
                            llm.add(LlmMessage.builder().role("user").content(message.getContent()).build());
                        }
                    }
                    case "system" -> llm.add(LlmMessage.builder().role("system").content(message.getContent()).build());
                    case "assistant" -> {
                        Map<String, Object> meta = parseMeta(message.getToolMeta());
                        if ("tool_call".equals(message.getType())) {
                            String toolCallId = strValue(meta.get("toolCallId"), String.valueOf(message.getId()));
                            String toolName = strValue(meta.get("toolName"), "unknown");
                            llm.add(LlmMessage.builder()
                                    .role("assistant")
                                    .content(null)
                                    .reasoningContent(strValue(meta.get("reasoningContent"), null))
                                    .toolCalls(List.of(LlmMessage.ToolCall.builder()
                                            .id(toolCallId)
                                            .name(toolName)
                                            .arguments(message.getContent())
                                            .build()))
                                    .build());
                        } else if ("text".equals(message.getType())) {
                            llm.add(LlmMessage.builder()
                                    .role("assistant")
                                    .content(message.getContent())
                                    .reasoningContent(strValue(meta.get("reasoningContent"), null))
                                    .build());
                        }
                    }
                    case "tool" -> {
                        Map<String, Object> meta = parseMeta(message.getToolMeta());
                        llm.add(LlmMessage.builder()
                                .role("tool")
                                .content(message.getContent())
                                .toolCallId(strValue(meta.get("toolCallId"), String.valueOf(message.getId())))
                                .build());
                    }
                    default -> {
                        // ignore
                    }
                }
            } catch (Exception e) {
                log.warn("消息转换失败，跳过 msgId={}: {}", message.getId(), e.getMessage());
            }
        }
        return llm;
    }

    private Map<String, Object> parseMeta(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String strValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
