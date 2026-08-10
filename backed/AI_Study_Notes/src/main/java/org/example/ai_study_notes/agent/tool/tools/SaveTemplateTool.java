package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.template.CaseTemplate;
import org.example.ai_study_notes.agent.template.CaseTemplateService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 保存用例模板（需用户确认）。
 */
@Component
public class SaveTemplateTool implements ToolExecutor {

    private final CaseTemplateService templateService;

    public SaveTemplateTool(CaseTemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("save_template")
                .label("保存模板")
                .description("保存一个用例模板，需要 name；caseShape/coverageRules/assertRules/examples 为 JSON 字符串（可选）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string"),
                                "description", Map.of("type", "string"),
                                "caseShape", Map.of("type", "string"),
                                "coverageRules", Map.of("type", "string"),
                                "assertRules", Map.of("type", "string"),
                                "examples", Map.of("type", "string")),
                        "required", List.of("name")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("模板")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        CaseTemplate template = templateService.create(
                context.getUserId(),
                Args.str(args, "name"),
                Args.str(args, "description"),
                Args.str(args, "caseShape"),
                Args.str(args, "coverageRules"),
                Args.str(args, "assertRules"),
                Args.str(args, "examples"));
        return ToolResult.success("save_template", Map.of("id", template.getId(), "name", template.getName()),
                "模板已保存: " + template.getName());
    }
}
