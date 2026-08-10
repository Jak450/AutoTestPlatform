package org.example.ai_study_notes.agent.api;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.file.AgentAttachment;
import org.example.ai_study_notes.agent.file.AttachmentService;
import org.example.ai_study_notes.agent.session.MessageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话附件 API：上传/列表/删除。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/conversations/{conversationId}/files")
public class FileController {

    private final AttachmentService attachmentService;
    private final MessageService messageService;

    public FileController(AttachmentService attachmentService, MessageService messageService) {
        this.attachmentService = attachmentService;
        this.messageService = messageService;
    }

    @PostMapping
    public Result<Map<String, Object>> upload(@PathVariable("conversationId") Long conversationId,
                                              @RequestParam("file") MultipartFile file) {
        Long userId = UserContext.userId();
        try {
            AgentAttachment attachment = attachmentService.upload(file, userId, conversationId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("fileId", attachment.getId());
            meta.put("fileName", attachment.getFileName());
            meta.put("sizeBytes", attachment.getSizeBytes());
            meta.put("parseStatus", attachment.getParseStatus());
            messageService.append(conversationId, "user", "file",
                    "上传了文件: " + attachment.getFileName(),
                    toJson(meta));
            return Result.success(toAttachmentMap(attachment));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable("conversationId") Long conversationId) {
        List<AgentAttachment> attachments = attachmentService.list(UserContext.userId(), conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentAttachment attachment : attachments) {
            result.add(toAttachmentMap(attachment));
        }
        return Result.success(result);
    }

    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@PathVariable("conversationId") Long conversationId,
                               @PathVariable("fileId") Long fileId) {
        attachmentService.delete(UserContext.userId(), fileId);
        return Result.success();
    }

    private Map<String, Object> toAttachmentMap(AgentAttachment attachment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", attachment.getId());
        map.put("fileName", attachment.getFileName());
        map.put("mimeType", attachment.getMimeType());
        map.put("sizeBytes", attachment.getSizeBytes());
        map.put("parseStatus", attachment.getParseStatus());
        map.put("createdAt", attachment.getCreatedAt());
        return map;
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
