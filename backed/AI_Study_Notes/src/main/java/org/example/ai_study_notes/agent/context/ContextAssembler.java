package org.example.ai_study_notes.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.MessageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 双消息模型转换：内部消息(AgentMessage) -> LLM 消息(ChatMessage)。
 * 只翻译 text/tool_call/tool_result，UI 专用消息（confirmation/case_preview/file）被过滤。
 */
@Slf4j
@Service
public class ContextAssembler {

    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public ContextAssembler(MessageService messageService, ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    public List<ChatMessage> toLlmMessages(Long conversationId) {
        List<AgentMessage> messages = messageService.list(conversationId);
        List<ChatMessage> llm = new ArrayList<>();
        for (AgentMessage message : messages) {
            try {
                switch (message.getRole()) {
                    case "user" -> llm.add(UserMessage.from(message.getContent()));
                    case "system" -> llm.add(SystemMessage.from(message.getContent()));
                    case "assistant" -> {
                        if ("tool_call".equals(message.getType())) {
                            Map<String, Object> meta = parseMeta(message.getToolMeta());
                            String toolCallId = strValue(meta.get("toolCallId"), String.valueOf(message.getId()));
                            String toolName = strValue(meta.get("toolName"), "unknown");
                            llm.add(AiMessage.from(ToolExecutionRequest.builder()
                                    .id(toolCallId)
                                    .name(toolName)
                                    .arguments(message.getContent())
                                    .build()));
                        } else if ("text".equals(message.getType())) {
                            llm.add(AiMessage.from(message.getContent()));
                        }
                        // confirmation / case_preview / file 等 UI 专用消息不进入 LLM
                    }
                    case "tool" -> {
                        Map<String, Object> meta = parseMeta(message.getToolMeta());
                        String toolCallId = strValue(meta.get("toolCallId"), String.valueOf(message.getId()));
                        String toolName = strValue(meta.get("toolName"), "unknown");
                        llm.add(ToolExecutionResultMessage.from(toolCallId, toolName, message.getContent()));
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
