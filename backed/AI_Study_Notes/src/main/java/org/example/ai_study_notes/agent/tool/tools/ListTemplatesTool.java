package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.template.CaseTemplate;
import org.example.ai_study_notes.agent.template.CaseTemplateService;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列出当前用户的用例模板。
 */
@Component
public class ListTemplatesTool implements ToolExecutor {

    private final CaseTemplateService templateService;

    public ListTemplatesTool(CaseTemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_templates")
                .label("模板列表")
                .description("列出当前用户的用例模板（仅名称与描述）")
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .permission(ToolPermission.READ)
                .category("模板")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<CaseTemplate> templates = templateService.list(context.getUserId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CaseTemplate template : templates) {
            result.add(Map.of(
                    "id", template.getId(),
                    "name", template.getName(),
                    "description", template.getDescription()));
        }
        return ToolResult.success("list_templates", result, "共 " + result.size() + " 个模板");
    }
}
