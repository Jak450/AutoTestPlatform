package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.ProjectService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询 API 项目列表。
 */
@Component
public class ListProjectsTool implements ToolExecutor {

    private final ProjectService projectService;

    public ListProjectsTool(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_projects")
                .label("查询项目列表")
                .description("查询当前平台所有 API 测试项目列表")
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .permission(ToolPermission.READ)
                .category("查询")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<ProjectVO> projects = projectService.getProject();
        return ToolResult.success("list_projects", projects, "共 " + projects.size() + " 个 API 项目");
    }
}
