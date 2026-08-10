package org.example.ai_study_notes.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 偏好合并：同主题内容并入已有记忆（追加 + 版本+1），重复内容不重复追加。
 */
class MemoryServiceTest {

    private MemoryMapper mapper;
    private MemoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MemoryMapper.class);
        service = new MemoryService(mapper);
    }

    @Test
    void mergeAppendsAndBumpsVersion() {
        MemoryEntry existing = MemoryEntry.builder()
                .id(1L).userId(1L).memKey("default_timeout")
                .contentMd("接口超时统一 30 秒").confirmed(1).version(1).build();
        when(mapper.selectOne(any())).thenReturn(existing);

        MemoryEntry merged = service.merge(1L, "default_timeout", "接口超时重试 2 次");

        assertEquals("接口超时统一 30 秒；接口超时重试 2 次", merged.getContentMd());
        assertEquals(2, merged.getVersion());
        verify(mapper).updateById(any(MemoryEntry.class));
    }

    @Test
    void mergeDoesNotDuplicateIdenticalContent() {
        MemoryEntry existing = MemoryEntry.builder()
                .id(1L).userId(1L).memKey("default_timeout")
                .contentMd("接口超时统一 30 秒").confirmed(1).version(1).build();
        when(mapper.selectOne(any())).thenReturn(existing);

        MemoryEntry merged = service.merge(1L, "default_timeout", "接口超时统一 30 秒");

        assertEquals("接口超时统一 30 秒", merged.getContentMd());
        assertEquals(2, merged.getVersion());
    }
}
