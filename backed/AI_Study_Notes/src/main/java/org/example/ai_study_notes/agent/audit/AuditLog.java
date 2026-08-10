package org.example.ai_study_notes.agent.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志表 agent_audit_log。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long conversationId;

    private String runId;

    private String action;

    private String detail;

    private LocalDateTime createdAt;
}
