package org.example.ai_study_notes.agent.memory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /embeddings 接口实现（支持 Ollama、云 API 等）。
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingClient(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        AgentProperties.Embedding cfg = properties.getEmbedding();
        String endpoint = cfg.getBaseUrl() + (cfg.getBaseUrl().endsWith("/") ? "" : "/") + "embeddings";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.getModel());
            body.put("input", texts);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Embedding HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<List<Float>> result = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                List<Float> vec = new ArrayList<>();
                for (JsonNode v : item.path("embedding")) {
                    vec.add((float) v.asDouble());
                }
                result.add(vec);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }
}
