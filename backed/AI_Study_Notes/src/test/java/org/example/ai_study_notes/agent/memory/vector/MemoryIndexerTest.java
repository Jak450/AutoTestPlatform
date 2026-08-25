package org.example.ai_study_notes.agent.memory.vector;

import org.example.ai_study_notes.agent.memory.embedding.EmbeddingClient;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryIndexerTest {

    @Test
    void indexFactUpsertsVectorWithPayload() {
        EmbeddingClient embedding = mock(EmbeddingClient.class);
        when(embedding.embed(any())).thenReturn(List.of(1f, 0f, 0f));
        QdrantVectorStore store = mock(QdrantVectorStore.class);
        MemoryIndexer indexer = new MemoryIndexer(embedding, store);

        MemoryFact fact = MemoryFact.builder().id(1L).workspaceId(1L)
                .entityId("shoe").attribute("price").factValue("500")
                .confidence(BigDecimal.valueOf(0.9)).build();
        indexer.indexFact(fact);

        verify(store).upsert(eq("facts"), eq("fact:1"), any(), any());
    }

    @Test
    void indexExperienceUpsertsCandidateWithConfirmedZero() {
        EmbeddingClient embedding = mock(EmbeddingClient.class);
        when(embedding.embed(any())).thenReturn(List.of(1f, 0f, 0f));
        QdrantVectorStore store = mock(QdrantVectorStore.class);
        MemoryIndexer indexer = new MemoryIndexer(embedding, store);

        MemoryExperience exp = MemoryExperience.builder().id(2L).workspaceId(1L)
                .taskType("test_case_extraction").ruleText("提取测试点时需考虑兼容性")
                .confirmed(0).build();
        indexer.indexExperience(exp);

        verify(store).upsert(eq("experiences"), eq("experience:2"), any(), any());
    }
}
