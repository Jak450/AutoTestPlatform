package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 删除私有测试知识（需用户确认）。
 */
@Component
public class DeleteKnowledgeTool implements ToolExecutor {

    private final KnowledgeService knowledgeService;

    public DeleteKnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("delete_knowledge")
                .label("删除知识")
                .description("删除一条私有测试知识文档。参数：title 标题（必填）、category 分类（可选，默认 默认）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "知识标题"),
                                "category", Map.of("type", "string", "description", "分类目录，默认 默认")),
                        "required", List.of("title")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("知识")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        knowledgeService.delete(
                context.getUserId(), Args.str(args, "category"), Args.str(args, "title"));
        return ToolResult.success("delete_knowledge",
                Map.of("deleted", Args.str(args, "title")),
                "知识已删除: " + Args.str(args, "title"));
    }
}
