package org.example.ai_study_notes.agent.memory;

import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Tag("integration")
@Transactional
class FactMemoryServiceIT {

    @Autowired
    private FactMemoryService service;

    @Test
    void upsertVersioningOnRealDb() {
        MemoryFact v1 = service.upsert(1L, 1L, "shoe", "price", "500", "conversation", null, 0.9);
        MemoryFact v2 = service.upsert(1L, 1L, "shoe", "price", "600", "conversation", null, 0.9);
        assertEquals(1, v1.getVersion());
        assertEquals(2, v2.getVersion());
        assertEquals("600", service.findCurrent(1L, "shoe", "price").getFactValue());
    }
}
