package org.example.ai_study_notes.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
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
            你是 AutoTestPlatform 的记忆提炼器。从对话中提炼两类内容：
            1. preference：用户明确表达的、跨会话有用的偏好或约定（例如默认项目、命名风格、断言习惯、常用环境）。
            2. knowledge：对话中沉淀出的可复用测试经验、知识结论或踩坑（例如某个接口的测试要点、某类缺陷的排查经验）。
            knowledge 必须同时满足：
            - 用户明确陈述（不是模型自己推理或泛泛而谈）；
            - 具体可复用：涉及具体接口/模块/场景/断言等，对后续测试有指导价值；
            - 不臆造：只提炼对话中出现的内容，不要自行补充或编造；
            - 排除空话套话（如"需要仔细测试""注意异常情况"这类无信息量内容）。
            宁可少提炼，不要错提炼；不确定的内容一律不输出。
            只输出一个 JSON 数组，preference 最多 2 条，knowledge 最多 2 条，knowledge 格式：
            {"type": "knowledge", "category": "分类（如 经验教训/测试理论/项目规范）", "title": "简短标题",
             "content": "2-4 句话的知识结论",
             "action": "create | append | skip",
             "targetTitle": "仅当 action=append 时填写，必须是上面"已有知识库文档"列表中的标题"}
            其中：
            - create：新主题，知识库没有相关文档，新建；
            - append：与知识库某条文档同主题（相关补充），追加到该文档（targetTitle 填其标题）；
            - skip：与知识库某条文档内容重复，不入库。
            preference 格式：
            {"type": "preference", "key": "snake_case 键名", "content": "一句话描述"}
            没有可提炼的内容就输出 []。不要输出其他文字。
            """;

    private final AgentAiClient aiClient;
    private final MemoryService memoryService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;
    private final Set<Long> extractedConversations = ConcurrentHashMap.newKeySet();

    public MemoryExtractor(AgentAiClient aiClient, MemoryService memoryService,
                           KnowledgeService knowledgeService, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.memoryService = memoryService;
        this.knowledgeService = knowledgeService;
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
            StringBuilder prompt = new StringBuilder(PROMPT);
            java.util.List<org.example.ai_study_notes.agent.knowledge.KnowledgeDoc> existing =
                    knowledgeService.list(userId, null, false);
            if (!existing.isEmpty()) {
                prompt.append("\n\n已有知识库文档（判断 action 时参考，最多列出 10 条）：\n");
                int count = 0;
                for (org.example.ai_study_notes.agent.knowledge.KnowledgeDoc doc : existing) {
                    if (count++ >= 10) {
                        break;
                    }
                    String snippet = doc.snippet() == null || doc.snippet().isEmpty()
                            ? doc.content() : doc.snippet();
                    if (snippet != null && snippet.length() > 100) {
                        snippet = snippet.substring(0, 100) + "…";
                    }
                    prompt.append("- [").append(doc.category()).append("] ")
                            .append(doc.title()).append(": ").append(snippet).append('\n');
                }
            }
            String raw = aiClient.chat(prompt.toString(), historyTail);
            List<Map<String, Object>> candidates = parse(raw);
            int savedPreferences = 0;
            int savedKnowledge = 0;
            for (Map<String, Object> candidate : candidates) {
                String type = String.valueOf(candidate.getOrDefault("type", "preference"));
                if ("knowledge".equals(type)) {
                    String title = String.valueOf(candidate.get("title"));
                    String content = String.valueOf(candidate.get("content"));
                    String category = String.valueOf(candidate.getOrDefault("category", "经验教训"));
                    if (isBlank(title) || isBlank(content) || "null".equals(title) || "null".equals(content)) {
                        continue;
                    }
                    try {
                        String action = String.valueOf(candidate.getOrDefault("action", "create"));
                        if ("skip".equals(action)) {
                            log.info("知识重复，跳过入库: {}", title.trim());
                            continue;
                        }
                        if ("append".equals(action)) {
                            String targetTitle = String.valueOf(candidate.getOrDefault("targetTitle", ""));
                            org.example.ai_study_notes.agent.knowledge.KnowledgeDoc target =
                                    knowledgeService.findByTitle(userId, targetTitle);
                            if (target != null) {
                                knowledgeService.append(userId, target.category(), target.slug(),
                                        title.trim(), content.trim());
                                savedKnowledge++;
                                log.info("知识已追加到已有文档: [{}] {}", target.category(), target.title());
                                continue;
                            }
                            log.info("append 目标未找到，退回新建: {}", title.trim());
                        }
                        // create（或 append 目标缺失）：相似检测后 去重跳过 / 新建
                        KnowledgeService.SmartSaveResult result = knowledgeService.saveSmart(
                                userId, title.trim(), content.trim(), category, java.util.List.of("auto"));
                        switch (result.action()) {
                            case "created" -> savedKnowledge++;
                            case "appended" -> {
                                savedKnowledge++;
                                log.info("知识已追加到已有文档: [{}] {}", result.doc().category(), result.doc().title());
                            }
                            case "skipped_duplicate" ->
                                    log.info("知识重复，跳过入库: {}（与已有文档 [{}] {} 相似度 {}）",
                                            title.trim(), result.doc().category(), result.doc().title(),
                                            result.match() == null ? 0 : result.match().score());
                            default -> { }
                        }
                    } catch (IllegalArgumentException e) {
                        log.info("知识自动入库跳过（已存在或内容无效）: {}", e.getMessage());
                    } catch (Exception e) {
                        log.warn("知识自动入库失败: {}", e.getMessage());
                    }
                    continue;
                }
                String key = String.valueOf(candidate.get("key"));
                String content = String.valueOf(candidate.get("content"));
                if (isBlank(key) || isBlank(content) || "null".equals(key) || "null".equals(content)) {
                    continue;
                }
                try {
                    MemoryEntry entry = memoryService.saveCandidate(userId, key.trim(), content.trim());
                    if (entry != null) {
                        savedPreferences++;
                    }
                } catch (Exception e) {
                    log.warn("偏好候选保存失败，继续处理其余条目: {}", e.getMessage());
                }
            }
            extractedConversations.add(conversationId);
            log.info("会话 {} 自动提炼候选：偏好 {} 条，知识 {} 条", conversationId, savedPreferences, savedKnowledge);
        } catch (Exception e) {
            log.warn("记忆自动提炼失败 conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
