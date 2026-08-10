package org.example.ai_study_notes.agent.task;

import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPlanServiceTest {

    @TempDir
    Path tempDir;

    private TaskPlanService service;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setDataDir(tempDir.toString());
        service = new TaskPlanService(properties);
    }

    @Test
    void createPersistsMdWithFrontmatter() {
        TaskPlan plan = service.create(1L, 10L, "生成登录用例",
                List.of("解析文档", "生成草稿", "试跑", "保存"));
        assertEquals(TaskPlanService.STATUS_PENDING, plan.status());
        assertEquals(10L, plan.sourceConversationId());
        assertEquals(4, plan.items().size());
        assertTrue(Files.exists(service.userRoot(1L).resolve(plan.taskId() + ".md")));
    }

    @Test
    void updateStatusAndCheckItems() {
        TaskPlan plan = service.create(1L, 10L, "生成登录用例",
                List.of("解析文档", "生成草稿", "试跑", "保存"));
        TaskPlan updated = service.update(1L, plan.taskId(), TaskPlanService.STATUS_IN_PROGRESS,
                List.of("解析文档", "生成草稿"));
        assertEquals(TaskPlanService.STATUS_IN_PROGRESS, updated.status());
        assertTrue(updated.items().get(0).checked());
        assertTrue(updated.items().get(1).checked());
        assertFalse(updated.items().get(2).checked());
    }

    @Test
    void activePlanAndInjectable() {
        TaskPlan plan = service.create(1L, 10L, "生成登录用例", List.of("解析文档", "生成草稿"));
        assertEquals(plan.taskId(), service.activePlan(1L, 10L).taskId());
        String injected = service.injectable(1L, 10L);
        assertTrue(injected.contains("生成登录用例"));
        assertTrue(injected.contains("- [ ] 解析文档"));
        service.update(1L, plan.taskId(), TaskPlanService.STATUS_DONE, List.of("解析文档", "生成草稿"));
        assertNull(service.activePlan(1L, 10L));
        assertEquals("", service.injectable(1L, 10L));
    }

    @Test
    void listFiltersByConversation() {
        service.create(1L, 10L, "任务A", List.of("步骤1"));
        service.create(1L, 11L, "任务B", List.of("步骤1"));
        assertEquals(1, service.list(1L, 10L).size());
        assertEquals(2, service.list(1L, null).size());
    }

    @Test
    void deleteRemovesFile() {
        TaskPlan plan = service.create(1L, 10L, "任务A", List.of("步骤1"));
        service.delete(1L, plan.taskId());
        assertTrue(service.list(1L, null).isEmpty());
    }

    @Test
    void sanitizeBlocksTraversal() {
        assertFalse(service.sanitize("../secret").contains(".."));
        assertFalse(service.sanitize("a/b\\c").contains("/"));
        assertFalse(service.sanitize("a/b\\c").contains("\\"));
    }
}
