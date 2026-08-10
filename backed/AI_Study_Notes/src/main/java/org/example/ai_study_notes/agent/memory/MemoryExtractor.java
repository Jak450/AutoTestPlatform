package org.example.ai_study_notes.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动提炼候选记忆：run 结束后从对话历史提炼偏好/约定，存为未确认候选（confirmed=0）。
 */
@Slf4j
@Component
public class MemoryExtractor {

    private static final String PROMPT = """
            你是 AutoTestPlatform 的记忆提炼器。从对话中提炼用户明确表达的、跨会话有用的偏好或约定（例如默认项目、命名风格、断言习惯、常用环境）。
            只输出一个 JSON 数组，最多 2 条，每条 {"key": "snake_case 键名", "content": "一句话描述"}。
            没有可提炼的内容就输出 []。不要输出其他文字。
            """;

    private final AgentAiClient aiClient;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final Set<Long> extractedConversations = ConcurrentHashMap.newKeySet();

    public MemoryExtractor(AgentAiClient aiClient, MemoryService memoryService, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    public void extractIfNeeded(Long conversationId, Long userId, String historyTail) {
        if (userId == null || extractedConversations.contains(conversationId)) {
            return;
        }
        if (historyTail == null || historyTail.length() < 200) {
            return;
        }
        try {
            String raw = aiClient.chat(PROMPT, historyTail);
            List<Map<String, Object>> candidates = parse(raw);
            int saved = 0;
            for (Map<String, Object> candidate : candidates) {
                String key = String.valueOf(candidate.get("key"));
                String content = String.valueOf(candidate.get("content"));
                if (key == null || "null".equals(key) || content == null || "null".equals(content)) {
                    continue;
                }
                MemoryEntry entry = memoryService.saveCandidate(userId, key.trim(), content.trim());
                if (entry != null) {
                    saved++;
                }
            }
            extractedConversations.add(conversationId);
            log.info("会话 {} 自动提炼记忆候选 {} 条", conversationId, saved);
        } catch (Exception e) {
            log.warn("记忆自动提炼失败 conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    private List<Map<String, Object>> parse(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int first = cleaned.indexOf('\n');
            int last = cleaned.lastIndexOf("```");
            if (first >= 0 && last > first) {
                cleaned = cleaned.substring(first + 1, last).trim();
            }
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
