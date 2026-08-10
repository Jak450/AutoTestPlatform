package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.file.AgentAttachment;
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
 * 解析需求文档，返回提取的文本内容。
 */
@Component("agentParseDocumentTool")
public class ParseDocumentTool implements ToolExecutor {

    private final AttachmentService attachmentService;

    public ParseDocumentTool(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("parse_document")
                .label("解析文档")
                .description("解析会话中上传的需求文档（fileId 通过 list_files 获取），提取全文内容用于后续分析")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("fileId", Map.of("type", "integer", "description", "附件ID")),
                        "required", List.of("fileId")))
                .permission(ToolPermission.READ)
                .category("文件")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        Long fileId = Long.valueOf(Args.integer(args, "fileId", null));
        AgentAttachment attachment = attachmentService.getOwned(context.getUserId(), fileId);
        if (attachment == null) {
            return ToolResult.error("parse_document", "文件不存在或无权访问",
                    ToolResultMeta.ErrorType.NOT_FOUND,
                    ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                    System.currentTimeMillis());
        }
        String text = attachmentService.parse(context.getUserId(), fileId);
        Map<String, Object> data = Map.of(
                "fileId", fileId,
                "fileName", attachment.getFileName(),
                "parseStatus", "done",
                "content", text.length() > 200_000 ? text.substring(0, 200_000) : text,
                "truncated", text.length() > 200_000);
        return ToolResult.success("parse_document", data, "文档解析完成，共 " + text.length() + " 字符");
    }
}
