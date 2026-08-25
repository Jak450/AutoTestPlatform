package org.example.ai_study_notes.agent.memory;

import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Tag("integration")
@Transactional
class MemoryMapperIT {

    @Autowired
    private MemoryFactMapper factMapper;
    @Autowired
    private MemoryExperienceMapper experienceMapper;

    @Test
    void factInsertAndSelect() {
        MemoryFact fact = MemoryFact.builder()
                .workspaceId(1L).userId(1L).entityId("shoe")
                .attribute("price").factValue("500")
                .validFrom(LocalDateTime.now())
                .validTo(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
                .version(1).sourceType("conversation")
                .confidence(BigDecimal.valueOf(0.9))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        factMapper.insert(fact);
        MemoryFact got = factMapper.selectById(fact.getId());
        assertEquals("500", got.getFactValue());
        assertEquals("shoe", got.getEntityId());
    }

    @Test
    void experienceInsertAndSelect() {
        MemoryExperience exp = MemoryExperience.builder()
                .workspaceId(1L).userId(1L).taskType("test_case_extraction")
                .ruleText("提取测试点时需考虑兼容性测试点")
                .confirmed(1)
                .confidence(BigDecimal.valueOf(0.8))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        experienceMapper.insert(exp);
        MemoryExperience got = experienceMapper.selectById(exp.getId());
        assertEquals("test_case_extraction", got.getTaskType());
    }
}
