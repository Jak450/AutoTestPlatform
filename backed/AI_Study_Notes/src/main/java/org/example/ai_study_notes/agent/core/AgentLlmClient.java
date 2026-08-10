package org.example.ai_study_notes.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Agent 模型层（自研 OpenAI 兼容客户端）：
 * - 支持 DeepSeek thinking 模式，assistant 消息的 reasoning_content 原样回传；
 * - 请求级失败重试一次；
 * - 失败以异常抛出，由 AgentLoop 编码为流内 error 事件。
 */
@Slf4j
@Service
public class AgentLlmClient {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentLlmClient(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(properties.getDeepseek().getTimeoutSeconds(), 30)))
                .build();
    }

    /**
     * 调用 /chat/completions，返回解析后的响应。
     */
    public LlmResponse chat(List<LlmMessage> messages, List<ToolDefinition> tools) {
        Map<String, Object> body = buildRequestBody(messages, tools);
        String endpoint = properties.getDeepseek().getBaseUrl()
                + (properties.getDeepseek().getBaseUrl().endsWith("/") ? "" : "/")
                + "chat/completions";
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String json = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(properties.getDeepseek().getTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + properties.getDeepseek().getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                }
                return parseResponse(response.body());
            } catch (Exception e) {
                lastError = e;
                if (attempt == 0) {
                    log.warn("LLM 调用失败，重试一次: {}", e.getMessage());
                }
            }
        }
        throw new IllegalStateException("模型调用失败: " + lastError.getMessage(), lastError);
    }

    /**
     * 流式调用 /chat/completions（stream=true），逐 token 回调 onTextDelta，
     * 同时累积 content/reasoning_content/tool_calls，结束时返回完整响应。
     */
    public LlmResponse chatStream(List<LlmMessage> messages, List<ToolDefinition> tools,
                                  Consumer<String> onTextDelta) {
        Map<String, Object> body = buildRequestBody(messages, tools);
        body.put("stream", true);
        String endpoint = properties.getDeepseek().getBaseUrl()
                + (properties.getDeepseek().getBaseUrl().endsWith("/") ? "" : "/")
                + "chat/completions";
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String json = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(properties.getDeepseek().getTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + properties.getDeepseek().getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + errBody);
                }
                return parseStream(response.body(), onTextDelta);
            } catch (Exception e) {
                lastError = e;
                if (attempt == 0) {
                    log.warn("LLM 流式调用失败，重试一次: {}", e.getMessage());
                }
            }
        }
        throw new IllegalStateException("模型调用失败: " + lastError.getMessage(), lastError);
    }

    private LlmResponse parseStream(InputStream in, Consumer<String> onTextDelta) throws Exception {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        TreeMap<Integer, Map<String, String>> toolFragments = new TreeMap<>();
        boolean toolCallSeen = false;
        String finishReason = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode root = objectMapper.readTree(payload);
                JsonNode choice = root.path("choices").path(0);
                if (!choice.path("finish_reason").isNull()) {
                    finishReason = choice.path("finish_reason").asText();
                }
                JsonNode delta = choice.path("delta");
                if (!delta.path("reasoning_content").isNull()) {
                    reasoning.append(delta.path("reasoning_content").asText());
                }
                JsonNode calls = delta.path("tool_calls");
                if (calls.isArray()) {
                    for (JsonNode call : calls) {
                        int index = call.path("index").asInt(-1);
                        if (index < 0) {
                            continue;
                        }
                        Map<String, String> fragment = toolFragments.computeIfAbsent(index, k -> new HashMap<>());
                        String id = call.path("id").asText("");
                        if (!id.isEmpty()) {
                            fragment.put("id", id);
                        }
                        JsonNode fn = call.path("function");
                        String name = fn.path("name").asText("");
                        if (!name.isEmpty()) {
                            fragment.put("name", name);
                        }
                        String arguments = fn.path("arguments").asText("");
                        if (!arguments.isEmpty()) {
                            fragment.merge("arguments", arguments, String::concat);
                        }
                        toolCallSeen = true;
                    }
                }
                if (!delta.path("content").isNull()) {
                    String c = delta.path("content").asText();
                    content.append(c == null ? "" : c);
                    if (!toolCallSeen && c != null && !c.isEmpty()) {
                        onTextDelta.accept(c);
                    }
                }
            }
        }
        List<LlmMessage.ToolCall> toolCalls = new ArrayList<>();
        for (Map<String, String> fragment : toolFragments.values()) {
            toolCalls.add(LlmMessage.ToolCall.builder()
                    .id(fragment.getOrDefault("id", UUID.randomUUID().toString()))
                    .name(fragment.getOrDefault("name", "unknown"))
                    .arguments(fragment.getOrDefault("arguments", "{}"))
                    .build());
        }
        return new LlmResponse(
                content.length() == 0 ? null : content.toString(),
                reasoning.length() == 0 ? null : reasoning.toString(),
                toolCalls,
                finishReason);
    }

    private Map<String, Object> buildRequestBody(List<LlmMessage> messages, List<ToolDefinition> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getDeepseek().getModel());
        List<Map<String, Object>> requestMessages = new ArrayList<>();
        for (LlmMessage message : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", message.getRole());
            m.put("content", message.getContent() == null ? "" : message.getContent());
            if ("assistant".equals(message.getRole())) {
                if (message.getReasoningContent() != null && !message.getReasoningContent().isBlank()) {
                    m.put("reasoning_content", message.getReasoningContent());
                }
                if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                    List<Map<String, Object>> calls = new ArrayList<>();
                    for (LlmMessage.ToolCall call : message.getToolCalls()) {
                        Map<String, Object> callMap = new LinkedHashMap<>();
                        callMap.put("id", call.getId());
                        callMap.put("type", "function");
                        Map<String, Object> function = new LinkedHashMap<>();
                        function.put("name", call.getName());
                        function.put("arguments", call.getArguments());
                        callMap.put("function", function);
                        calls.add(callMap);
                    }
                    m.put("tool_calls", calls);
                }
            }
            if ("tool".equals(message.getRole())) {
                m.put("tool_call_id", message.getToolCallId());
            }
            requestMessages.add(m);
        }
        body.put("messages", requestMessages);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolList = new ArrayList<>();
            for (ToolDefinition definition : tools) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", definition.getName());
                function.put("description", definition.getDescription());
                function.put("parameters", definition.getInputSchema() == null
                        ? Map.of("type", "object", "properties", Map.of())
                        : definition.getInputSchema());
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("type", "function");
                tool.put("function", function);
                toolList.add(tool);
            }
            body.put("tools", toolList);
        }
        body.put("temperature", properties.getDeepseek().getTemperature());
        body.put("max_tokens", properties.getDeepseek().getMaxTokens());
        return body;
    }

    private LlmResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choice = root.path("choices").path(0).path("message");
        String content = choice.path("content").isNull() ? null : choice.path("content").asText();
        String reasoning = choice.path("reasoning_content").isNull()
                ? null : choice.path("reasoning_content").asText();
        List<LlmMessage.ToolCall> toolCalls = new ArrayList<>();
        JsonNode calls = choice.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode call : calls) {
                toolCalls.add(LlmMessage.ToolCall.builder()
                        .id(call.path("id").asText())
                        .name(call.path("function").path("name").asText())
                        .arguments(call.path("function").path("arguments").asText())
                        .build());
            }
        }
        String finishReason = root.path("choices").path(0).path("finish_reason").asText(null);
        return new LlmResponse(content, reasoning, toolCalls, finishReason);
    }

    public record LlmResponse(String content, String reasoningContent,
                              List<LlmMessage.ToolCall> toolCalls, String finishReason) {
    }
}
