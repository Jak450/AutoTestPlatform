package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.entity.UseCase;
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
 * 查询单个 API 用例详情。
 */
@Component
public class GetUseCaseTool implements ToolExecutor {

    private final UseCaseService useCaseService;

    public GetUseCaseTool(UseCaseService useCaseService) {
        this.useCaseService = useCaseService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_use_case")
                .label("查询用例详情")
                .description("查询单个 API 测试用例的完整详情（请求头、参数、断言），需要用例ID")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("id", Map.of("type", "integer", "description", "API 用例ID")),
                        "required", List.of("id")))
                .permission(ToolPermission.READ)
                .category("查询")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        Integer id = Args.integer(args, "id", null);
        UseCase useCase = useCaseService.getUseCasesById(id);
        if (useCase == null) {
            return ToolResult.error("get_use_case", "用例不存在: " + id,
                    org.example.ai_study_notes.agent.contract.ToolResultMeta.ErrorType.NOT_FOUND,
                    org.example.ai_study_notes.agent.contract.ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                    System.currentTimeMillis());
        }
        return ToolResult.success("get_use_case", useCase, "用例: " + useCase.getName());
    }
}
