package org.example.ai_study_notes.agent.memory.graph;

import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelationExtractorTest {

    @Test
    void parsesTriplesAndCallsRepository() {
        AgentAiClient aiClient = mock(AgentAiClient.class);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("{\"relations\":[{\"subject\":\"购物模块\",\"predicate\":\"AFFECTS\","
                        + "\"object\":\"库存模块\",\"context\":\"下单时校验库存\"}]}");
        Neo4jGraphRepository repo = mock(Neo4jGraphRepository.class);
        when(repo.resolveEntity("购物模块", 1L)).thenReturn("s1");
        when(repo.resolveEntity("库存模块", 1L)).thenReturn("o1");

        RelationExtractor extractor = new RelationExtractor(aiClient, repo);
        int saved = extractor.extract("购物模块影响库存模块，因为下单时要校验库存", 1L);

        assertEquals(1, saved);
        verify(repo).upsertRelation(
                "s1", RelationType.AFFECTS, "o1", "下单时校验库存", "对话", 1L);
    }
}
