package org.example.ai_study_notes.agent.core;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Agent 模型层：DeepSeek 官方 OpenAI 兼容 API，仅负责模型调用与工具参数绑定。
 */
@Slf4j
@Service
public class AgentAiClient {

    private final ChatModel chatModel;

    public AgentAiClient(AgentProperties properties) {
        AgentProperties.DeepSeek deepseek = properties.getDeepseek();
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(deepseek.getApiKey())
                .baseUrl(deepseek.getBaseUrl())
                .modelName(deepseek.getModel())
                .maxTokens(deepseek.getMaxTokens())
                .temperature(deepseek.getTemperature())
                .timeout(Duration.ofSeconds(deepseek.getTimeoutSeconds()))
                .build();
        log.info("Agent 模型初始化完成: model={}, baseUrl={}", deepseek.getModel(), deepseek.getBaseUrl());
    }

    public ChatResponse chat(List<dev.langchain4j.data.message.ChatMessage> messages,
                             List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecifications) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.toolSpecifications(toolSpecifications);
        }
        return chatModel.chat(builder.build());
    }
}
