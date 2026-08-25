package org.example.ai_study_notes.agent.memory.distill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.FactMemoryService;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.memory.episode.EpisodeRecorder;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆提炼器：run 结束后从对话尾部提炼事实/经验候选，先落情景记录再提炼入库。
 * 只做编排；提炼 LLM 用 AgentAiClient（单轮生成），持久化走各 Service，向量索引走 MemoryIndexer。
 */
@Slf4j
@Service
public class MemoryDistiller {

    private static final String PROMPT = """
            你是记忆提炼器。从对话历史中提炼结构化记忆，只输出 JSON：
            {"facts":[{"entity_id":"...","attribute":"...","value":"...","confidence":0.9}],
             "experiences":[{"task_type":"...","rule":"...","confidence":0.8}],
             "preferences":[{"key":"snake_case","content":"一句话偏好"}],
             "knowledge":[{"title":"...","category":"经验教训|测试理论|项目规范","content":"2-4 句结论"}]}
            规则：
            - facts 是用户明确陈述的实体事实（价格/环境/命名/约定等），宁缺毋滥，不虚构；
            - experiences 是绑定任务类型的可复用测试经验（如 test_case_extraction / api_test / ui_test）；
            - preferences 是用户明确表达、跨会话有用的偏好/约定；
            - knowledge 是对话中沉淀的可复用测试经验/踩坑结论；
            - 没有可提炼内容对应数组给 []，不要输出其他文字。
            """;

    private final AgentAiClient aiClient;
    private final FactMemoryService factService;
    private final ExperienceMemoryService experienceService;
    private final KnowledgeService knowledgeService;
    private final MemoryIndexer indexer;
    private final EpisodeRecorder episodeRecorder;
    private final MessageService messageService;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<Long> extracted = ConcurrentHashMap.newKeySet();

    public MemoryDistiller(AgentAiClient aiClient, FactMemoryService factService,
                           ExperienceMemoryService experienceService, MemoryIndexer indexer,
                           EpisodeRecorder episodeRecorder, MessageService messageService,
                           AgentProperties properties, KnowledgeService knowledgeService) {
        this.aiClient = aiClient;
        this.factService = factService;
        this.experienceService = experienceService;
        this.knowledgeService = knowledgeService;
        this.indexer = indexer;
        this.episodeRecorder = episodeRecorder;
        this.messageService = messageService;
        this.properties = properties;
    }

    public void extractIfNeeded(Long conversationId, Long userId) {
        if (!properties.getDistill().isEnabled() || userId == null) {
            return;
        }
        if (extracted.contains(conversationId)) {
            return;
        }
        String tail = historyTail(conversationId);
        if (tail.length() < properties.getDistill().getMinCharacters()) {
            return;
        }
        try {
            episodeRecorder.record(userId, userId, "conversation",
                    "conv:" + conversationId, tail);
            Map<String, Object> result = parse(aiClient.chat(PROMPT, tail));
            for (Object item : list(result, "facts")) {
                Map<String, Object> f = cast(item);
                MemoryFact fact = factService.upsert(userId, userId,
                        str(f.get("entity_id")), str(f.get("attribute")), str(f.get("value")),
                        "conversation", String.valueOf(conversationId), dbl(f.get("confidence")));
                indexer.indexFact(fact);
            }
            for (Object item : list(result, "experiences")) {
                Map<String, Object> e = cast(item);
                MemoryExperience exp = experienceService.saveCandidate(userId, userId,
                        str(e.get("task_type")), str(e.get("rule")),
                        "会话#" + conversationId, dbl(e.get("confidence")));
                indexer.indexExperience(exp);
            }
            for (Object item : list(result, "preferences")) {
                Map<String, Object> p = cast(item);
                String key = str(p.get("key"));
                String content = str(p.get("content"));
                if (key.isBlank() || content.isBlank()) {
                    continue;
                }
                factService.upsert(userId, userId, "user", key, content,
                        "conversation", String.valueOf(conversationId), 0.9);
            }
            for (Object item : list(result, "knowledge")) {
                Map<String, Object> k = cast(item);
                String title = str(k.get("title"));
                String content = str(k.get("content"));
                String category = str(k.get("category"));
                if (title.isBlank() || content.isBlank()) {
                    continue;
                }
                knowledgeService.saveCandidate(userId, title, content,
                        category.isBlank() ? "经验教训" : category, List.of("auto"));
            }
            extracted.add(conversationId);
            log.info("会话 {} 记忆提炼完成", conversationId);
        } catch (Exception e) {
            log.warn("会话 {} 记忆提炼失败: {}", conversationId, e.getMessage());
        }
    }

    private String historyTail(Long conversationId) {
        List<AgentMessage> all = messageService.list(conversationId);
        StringBuilder tail = new StringBuilder();
        int added = 0;
        for (int i = all.size() - 1; i >= 0 && added < 6; i--) {
            AgentMessage message = all.get(i);
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            tail.insert(0, "[" + message.getRole() + "] " + message.getContent() + "\n");
            added++;
        }
        return tail.toString();
    }

    private Map<String, Object> parse(String raw) {
        String cleaned = raw.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("提炼结果 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Map<String, Object> root, String key) {
        Object value = root.get(key);
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object item) {
        return item instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.8;
    }
}
