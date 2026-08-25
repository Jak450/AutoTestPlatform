package org.example.ai_study_notes.agent.memory.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 业务关系抽取器：LLM 输出受控词表三元组 → 实体消歧（命中复用，未命中新建）→ 落图（候选，需确认）。
 */
@Slf4j
@Service
public class RelationExtractor {

    private static final String PROMPT = """
            你是业务关系抽取器。从文本中抽取实体之间的业务关系，只输出 JSON：
            {"relations":[{"subject":"...","predicate":"AFFECTS","object":"...","context":"一句话原因"}]}
            谓词只能取：DEPENDS_ON(依赖) / AFFECTS(影响) / CONTAINED_IN(包含) /
            ASSOCIATED_WITH(关联) / BELONGS_TO(归属) / REFERENCES(引用)。
            主语/宾语必须是业务实体（模块/需求/业务对象/文档），宁缺毋滥，不虚构。
            没有关系输出 {"relations":[]}，不要输出其他文字。
            """;

    private final AgentAiClient aiClient;
    private final Neo4jGraphRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RelationExtractor(AgentAiClient aiClient, Neo4jGraphRepository repository) {
        this.aiClient = aiClient;
        this.repository = repository;
    }

    public int extract(String text, Long workspaceId) {
        try {
            Map<String, Object> root = parse(aiClient.chat(PROMPT, text));
            List<?> relations = root.get("relations") instanceof List<?> list ? list : List.of();
            int saved = 0;
            for (Object item : relations) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String subject = str(map.get("subject"));
                String object = str(map.get("object"));
                String predicate = str(map.get("predicate"));
                String context = str(map.get("context"));
                if (subject.isBlank() || object.isBlank() || !RelationType.isValid(predicate)) {
                    continue;
                }
                String subjectId = repository.resolveEntity(subject, workspaceId);
                if (subjectId == null) {
                    subjectId = repository.upsertEntity(subject, "Concept", workspaceId, List.of());
                }
                String objectId = repository.resolveEntity(object, workspaceId);
                if (objectId == null) {
                    objectId = repository.upsertEntity(object, "Concept", workspaceId, List.of());
                }
                repository.upsertRelation(subjectId, RelationType.valueOf(predicate),
                        objectId, context, "对话", workspaceId);
                saved++;
            }
            return saved;
        } catch (Exception e) {
            log.warn("关系抽取失败: {}", e.getMessage());
            return 0;
        }
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
            log.warn("关系 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
