package org.example.ai_study_notes.agent.confirmation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 用户确认服务：写/执行类工具必须先创建确认记录，批准后才执行。
 */
@Slf4j
@Service
public class ConfirmationService {

    private static final long CONFIRMATION_TTL_MINUTES = 10;

    private final ConfirmationMapper confirmationMapper;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<Long, PendingToolCall> pendingCalls = new ConcurrentHashMap<>();

    public ConfirmationService(ConfirmationMapper confirmationMapper, ObjectMapper objectMapper) {
        this.confirmationMapper = confirmationMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建待确认记录。
     *
     * @return 确认记录（含 id）
     */
    public AgentConfirmation create(Long conversationId, String toolName, Map<String, Object> payload, String toolCallId) {
        String payloadJson = toJson(payload);
        AgentConfirmation confirmation = AgentConfirmation.builder()
                .conversationId(conversationId)
                .toolName(toolName)
                .payload(payloadJson)
                .payloadHash(sha256(payloadJson))
                .status("pending")
                .expiresAt(LocalDateTime.now().plusMinutes(CONFIRMATION_TTL_MINUTES))
                .build();
        confirmationMapper.insert(confirmation);
        pendingCalls.put(confirmation.getId(), new PendingToolCall(toolName, payload, payloadJson, toolCallId));
        return confirmation;
    }

    /**
     * 批准确认：校验归属与状态，返回待执行的工具调用。
     */
    public PendingToolCall approve(Long conversationId, Long confirmationId) {
        AgentConfirmation confirmation = getOwned(conversationId, confirmationId);
        if (confirmation == null) {
            throw new IllegalArgumentException("确认记录不存在或无权访问");
        }
        if (!"pending".equals(confirmation.getStatus())) {
            throw new IllegalArgumentException("该确认已处理（" + confirmation.getStatus() + "）");
        }
        if (confirmation.getExpiresAt() != null && confirmation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("确认已过期，请重新发起操作");
        }
        AgentConfirmation update = new AgentConfirmation();
        update.setId(confirmation.getId());
        update.setStatus("approved");
        update.setRespondedAt(LocalDateTime.now());
        confirmationMapper.updateById(update);
        return pendingCalls.remove(confirmation.getId());
    }

    public PendingToolCall reject(Long conversationId, Long confirmationId) {
        AgentConfirmation confirmation = getOwned(conversationId, confirmationId);
        if (confirmation == null) {
            throw new IllegalArgumentException("确认记录不存在或无权访问");
        }
        if (!"pending".equals(confirmation.getStatus())) {
            throw new IllegalArgumentException("该确认已处理（" + confirmation.getStatus() + "）");
        }
        AgentConfirmation update = new AgentConfirmation();
        update.setId(confirmation.getId());
        update.setStatus("rejected");
        update.setRespondedAt(LocalDateTime.now());
        confirmationMapper.updateById(update);
        return pendingCalls.remove(confirmation.getId());
    }

    public AgentConfirmation getOwned(Long conversationId, Long confirmationId) {
        AgentConfirmation confirmation = confirmationMapper.selectOne(new LambdaQueryWrapper<AgentConfirmation>()
                .eq(AgentConfirmation::getId, confirmationId)
                .eq(AgentConfirmation::getConversationId, conversationId));
        return confirmation;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("确认载荷序列化失败", e);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("payload hash 计算失败", e);
        }
    }

    @Data
    @AllArgsConstructor
    public static class PendingToolCall {
        private String toolName;
        private Map<String, Object> args;
        private String argsJson;
        private String toolCallId;
    }
}
