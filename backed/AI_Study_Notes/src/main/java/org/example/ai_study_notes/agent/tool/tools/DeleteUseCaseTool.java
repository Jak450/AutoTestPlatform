package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 删除 API 用例（需用户确认）。
 */
@Component
public class DeleteUseCaseTool implements ToolExecutor {

    private final UseCaseService useCaseService;

    public DeleteUseCaseTool(UseCaseService useCaseService) {
        this.useCaseService = useCaseService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("delete_use_case")
                .label("删除用例")
                .description("删除指定 API 测试用例，需要用例ID id")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("id", Map.of("type", "integer", "description", "用例ID")),
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
        useCaseService.deleteUseCase(id);
        return ToolResult.success("delete_use_case", Map.of("id", id), "用例删除成功");
    }
}
