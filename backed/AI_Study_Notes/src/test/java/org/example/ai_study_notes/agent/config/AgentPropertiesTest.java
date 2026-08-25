package org.example.ai_study_notes.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPropertiesTest {

    @TestConfiguration
    @EnableConfigurationProperties(AgentProperties.class)
    static class TestConfig {
    }

    @Test
    void embeddingAndQdrantBind() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "agent.embedding.base-url=http://127.0.0.1:11434/v1",
                        "agent.embedding.model=bge-m3",
                        "agent.embedding.dimensions=1024",
                        "agent.qdrant.host=127.0.0.1",
                        "agent.qdrant.port=6334",
                        "agent.distill.enabled=false",
                        "agent.distill.min-characters=40",
                        "agent.neo4j.uri=bolt://127.0.0.1:7687",
                        "agent.neo4j.user=neo4j",
                        "agent.neo4j.password=autotest123456",
                        "agent.maintenance.enabled=true",
                        "agent.maintenance.episode-retention-days=90",
                        "agent.maintenance.candidate-retention-days=7",
                        "agent.confirm.window-hours=24",
                        "agent.confirm.ask-limit=5")
                .run(ctx -> {
                    AgentProperties props = ctx.getBean(AgentProperties.class);
                    assertEquals("bge-m3", props.getEmbedding().getModel());
                    assertEquals(1024, props.getEmbedding().getDimensions());
                    assertEquals("127.0.0.1", props.getQdrant().getHost());
                    assertEquals(6334, props.getQdrant().getPort());
                    assertEquals(false, props.getDistill().isEnabled());
                    assertEquals(40, props.getDistill().getMinCharacters());
                    assertEquals("bolt://127.0.0.1:7687", props.getNeo4j().getUri());
                    assertEquals("neo4j", props.getNeo4j().getUser());
                    assertEquals(90, props.getMaintenance().getEpisodeRetentionDays());
                    assertEquals(7, props.getMaintenance().getCandidateRetentionDays());
                    assertEquals(24, props.getConfirm().getWindowHours());
                    assertEquals(5, props.getConfirm().getAskLimit());
                });
    }
}
