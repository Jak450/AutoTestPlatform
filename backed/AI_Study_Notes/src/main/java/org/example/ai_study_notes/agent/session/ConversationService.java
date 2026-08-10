package org.example.ai_study_notes.agent.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.agent.file.AttachmentMapper;
import org.example.ai_study_notes.agent.file.FileStorageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话 CRUD 与归属校验。会话按 user_id 隔离。
 */
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageService messageService;
    private final AttachmentMapper attachmentMapper;
    private final FileStorageService fileStorageService;

    public ConversationService(ConversationMapper conversationMapper,
                               MessageService messageService,
                               AttachmentMapper attachmentMapper,
                               FileStorageService fileStorageService) {
        this.conversationMapper = conversationMapper;
        this.messageService = messageService;
        this.attachmentMapper = attachmentMapper;
        this.fileStorageService = fileStorageService;
    }

    public AgentConversation create(Long userId, String title) {
        AgentConversation conversation = AgentConversation.builder()
                .userId(userId)
                .title(title == null || title.isBlank() ? "新会话" : title)
                .status("active")
                .build();
        conversationMapper.insert(conversation);
        return conversation;
    }

    public List<AgentConversation> list(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AgentConversation>()
                .eq(AgentConversation::getUserId, userId)
                .orderByDesc(AgentConversation::getUpdatedAt));
    }

    /**
     * 获取用户自己的会话；不存在或不属于该用户时返回 null。
     */
    public AgentConversation getOwned(Long userId, Long conversationId) {
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            return null;
        }
        return conversation;
    }

    public AgentConversation get(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    public void delete(Long userId, Long conversationId) {
        if (getOwned(userId, conversationId) == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        messageService.deleteByConversation(conversationId);
        deleteAttachments(conversationId);
        conversationMapper.deleteById(conversationId);
    }

    public void touch(Long conversationId) {
        AgentConversation update = new AgentConversation();
        update.setId(conversationId);
        update.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(update);
    }

    public void update(AgentConversation conversation) {
        conversationMapper.updateById(conversation);
    }

    public void deleteExpired(LocalDateTime cutoff) {
        List<AgentConversation> expired = conversationMapper.selectList(new LambdaQueryWrapper<AgentConversation>()
                .lt(AgentConversation::getUpdatedAt, cutoff));
        for (AgentConversation conversation : expired) {
            messageService.deleteByConversation(conversation.getId());
            deleteAttachments(conversation.getId());
            conversationMapper.deleteById(conversation.getId());
        }
    }

    private void deleteAttachments(Long conversationId) {
        try {
            List<org.example.ai_study_notes.agent.file.AgentAttachment> attachments =
                    attachmentMapper.selectList(new LambdaQueryWrapper<org.example.ai_study_notes.agent.file.AgentAttachment>()
                            .eq(org.example.ai_study_notes.agent.file.AgentAttachment::getConversationId, conversationId));
            for (org.example.ai_study_notes.agent.file.AgentAttachment attachment : attachments) {
                fileStorageService.delete(attachment);
            }
            attachmentMapper.delete(new LambdaQueryWrapper<org.example.ai_study_notes.agent.file.AgentAttachment>()
                    .eq(org.example.ai_study_notes.agent.file.AgentAttachment::getConversationId, conversationId));
        } catch (Exception e) {
            // 附件清理失败不影响会话删除
        }
    }
}
