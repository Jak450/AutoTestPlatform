package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.ProjectService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 删除 API 项目（需用户确认）。
 */
@Component
public class DeleteProjectTool implements ToolExecutor {

    private final ProjectService projectService;

    public DeleteProjectTool(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("delete_project")
                .label("删除项目")
                .description("删除指定 API 测试项目，需要项目ID id")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("id", Map.of("type", "integer", "description", "项目ID")),
                        "required", List.of("id")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("写入")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        Integer id = Args.integer(args, "id", null);
        projectService.deleteProject(id);
        return ToolResult.success("delete_project", Map.of("id", id), "项目删除成功");
    }
}
