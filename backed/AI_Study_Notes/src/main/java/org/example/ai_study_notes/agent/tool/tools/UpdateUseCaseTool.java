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
 * 更新 API 用例（需用户确认）。
 */
@Component
public class UpdateUseCaseTool implements ToolExecutor {

    private final UseCaseService useCaseService;

    public UpdateUseCaseTool(UseCaseService useCaseService) {
        this.useCaseService = useCaseService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("update_use_case")
                .label("更新用例")
                .description("更新 API 测试用例，需要 id；name/url/method/header/param/assertStr/description 可选")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of("type", "integer", "description", "用例ID"),
                                "name", Map.of("type", "string"),
                                "url", Map.of("type", "string"),
                                "method", Map.of("type", "string", "enum", List.of("GET", "POST", "PUT", "DELETE", "PATCH")),
                                "header", Map.of("type", "string"),
                                "param", Map.of("type", "string"),
                                "assertStr", Map.of("type", "string"),
                                "description", Map.of("type", "string")),
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
        UseCase useCase = new UseCase();
        useCase.setId(Args.integer(args, "id", null));
        useCase.setName(Args.str(args, "name"));
        useCase.setUrl(Args.str(args, "url"));
        useCase.setMethod(Args.str(args, "method"));
        useCase.setHeader(Args.str(args, "header"));
        useCase.setParam(Args.str(args, "param"));
        useCase.setAssertStr(Args.str(args, "assertStr"));
        useCase.setDescription(Args.str(args, "description"));
        useCaseService.updateUseCase(useCase);
        return ToolResult.success("update_use_case", useCase, "用例更新成功");
    }
}
