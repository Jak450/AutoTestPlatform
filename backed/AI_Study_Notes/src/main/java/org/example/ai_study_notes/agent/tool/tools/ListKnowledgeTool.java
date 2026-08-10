package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.knowledge.KnowledgeDoc;
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
 * 列出私有测试知识（只读，自动放行）。
 */
@Component
public class ListKnowledgeTool implements ToolExecutor {

    private final KnowledgeService knowledgeService;

    public ListKnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_knowledge")
                .label("知识列表")
                .description("列出私有测试知识库中的文档（标题/分类/标签/摘要）。参数：category 分类（可选，不传列出全部）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "category", Map.of("type", "string", "description", "分类目录，不传列出全部")),
                        "required", List.of()))
                .permission(ToolPermission.READ)
                .category("知识")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<KnowledgeDoc> docs = knowledgeService.list(
                context.getUserId(), Args.str(args, "category"), false);
        List<Map<String, Object>> data = docs.stream().map(d -> (Map<String, Object>) Map.of(
                "slug", d.slug(),
                "category", d.category(),
                "title", d.title(),
                "tags", d.tags(),
                "snippet", d.snippet(),
                "confirmed", d.confirmed())).toList();
        return ToolResult.success("list_knowledge", data, "共 " + data.size() + " 条知识文档");
    }
}
