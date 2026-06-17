package org.example.ai_study_notes.aiservice.client;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class AIClient {

    private final AIModelConfig config;
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    public AIClient(AIModelConfig config) {
        this.config = config;
    }

    public String chat(String model, String systemPrompt, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userMessage));
        return doChat(model, messages);
    }

    public String chatWithHistory(String model, List<Map<String, String>> rawMessages) {
        return doChat(model, convertMessages(rawMessages));
    }

    private String doChat(String model, List<ChatMessage> messages) {
        ChatModel chatModel = getOrCreateChatModel(model);
        try {
            ChatResponse response = chatModel.chat(messages);
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("AI client error with model {}: {}", model, e.getMessage(), e);
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    public String chatStream(String model, String systemPrompt, String userMessage, Consumer<String> onToken) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userMessage));
        return doChatStream(model, messages, onToken);
    }

    public String chatStreamWithHistory(String model, List<Map<String, String>> rawMessages, Consumer<String> onToken) {
        return doChatStream(model, convertMessages(rawMessages), onToken);
    }

    private String doChatStream(String model, List<ChatMessage> messages, Consumer<String> onToken) {
        StreamingChatModel streamingModel = getOrCreateStreamingModel(model);
        StringBuilder fullContent = new StringBuilder();
        CompletableFuture<Void> future = new CompletableFuture<>();

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullContent.append(partialResponse);
                onToken.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        try {
            future.get(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("AI stream error with model {}: {}", model, e.getMessage(), e);
            throw new RuntimeException("AI stream failed: " + e.getMessage(), e);
        }

        return fullContent.toString();
    }

    private List<ChatMessage> convertMessages(List<Map<String, String>> rawMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        for (Map<String, String> msg : rawMessages) {
            String role = msg.get("role");
            String content = msg.get("content");
            switch (role) {
                case "system" -> messages.add(SystemMessage.from(content));
                case "user" -> messages.add(UserMessage.from(content));
                case "assistant" -> messages.add(AiMessage.from(content));
            }
        }
        return messages;
    }

    private ChatModel getOrCreateChatModel(String model) {
        return chatModelCache.computeIfAbsent(model, m -> OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(m)
                .maxTokens(4096)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(120))
                .build());
    }

    private StreamingChatModel getOrCreateStreamingModel(String model) {
        return streamingModelCache.computeIfAbsent(model, m -> OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(m)
                .maxTokens(4096)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(300))
                .build());
    }
}
