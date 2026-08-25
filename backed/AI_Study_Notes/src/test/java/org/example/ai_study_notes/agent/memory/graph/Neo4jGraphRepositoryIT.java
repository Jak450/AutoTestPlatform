package org.example.ai_study_notes.agent.memory.graph;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class Neo4jGraphRepositoryIT {

    private static Neo4jGraphRepository repo;
    private static Driver driver;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(isNeo4jUp(), "Neo4j 未运行，跳过");
        driver = Neo4jGraphRepository.createDriver("bolt://127.0.0.1:7687", "neo4j", "autotest123456");
        repo = new Neo4jGraphRepository(driver);
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    void upsertEntitiesAndRelationThenExpandOneHop() {
        String shopping = repo.upsertEntity("购物模块", "Module", 1L, List.of("商城购物"));
        String inventory = repo.upsertEntity("库存模块", "Module", 1L, List.of());
        repo.upsertRelation(shopping, RelationType.AFFECTS, inventory, "下单时校验库存", "需求文档#12", 1L);
        repo.confirmRelation(shopping, RelationType.AFFECTS, inventory, 1L);

        List<Neo4jGraphRepository.RelationHit> hits =
                repo.expand(shopping, 1L, 1);
        assertTrue(hits.stream().anyMatch(h -> h.objectEntityId().equals(inventory)));
        assertEquals(RelationType.AFFECTS, hits.get(0).predicate());
    }

    @Test
    void listConfirmDeleteAndDecayRelations() {
        String shopping = repo.upsertEntity("订单模块", "Module", 1L, List.of());
        String payment = repo.upsertEntity("支付模块", "Module", 1L, List.of());
        repo.upsertRelation(shopping, RelationType.DEPENDS_ON, payment, "下单依赖支付", "测试", 1L);

        var pending = repo.listUnconfirmedRelations(1L, 5);
        assertTrue(pending.stream().anyMatch(h -> h.subjectName().equals("订单模块")));

        repo.confirmRelation(shopping, RelationType.DEPENDS_ON, payment, 1L);
        assertTrue(repo.expand(shopping, 1L, 1).stream()
                .anyMatch(h -> h.objectName().equals("支付模块")));

        repo.deleteRelation(shopping, RelationType.DEPENDS_ON, payment, 1L);
        assertTrue(repo.expand(shopping, 1L, 1).stream()
                .noneMatch(h -> h.objectName().equals("支付模块")));
    }

    private static boolean isNeo4jUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 7687), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
