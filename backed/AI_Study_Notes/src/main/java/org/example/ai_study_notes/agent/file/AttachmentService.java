package org.example.ai_study_notes.agent.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * 附件服务：上传/列表/删除/解析，绑定会话并按用户隔离。
 */
@Slf4j
@Service
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final FileStorageService fileStorageService;
    private final ConversationService conversationService;

    public AttachmentService(AttachmentMapper attachmentMapper,
                             FileStorageService fileStorageService,
                             ConversationService conversationService) {
        this.attachmentMapper = attachmentMapper;
        this.fileStorageService = fileStorageService;
        this.conversationService = conversationService;
    }

    public AgentAttachment upload(MultipartFile file, Long userId, Long conversationId) {
        if (conversationService.getOwned(userId, conversationId) == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        try {
            Path stored = fileStorageService.store(file, userId, conversationId);
            AgentAttachment attachment = AgentAttachment.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .fileName(file.getOriginalFilename())
                    .mimeType(file.getContentType() == null ? "" : file.getContentType())
                    .sizeBytes(file.getSize())
                    .storagePath(stored.toString())
                    .parseStatus("pending")
                    .build();
            attachmentMapper.insert(attachment);
            parseAsync(attachment);
            return attachment;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("文件上传失败: " + e.getMessage(), e);
        }
    }

    public List<AgentAttachment> list(Long userId, Long conversationId) {
        if (conversationService.getOwned(userId, conversationId) == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<AgentAttachment>()
                .eq(AgentAttachment::getConversationId, conversationId)
                .orderByDesc(AgentAttachment::getCreatedAt));
    }

    public AgentAttachment getOwned(Long userId, Long attachmentId) {
        AgentAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getUserId().equals(userId)) {
            return null;
        }
        return attachment;
    }

    public void delete(Long userId, Long attachmentId) {
        AgentAttachment attachment = getOwned(userId, attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在或无权访问");
        }
        fileStorageService.delete(attachment);
        attachmentMapper.deleteById(attachmentId);
    }

    public String parse(Long userId, Long attachmentId) {
        AgentAttachment attachment = getOwned(userId, attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在或无权访问");
        }
        return parseNow(attachment);
    }

    public String parseNow(AgentAttachment attachment) {
        updateStatus(attachment, "parsing");
        try {
            Path textPath = fileStorageService.extractTextFile(attachment);
            AgentAttachment update = new AgentAttachment();
            update.setId(attachment.getId());
            update.setParseStatus("done");
            update.setParseResultRef(textPath.toString());
            attachmentMapper.updateById(update);
            attachment.setParseStatus("done");
            attachment.setParseResultRef(textPath.toString());
            return fileStorageService.readText(attachment);
        } catch (Exception e) {
            updateStatus(attachment, "failed");
            throw e instanceof IllegalStateException ise ? ise : new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
    }

    private void parseAsync(AgentAttachment attachment) {
        Thread thread = new Thread(() -> {
            try {
                parseNow(attachment);
            } catch (Exception e) {
                log.warn("附件异步解析失败 fileId={}", attachment.getId(), e.getMessage());
            }
        }, "agent-parse-" + attachment.getId());
        thread.setDaemon(true);
        thread.start();
    }

    private void updateStatus(AgentAttachment attachment, String status) {
        AgentAttachment update = new AgentAttachment();
        update.setId(attachment.getId());
        update.setParseStatus(status);
        attachmentMapper.updateById(update);
        attachment.setParseStatus(status);
    }
}
