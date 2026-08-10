package org.example.ai_study_notes.agent.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话消息追加式存储，seq 单调递增保证顺序稳定。
 */
@Service
public class MessageService {

    private final MessageMapper messageMapper;

    public MessageService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public AgentMessage append(Long conversationId, String role, String type, String content, String toolMeta) {
        AgentMessage message = AgentMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .type(type)
                .content(content)
                .toolMeta(toolMeta)
                .seq(nextSeq(conversationId))
                .build();
        messageMapper.insert(message);
        return message;
    }

    public List<AgentMessage> list(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getConversationId, conversationId)
                .orderByAsc(AgentMessage::getSeq));
    }

    public void deleteByConversation(Long conversationId) {
        messageMapper.delete(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getConversationId, conversationId));
    }

    private Integer nextSeq(Long conversationId) {
        AgentMessage last = messageMapper.selectOne(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getConversationId, conversationId)
                .orderByDesc(AgentMessage::getSeq)
                .last("limit 1"));
        return last == null ? 1 : last.getSeq() + 1;
    }
}
