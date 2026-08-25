package org.example.ai_study_notes.agent.memory.retrieval;

import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.FactMemoryService;
import org.example.ai_study_notes.agent.memory.embedding.EmbeddingClient;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRetrieverTest {

    @Test
    void retrieveFusesVectorAndKeywordRanks() {
        EmbeddingClient embedding = mock(EmbeddingClient.class);
        when(embedding.embed(anyString())).thenReturn(List.of(1f, 0f));
        QdrantVectorStore store = mock(QdrantVectorStore.class);
        when(store.search(eq("facts"), any(), eq("1"), any(), anyInt()))
                .thenReturn(List.of(new QdrantVectorStore.Hit("10", 0.9,
                        java.util.Map.of("entity_id", "shoe"))));
        when(store.search(eq("experiences"), any(), eq("1"), eq(true), anyInt()))
                .thenReturn(List.of());
        FactMemoryService factService = mock(FactMemoryService.class);
        when(factService.searchKeyword(eq(1L), anyString())).thenReturn(List.of());
        ExperienceMemoryService experienceService = mock(ExperienceMemoryService.class);
        when(experienceService.searchConfirmedKeyword(eq(1L), anyString())).thenReturn(List.of());
        KnowledgeService knowledge = mock(KnowledgeService.class);
        when(knowledge.search(any(), any(), anyInt())).thenReturn(List.of());

        MemoryRetriever retriever = new MemoryRetriever(
                factService, experienceService, knowledge, embedding, store);
        List<MemoryRetriever.RankedItem> items = retriever.retrieve(1L, 1L, "鞋子价格", 5);

        assertFalse(items.isEmpty());
        assertEquals("fact", items.get(0).type());
    }
}
