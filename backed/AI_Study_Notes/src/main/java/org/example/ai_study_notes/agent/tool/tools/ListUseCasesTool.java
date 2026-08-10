package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.vo.UseCaseVO;
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
 * 查询指定项目下的 API 用例列表。
 */
@Component
public class ListUseCasesTool implements ToolExecutor {

    private final UseCaseService useCaseService;

    public ListUseCasesTool(UseCaseService useCaseService) {
        this.useCaseService = useCaseService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_use_cases")
                .label("查询用例列表")
                .description("查询指定 API 项目下的测试用例列表，需要项目ID pid")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("pid", Map.of("type", "integer", "description", "API 项目ID")),
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
        Integer pid = Args.integer(args, "pid", null);
        List<UseCaseVO> useCases = useCaseService.getUseCases(pid);
        return ToolResult.success("list_use_cases", useCases, "共 " + useCases.size() + " 个用例");
    }
}
