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
 * 保存/更新私有测试知识（MD 文件，按用户隔离；需用户确认；覆盖需 overwrite=true）。
 */
@Component
public class SaveKnowledgeTool implements ToolExecutor {

    private final KnowledgeService knowledgeService;

    public SaveKnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("save_knowledge")
                .label("保存知识")
                .description("把测试经验/知识结论保存为私有知识文档（按用户隔离）。参数：title 标题、content 内容、category 分类目录（可选）、tags 标签数组（可选）、overwrite 是否覆盖同名文档（可选）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "知识标题（同时也是文件名）"),
                                "content", Map.of("type", "string", "description", "知识正文（Markdown）"),
                                "category", Map.of("type", "string", "description", "分类目录，如 经验教训/测试理论/项目规范"),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                                "overwrite", Map.of("type", "boolean", "description", "是否覆盖同名知识文档")),
                        "required", List.of("title", "content")))
                .permission(ToolPermission.AUTO_WRITE)
                .category("知识")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<String> tags = Args.strList(args, "tags");
        KnowledgeDoc doc = knowledgeService.save(
                context.getUserId(),
                Args.str(args, "title"),
                Args.str(args, "content"),
                Args.str(args, "category"),
                tags,
                Args.bool(args, "overwrite", false));
        return ToolResult.success("save_knowledge",
                Map.of("slug", doc.slug(), "category", doc.category(), "title", doc.title()),
                "知识已保存: [" + doc.category() + "] " + doc.title());
    }
}
