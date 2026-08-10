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
 * 新增 API 项目（需用户确认）。
 */
@Component
public class CreateProjectTool implements ToolExecutor {

    private final ProjectService projectService;

    public CreateProjectTool(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("create_project")
                .label("新增项目")
                .description("新增一个 API 测试项目，需要项目名称 name")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string", "description", "项目名称")),
                        "required", List.of("name")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("写入")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        ProjectDTO dto = ProjectDTO.builder().name(Args.str(args, "name")).build();
        projectService.addProject(dto);
        return ToolResult.success("create_project", dto, "项目创建成功: " + dto.getName());
    }
}
