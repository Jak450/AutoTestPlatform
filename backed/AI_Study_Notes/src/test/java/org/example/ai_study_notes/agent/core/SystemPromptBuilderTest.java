package org.example.ai_study_notes.agent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptBuilderTest {

    @Test
    void buildWithMemoryContainsMemorySection() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        String prompt = builder.buildWithMemory(
                "相关记忆：\n- 事实: shoe.price = 500\n- 经验: 提取测试点时需考虑兼容性",
                List.of("技能正文"), "任务计划");
        assertTrue(prompt.contains("事实: shoe.price = 500"));
        assertTrue(prompt.contains("技能正文"));
        assertTrue(prompt.contains("任务计划"));
    }
}
