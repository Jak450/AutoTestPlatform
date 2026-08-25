package org.example.ai_study_notes.agent.memory.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphRetrieverTest {

    @Test
    void expandsWhenQueryMentionsEntity() {
        Neo4jGraphRepository repo = mock(Neo4jGraphRepository.class);
        when(repo.findEntityIdsByQuery("分析购物模块需求", 1L)).thenReturn(List.of("s1"));
        when(repo.expand("s1", 1L, 2)).thenReturn(List.of(
                new Neo4jGraphRepository.RelationHit("s1", "购物模块", "o1", "库存模块",
                        RelationType.AFFECTS, "下单时校验库存")));
        GraphRetriever retriever = new GraphRetriever(repo);

        String out = retriever.expandForQuery("分析购物模块需求", 1L, 512);
        assertTrue(out.contains("购物模块 --AFFECTS--> 库存模块"));
    }
}
