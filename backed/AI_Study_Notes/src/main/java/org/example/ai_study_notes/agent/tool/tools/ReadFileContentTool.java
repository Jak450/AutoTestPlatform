package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.file.AgentAttachment;
import org.example.ai_study_notes.agent.file.AttachmentService;
import org.example.ai_study_notes.agent.file.FileStorageService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 读取会话附件文本内容（供模型问答）。
 */
@Component
public class ReadFileContentTool implements ToolExecutor {

    private static final int MAX_READ_BYTES = 100_000;

    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;

    public ReadFileContentTool(AttachmentService attachmentService, FileStorageService fileStorageService) {
        this.attachmentService = attachmentService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("read_file_content")
                .label("读取文件内容")
                .description("读取会话中某个附件的文本内容（fileId 通过 list_files 获取），可用于针对文档问答")
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
            return ToolResult.error("read_file_content", "文件不存在或无权访问",
                    ToolResultMeta.ErrorType.NOT_FOUND,
                    ToolResultMeta.RecommendedNextAction.REWRITE_QUERY,
                    System.currentTimeMillis());
        }
        if (!"done".equals(attachment.getParseStatus())) {
            attachmentService.parseNow(attachment);
        }
        String text = fileStorageService.readText(attachment);
        boolean truncated = text.length() > MAX_READ_BYTES;
        String content = truncated ? text.substring(0, MAX_READ_BYTES) : text;
        Map<String, Object> data = Map.of(
                "fileId", fileId,
                "fileName", attachment.getFileName(),
                "content", content,
                "truncated", truncated);
        return ToolResult.success("read_file_content", data, "文件内容已读取，长度 " + text.length());
    }
}
