package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.UseCaseUpdateDTO;
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
 * 新增 API 用例（需用户确认）。注意：新增接口描述字段名为 desc。
 */
@Component
public class CreateUseCaseTool implements ToolExecutor {

    private final UseCaseService useCaseService;

    public CreateUseCaseTool(UseCaseService useCaseService) {
        this.useCaseService = useCaseService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("create_use_case")
                .label("新增用例")
                .description("新增一个 API 测试用例，需要 pid(项目ID)、name、url、method；header/param/assertStr/desc 可选")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "pid", Map.of("type", "integer", "description", "项目ID"),
                                "name", Map.of("type", "string", "description", "用例名称"),
                                "url", Map.of("type", "string", "description", "接口地址"),
                                "method", Map.of("type", "string", "enum", List.of("GET", "POST", "PUT", "DELETE", "PATCH")),
                                "header", Map.of("type", "string", "description", "请求头 JSON 字符串"),
                                "param", Map.of("type", "string", "description", "请求参数 JSON 字符串"),
                                "assertStr", Map.of("type", "string", "description", "断言 JSON 字符串"),
                                "desc", Map.of("type", "string", "description", "用例描述")),
                        "required", List.of("pid", "name", "url", "method")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("写入")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        UseCaseUpdateDTO dto = UseCaseUpdateDTO.builder()
                .pid(Args.integer(args, "pid", null))
                .name(Args.str(args, "name"))
                .url(Args.str(args, "url"))
                .method(Args.str(args, "method"))
                .header(Args.str(args, "header"))
                .param(Args.str(args, "param"))
                .assertStr(Args.str(args, "assertStr"))
                .desc(Args.str(args, "desc"))
                .build();
        useCaseService.addUseCase(dto);
        return ToolResult.success("create_use_case", dto, "用例创建成功: " + dto.getName());
    }
}
