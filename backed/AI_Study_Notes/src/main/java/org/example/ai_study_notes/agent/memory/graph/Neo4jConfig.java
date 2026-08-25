package org.example.ai_study_notes.agent.memory.graph;

import org.example.ai_study_notes.agent.config.AgentProperties;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 驱动与图仓储的 Spring 装配。Driver 懒连接，Neo4j 不可用时应用仍可启动。
 */
@Configuration
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(AgentProperties properties) {
        AgentProperties.Neo4j cfg = properties.getNeo4j();
        return Neo4jGraphRepository.createDriver(cfg.getUri(), cfg.getUser(), cfg.getPassword());
    }

    @Bean
    public Neo4jGraphRepository neo4jGraphRepository(Driver neo4jDriver) {
        return new Neo4jGraphRepository(neo4jDriver);
    }
}
