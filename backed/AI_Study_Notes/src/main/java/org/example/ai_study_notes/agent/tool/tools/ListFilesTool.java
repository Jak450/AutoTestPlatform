package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.file.AgentAttachment;
import org.example.ai_study_notes.agent.file.AttachmentService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列出当前会话上传的附件。
 */
@Component
public class ListFilesTool implements ToolExecutor {

    private final AttachmentService attachmentService;

    public ListFilesTool(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_files")
                .label("文件列表")
                .description("列出当前会话上传的附件文件")
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .permission(ToolPermission.READ)
                .category("文件")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<AgentAttachment> attachments = attachmentService.list(context.getUserId(), context.getConversationId());
        List<Map<String, Object>> files = new ArrayList<>();
        for (AgentAttachment attachment : attachments) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", attachment.getId());
            map.put("fileName", attachment.getFileName());
            map.put("sizeBytes", attachment.getSizeBytes());
            map.put("parseStatus", attachment.getParseStatus());
            files.add(map);
        }
        return ToolResult.success("list_files", files, "共 " + files.size() + " 个文件");
    }
}
