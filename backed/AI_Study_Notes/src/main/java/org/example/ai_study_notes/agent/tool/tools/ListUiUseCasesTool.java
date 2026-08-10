package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.UiDTO.UiUseCaseDTO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.UIUseCaseService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询指定项目下的 UI 用例列表。
 */
@Component
public class ListUiUseCasesTool implements ToolExecutor {

    private final UIUseCaseService uiUseCaseService;

    public ListUiUseCasesTool(UIUseCaseService uiUseCaseService) {
        this.uiUseCaseService = uiUseCaseService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_ui_use_cases")
                .label("查询UI用例列表")
                .description("查询指定 UI 项目下的测试用例列表，需要项目ID pid")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("pid", Map.of("type", "string", "description", "UI 项目ID")),
                        "required", List.of("pid")))
                .permission(ToolPermission.READ)
                .category("查询")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        String pid = Args.str(args, "pid");
        List<UiUseCaseDTO> useCases = uiUseCaseService.getUseCasesByProjectId(pid);
        return ToolResult.success("list_ui_use_cases", useCases, "共 " + useCases.size() + " 个 UI 用例");
    }
}
