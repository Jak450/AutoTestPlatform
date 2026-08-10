package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.generator.TestCaseGeneratorService;
import org.example.ai_study_notes.agent.file.AttachmentService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 根据需求文档与模板生成用例草稿（草稿需用户确认后才保存）。
 */
@Component
public class GenerateCasesTool implements ToolExecutor {

    private final TestCaseGeneratorService generatorService;
    private final AttachmentService attachmentService;

    public GenerateCasesTool(TestCaseGeneratorService generatorService, AttachmentService attachmentService) {
        this.generatorService = generatorService;
        this.attachmentService = attachmentService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("generate_cases")
                .label("生成用例")
                .description("根据需求文档与用例模板生成 API 测试用例草稿，返回用例 JSON 数组；docText 传文档文本，或直接传 fileId（会话中已上传的文档 ID，工具会自己读取内容），二者必填其一")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "docText", Map.of("type", "string", "description", "需求文档文本"),
                                "fileId", Map.of("type", "integer", "description", "会话中已上传的文档 ID（推荐）"),
                                "templateId", Map.of("type", "integer", "description", "用例模板ID（可选）"),
                                "projectId", Map.of("type", "integer", "description", "目标项目ID（可选）")),
                        "required", List.of()))
                .permission(ToolPermission.READ)
                .category("生成")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        String docText = Args.str(args, "docText");
        Integer fileId = Args.integer(args, "fileId", null);
        Long templateId = args.get("templateId") instanceof Number n ? n.longValue() : null;
        Integer projectId = Args.integer(args, "projectId", null);
        if ((docText == null || docText.isBlank()) && fileId == null) {
            return ToolResult.error("generate_cases", "请提供 docText 或 fileId（已上传文档的 ID）",
                    org.example.ai_study_notes.agent.contract.ToolResultMeta.ErrorType.CONFIG,
                    org.example.ai_study_notes.agent.contract.ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                    System.currentTimeMillis());
        }
        if (docText == null || docText.isBlank()) {
            docText = attachmentService.parse(context.getUserId(), Long.valueOf(fileId));
        }
        List<Map<String, Object>> cases = generatorService.generateCases(
                context.getUserId(), context.getConversationId(), docText, templateId, projectId);
        return ToolResult.success("generate_cases", cases, "生成 " + cases.size() + " 条用例草稿，请向用户展示并确认后保存");
    }
}
