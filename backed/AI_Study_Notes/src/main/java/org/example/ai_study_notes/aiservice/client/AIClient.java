package org.example.ai_study_notes.aiservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AIClient {

    private final AIModelConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIClient(AIModelConfig config) {
        this.config = config;
    }

    public String chat(String model, String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        return chatWithHistory(model, messages);
    }

    public String chatWithHistory(String model, List<Map<String, String>> messages) {
        try {
            URI uri = new URI(config.getBaseUrl() + "/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setDoOutput(true);
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(120000);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messages,
                    "max_tokens", 4096,
                    "temperature", 0.3
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(requestBody));
                os.flush();
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("AI API error [{}]: {}", status, error);
                throw new RuntimeException("AI API call failed: " + status);
            }

            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI API returned empty choices");
            }
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, String> message = (Map<String, String>) firstChoice.get("message");
            return message.get("content");

        } catch (Exception e) {
            log.error("AI client error with model {}: {}", model, e.getMessage(), e);
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }
}
