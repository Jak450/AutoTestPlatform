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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 模型层：DeepSeek 官方 OpenAI 兼容 API，仅负责模型调用与工具参数绑定。
 */
@Slf4j
@Service
public class AgentAiClient {

    private final ChatModel chatModel;
    private final Map<Integer, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final AgentProperties.DeepSeek deepseek;

    public AgentAiClient(AgentProperties properties) {
        this.deepseek = properties.getDeepseek();
        this.chatModel = getOrCreateModel(deepseek.getMaxTokens());
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

    /**
     * 便捷调用：单轮 system + user 文本。
     * 生成类任务（用例生成/压缩/记忆提炼）使用独立的 generation-max-tokens 预算，
     * 避免大段 JSON 输出被 max_tokens 截断成非法内容。
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, deepseek.getGenerationMaxTokens());
    }

    /**
     * 指定输出预算的单轮文本调用。
     */
    public String chat(String systemPrompt, String userMessage, int maxTokens) {
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(userMessage));
        ChatModel model = getOrCreateModel(maxTokens);
        return model.chat(ChatRequest.builder().messages(messages).build()).aiMessage().text();
    }

    private ChatModel getOrCreateModel(int maxTokens) {
        return modelCache.computeIfAbsent(maxTokens, tokens -> OpenAiChatModel.builder()
                .apiKey(deepseek.getApiKey())
                .baseUrl(deepseek.getBaseUrl())
                .modelName(deepseek.getModel())
                .maxTokens(tokens)
                .temperature(deepseek.getTemperature())
                .timeout(Duration.ofSeconds(deepseek.getTimeoutSeconds()))
                .build());
    }
}
