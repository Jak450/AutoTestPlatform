package org.example.ai_study_notes.agent.confirmation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户确认服务：写/执行类工具必须先创建确认记录，批准后才执行。
 */
@Slf4j
@Service
public class ConfirmationService {

    private static final long CONFIRMATION_TTL_MINUTES = 10;
    private static final String PENDING_PREFIX = "agent:confirm:";
    private static final String PENDING_SUFFIX = ":pending";

    private final ConfirmationMapper confirmationMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ConfirmationService(ConfirmationMapper confirmationMapper,
                               ObjectMapper objectMapper,
                               RedisTemplate<String, Object> redisTemplate) {
        this.confirmationMapper = confirmationMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
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
        redisTemplate.opsForValue().set(pendingKey(confirmation.getId()),
                pendingToMap(new PendingToolCall(toolName, payload, payloadJson, toolCallId)),
                CONFIRMATION_TTL_MINUTES, TimeUnit.MINUTES);
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
        return takePending(confirmationId);
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
        return takePending(confirmationId);
    }

    private PendingToolCall takePending(Long confirmationId) {
        String key = pendingKey(confirmationId);
        Object value = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return mapToPending(value);
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

    private Map<String, Object> pendingToMap(PendingToolCall pending) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("toolName", pending.getToolName());
        map.put("args", pending.getArgs());
        map.put("argsJson", pending.getArgsJson());
        map.put("toolCallId", pending.getToolCallId());
        return map;
    }

    @SuppressWarnings("unchecked")
    private PendingToolCall mapToPending(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new PendingToolCall(
                String.valueOf(map.get("toolName")),
                (Map<String, Object>) map.get("args"),
                String.valueOf(map.get("argsJson")),
                String.valueOf(map.get("toolCallId")));
    }

    private String pendingKey(Long confirmationId) {
        return PENDING_PREFIX + confirmationId + PENDING_SUFFIX;
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
