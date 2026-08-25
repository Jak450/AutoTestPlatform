package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FactMemoryServiceTest {

    private static final LocalDateTime OPEN_END = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @Test
    void sameValueDoesNotCreateNewVersion() {
        MemoryFactMapper mapper = mock(MemoryFactMapper.class);
        MemoryFact current = MemoryFact.builder().id(1L).workspaceId(1L).entityId("shoe")
                .attribute("price").factValue("500").version(1)
                .validTo(OPEN_END).build();
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);

        FactMemoryService service = new FactMemoryService(mapper);
        MemoryFact result = service.upsert(1L, 1L, "shoe", "price", "500", "conversation", null, 0.9);

        assertEquals(current, result);
        verify(mapper, never()).insert(any(MemoryFact.class));
    }

    @Test
    void changedValueArchivesOldAndInsertsNew() {
        MemoryFactMapper mapper = mock(MemoryFactMapper.class);
        MemoryFact current = MemoryFact.builder().id(1L).workspaceId(1L).entityId("shoe")
                .attribute("price").factValue("500").version(1)
                .validTo(OPEN_END).build();
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);

        FactMemoryService service = new FactMemoryService(mapper);
        MemoryFact result = service.upsert(1L, 1L, "shoe", "price", "600", "conversation", null, 0.9);

        assertEquals(2, result.getVersion());
        verify(mapper).updateById(any(MemoryFact.class));
        verify(mapper).insert(any(MemoryFact.class));
    }
}
