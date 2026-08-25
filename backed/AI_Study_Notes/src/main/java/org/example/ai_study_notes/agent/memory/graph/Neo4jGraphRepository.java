package org.example.ai_study_notes.agent.memory.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/**
 * Neo4j 图仓储：实体/关系 upsert 与 1-2 跳展开。只封装 Cypher，业务编排在 Extractor/Retriever。
 */
public class Neo4jGraphRepository {

    public static Driver createDriver(String uri, String user, String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }
}
