package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
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
 * 删除用例模板（需用户确认）。
 */
@Component
public class DeleteTemplateTool implements ToolExecutor {

    private final CaseTemplateService templateService;

    public DeleteTemplateTool(CaseTemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("delete_template")
                .label("删除模板")
                .description("删除指定用例模板，需要 templateId")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("templateId", Map.of("type", "integer")),
                        "required", List.of("templateId")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("模板")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        Long templateId = args.get("templateId") instanceof Number n ? n.longValue() : null;
        templateService.delete(context.getUserId(), templateId);
        return ToolResult.success("delete_template", Map.of("templateId", templateId), "模板已删除");
    }
}
