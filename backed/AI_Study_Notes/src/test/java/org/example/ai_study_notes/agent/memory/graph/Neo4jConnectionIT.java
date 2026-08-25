package org.example.ai_study_notes.agent.memory.graph;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class Neo4jConnectionIT {

    @Test
    void driverConnectsAndReturnsServerVersion() {
        Assumptions.assumeTrue(isNeo4jUp(), "Neo4j 未运行，跳过");
        try (org.neo4j.driver.Driver driver = Neo4jGraphRepository.createDriver(
                "bolt://127.0.0.1:7687", "neo4j", "autotest123456")) {
            try (org.neo4j.driver.Session session = driver.session()) {
                long one = session.run("RETURN 1 AS one").single().get("one").asLong();
                assertEquals(1L, one);
            }
        }
    }

    private boolean isNeo4jUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 7687), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
