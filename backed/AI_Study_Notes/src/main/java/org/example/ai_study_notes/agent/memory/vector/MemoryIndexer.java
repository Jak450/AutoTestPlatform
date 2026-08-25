package org.example.ai_study_notes.agent.memory.vector;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.memory.embedding.EmbeddingClient;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 记忆向量索引：提炼/确认后的记忆 embedding 后写入 Qdrant。
 * embedding 或 Qdrant 不可用时降级——数据保留在 MySQL，仅记录 WARN。
 */
@Slf4j
@Service
public class MemoryIndexer {

    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore vectorStore;

    public MemoryIndexer(EmbeddingClient embeddingClient, QdrantVectorStore vectorStore) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public void indexFact(MemoryFact fact) {
        try {
            List<Float> vector = embeddingClient.embed(
                    fact.getEntityId() + " " + fact.getAttribute() + " " + fact.getFactValue());
            vectorStore.upsert(vectorStore.collectionName(QdrantVectorStore.COLLECTION_FACTS),
                    "fact:" + fact.getId(), vector,
                    Map.of("workspace_id", String.valueOf(fact.getWorkspaceId()),
                            "text", fact.getEntityId() + "." + fact.getAttribute() + " = " + fact.getFactValue(),
                            "confirmed", 1,
                            "entity_id", fact.getEntityId()));
        } catch (Exception e) {
            log.warn("事实索引失败（数据保留在 MySQL）fact={}: {}", fact.getId(), e.getMessage());
        }
    }

    public void indexExperience(MemoryExperience exp) {
        try {
            List<Float> vector = embeddingClient.embed(exp.getRuleText());
            vectorStore.upsert(vectorStore.collectionName(QdrantVectorStore.COLLECTION_EXPERIENCES),
                    "experience:" + exp.getId(), vector,
                    Map.of("workspace_id", String.valueOf(exp.getWorkspaceId()),
                            "text", exp.getRuleText(),
                            "confirmed", exp.getConfirmed(),
                            "task_type", exp.getTaskType()));
        } catch (Exception e) {
            log.warn("经验索引失败（数据保留在 MySQL）experience={}: {}", exp.getId(), e.getMessage());
        }
    }
}
