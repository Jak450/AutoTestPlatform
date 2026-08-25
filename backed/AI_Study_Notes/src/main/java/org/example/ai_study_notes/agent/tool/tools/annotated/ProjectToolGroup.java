package org.example.ai_study_notes.agent.tool.tools.annotated;

import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.annotation.AgentTool;
import org.example.ai_study_notes.service.ProjectService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 注解化工具示例：直接复用现有 ProjectService 方法，无需手写 ToolExecutor 类。
 */
@Component
public class ProjectToolGroup {

    private final ProjectService projectService;

    public ProjectToolGroup(ProjectService projectService) {
        this.projectService = projectService;
    }

    @AgentTool(name = "list_all_projects", label = "查询全部项目",
               description = "查询当前平台所有 API 测试项目",
               permission = ToolPermission.READ, category = "查询")
    public List<ProjectVO> listAllProjects() {
        return projectService.getProject();
    }
}
