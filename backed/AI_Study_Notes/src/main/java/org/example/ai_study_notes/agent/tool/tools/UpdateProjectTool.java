package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.ProjectDTO;
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
 * 更新 API 项目（需用户确认）。
 */
@Component
public class UpdateProjectTool implements ToolExecutor {

    private final ProjectService projectService;

    public UpdateProjectTool(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("update_project")
                .label("更新项目")
                .description("更新 API 测试项目的名称，需要项目ID id 与新名称 name")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of("type", "integer", "description", "项目ID"),
                                "name", Map.of("type", "string", "description", "新项目名称")),
                        "required", List.of("id", "name")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("写入")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        ProjectDTO dto = ProjectDTO.builder()
                .id(Args.integer(args, "id", null))
                .name(Args.str(args, "name"))
                .build();
        projectService.updateProject(dto);
        return ToolResult.success("update_project", dto, "项目更新成功");
    }
}
