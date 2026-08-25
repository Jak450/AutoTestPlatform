package org.example.ai_study_notes.agent.memory.vector;

import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class QdrantVectorStoreIT {

    private QdrantVectorStore store;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(isQdrantUp(), "Qdrant 未运行，跳过");
        AgentProperties props = new AgentProperties();
        props.getQdrant().setHost("127.0.0.1");
        props.getQdrant().setPort(6334);
        props.getQdrant().setCollectionPrefix("it_");
        props.getEmbedding().setDimensions(4);
        store = new QdrantVectorStore(props);
    }

    @Test
    void upsertAndSearch() {
        store.upsert(store.collectionName("facts"), "f1", List.of(1f, 0f, 0f, 0f), Map.of("workspace_id", "1", "confirmed", 1));
        store.upsert(store.collectionName("facts"), "f2", List.of(0f, 1f, 0f, 0f), Map.of("workspace_id", "1", "confirmed", 1));
        List<QdrantVectorStore.Hit> hits = store.search(store.collectionName("facts"), List.of(0.9f, 0.1f, 0f, 0f), "1", null, 2);
        assertFalse(hits.isEmpty());
    }

    @Test
    void confirmedFilterMatchesIntegerPayload() {
        store.upsert(store.collectionName("experiences"), "e1", List.of(1f, 0f, 0f, 0f),
                Map.of("workspace_id", "1", "confirmed", 1));
        store.upsert(store.collectionName("experiences"), "e2", List.of(0f, 1f, 0f, 0f),
                Map.of("workspace_id", "1", "confirmed", 0));
        List<QdrantVectorStore.Hit> hits =
                store.search(store.collectionName("experiences"), List.of(0.9f, 0.1f, 0f, 0f), "1", true, 2);
        assertEquals(1, hits.size());
        assertEquals("e1", hits.get(0).id());
    }

    @Test
    void deleteByIdsRemovesPoints() {
        String col = store.collectionName("facts");
        store.upsert(col, "d1", List.of(1f, 0f, 0f, 0f), Map.of("workspace_id", "1"));
        store.deleteByIds(col, List.of("d1"));
        List<QdrantVectorStore.Hit> hits = store.search(col, List.of(1f, 0f, 0f, 0f), "1", null, 2);
        assertTrue(hits.stream().noneMatch(h -> h.id().equals("d1")));
    }

    private boolean isQdrantUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6334), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
