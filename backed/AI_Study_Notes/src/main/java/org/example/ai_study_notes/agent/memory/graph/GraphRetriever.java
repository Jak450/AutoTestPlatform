package org.example.ai_study_notes.agent.memory.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 关系检索：查询命中实体提示词 → 实体消歧 → 1-2 跳展开 → 格式化为注入文本。
 * 关系是独立注入段，不走 RRF 融合（按实体命中展开）。
 */
@Slf4j
@Service
public class GraphRetriever {

    private final Neo4jGraphRepository repository;

    public GraphRetriever(Neo4jGraphRepository repository) {
        this.repository = repository;
    }

    public String expandForQuery(String query, Long workspaceId, int budgetChars) {
        try {
            List<String> sections = new ArrayList<>();
            for (String entityId : repository.findEntityIdsByQuery(query, workspaceId)) {
                for (Neo4jGraphRepository.RelationHit hit : repository.expand(entityId, workspaceId, 2)) {
                    sections.add(format(hit.subjectName(), hit.predicate(), hit.objectName(), hit.context()));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String section : sections) {
                if (sb.length() + section.length() + 1 > budgetChars) {
                    break;
                }
                sb.append(section).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("关系展开失败: {}", e.getMessage());
            return "";
        }
    }

    private String format(String subjectName, RelationType predicate, String objectName, String context) {
        return "关系: " + subjectName + " --" + predicate + "--> " + objectName
                + (context == null || context.isBlank() ? "" : "（" + context + "）");
    }
}
