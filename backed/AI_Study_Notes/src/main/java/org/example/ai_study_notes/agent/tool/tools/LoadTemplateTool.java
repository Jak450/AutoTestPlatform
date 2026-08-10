package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
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
 * 加载模板为当前会话的生成上下文。
 */
@Component
public class LoadTemplateTool implements ToolExecutor {

    private final CaseTemplateService templateService;

    public LoadTemplateTool(CaseTemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("load_template")
                .label("加载模板")
                .description("把指定模板设为当前会话的激活模板，后续 generate_cases 会参考该模板，需要 templateId")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("templateId", Map.of("type", "integer", "description", "模板ID")),
                        "required", List.of("templateId")))
                .permission(ToolPermission.READ)
                .category("模板")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        Long templateId = args.get("templateId") instanceof Number n ? n.longValue() : null;
        CaseTemplate template = templateService.getOwned(context.getUserId(), templateId);
        if (template == null) {
            return ToolResult.error("load_template", "模板不存在或无权访问",
                    ToolResultMeta.ErrorType.NOT_FOUND,
                    ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                    System.currentTimeMillis());
        }
        templateService.setActive(context.getConversationId(), template);
        return ToolResult.success("load_template", templateService.toMap(template), "模板已激活: " + template.getName());
    }
}
