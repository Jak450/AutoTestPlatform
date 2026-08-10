package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.generator.TestCaseGeneratorService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验用例草稿的 schema 与业务规则。
 */
@Component
public class ValidateCasesTool implements ToolExecutor {

    private final TestCaseGeneratorService generatorService;

    public ValidateCasesTool(TestCaseGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("validate_cases")
                .label("校验用例")
                .description("校验用例草稿数组（name/url/method 等字段），返回校验错误列表")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("cases", Map.of("type", "array", "items", Map.of("type", "object"), "description", "用例对象数组")),
                        "required", List.of("cases")))
                .permission(ToolPermission.READ)
                .category("生成")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<Map<String, Object>> cases = Args.listOfMaps(args, "cases");
        List<String> errors = generatorService.validateCases(cases);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", errors.isEmpty());
        data.put("count", cases == null ? 0 : cases.size());
        data.put("errors", errors);
        return ToolResult.success("validate_cases", data,
                errors.isEmpty() ? "校验通过" : "校验失败: " + String.join("; ", errors));
    }
}
