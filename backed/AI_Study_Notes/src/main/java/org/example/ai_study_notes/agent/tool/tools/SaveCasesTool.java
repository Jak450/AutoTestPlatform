package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.generator.TestCaseGeneratorService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 保存用例到用例库（需用户确认）。
 */
@Component
public class SaveCasesTool implements ToolExecutor {

    private final TestCaseGeneratorService generatorService;

    public SaveCasesTool(TestCaseGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("save_cases")
                .label("保存用例")
                .description("把确认过的用例草稿批量保存到用例库，需要 cases 数组与 projectId")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "cases", Map.of("type", "array", "items", Map.of("type", "object")),
                                "projectId", Map.of("type", "integer", "description", "目标项目ID")),
                        "required", List.of("cases", "projectId")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("写入")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<Map<String, Object>> cases = Args.listOfMaps(args, "cases");
        Integer projectId = Args.integer(args, "projectId", null);
        int saved = generatorService.saveCases(cases, projectId);
        return ToolResult.success("save_cases", Map.of("saved", saved), "已保存 " + saved + " 条用例");
    }
}
