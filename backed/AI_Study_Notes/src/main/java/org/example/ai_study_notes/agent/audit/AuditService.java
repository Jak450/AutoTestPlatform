package org.example.ai_study_notes.agent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审计服务：记录工具调用链、写/执行确认等关键动作（不含密钥与敏感响应体）。
 */
@Slf4j
@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void log(Long userId, Long conversationId, String runId, String action, Map<String, Object> detail) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .runId(runId)
                    .action(action)
                    .detail(detail == null ? null : objectMapper.writeValueAsString(detail))
                    .build();
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败 action={}: {}", action, e.getMessage());
        }
    }
}
