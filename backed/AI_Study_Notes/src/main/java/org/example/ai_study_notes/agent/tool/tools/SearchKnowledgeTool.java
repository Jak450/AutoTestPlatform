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
 * 检索私有测试知识（只读，自动放行）。
 */
@Component
public class SearchKnowledgeTool implements ToolExecutor {

    private final KnowledgeService knowledgeService;

    public SearchKnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("search_knowledge")
                .label("检索知识")
                .description("按关键词检索私有测试知识库（标题/标签/内容），返回相关文档的标题、分类与摘要片段。参数：query 关键词、limit 返回条数（可选，默认 5）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "检索关键词"),
                                "limit", Map.of("type", "integer", "description", "返回条数，默认 5")),
                        "required", List.of("query")))
                .permission(ToolPermission.READ)
                .category("知识")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        int limit = Args.integer(args, "limit", 5);
        List<KnowledgeDoc> docs = knowledgeService.search(
                context.getUserId(), Args.str(args, "query"), limit);
        List<Map<String, Object>> data = docs.stream().map(d -> (Map<String, Object>) Map.of(
                "slug", d.slug(),
                "category", d.category(),
                "title", d.title(),
                "tags", d.tags(),
                "snippet", d.snippet(),
                "confirmed", d.confirmed())).toList();
        return ToolResult.success("search_knowledge", data, "检索到 " + data.size() + " 条相关知识");
    }
}
