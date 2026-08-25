package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolExecutionService;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Tag("integration")
class AnnotationToolScannerIT {

    @Autowired
    private ToolRegistry registry;
    @Autowired
    private ToolExecutionService executionService;

    @Test
    void annotatedToolRegisteredAndExecutable() {
        ToolDefinition definition = registry.get("list_all_projects");
        assertNotNull(definition, "list_all_projects 应由 @AgentTool 注册");

        ToolResult result = executionService.execute("list_all_projects",
                Map.of(),
                ToolContext.builder().userId(1L).conversationId(1L).build(),
                false);
        assertEquals(ToolResultMeta.Status.SUCCESS, result.getStatus());
    }
}
