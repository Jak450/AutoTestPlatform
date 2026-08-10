package org.example.ai_study_notes.agent.confirmation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具执行确认记录 agent_confirmation。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_confirmation")
public class AgentConfirmation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long messageId;

    private String toolName;

    private String payload;

    private String payloadHash;

    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime respondedAt;
}
