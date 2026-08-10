package org.example.ai_study_notes.agent.execution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具执行记录表 agent_tool_execution：按 (conversation_id, tool_name, payload_hash) 唯一，
 * 用于断连重传幂等（同参数写操作只执行一次，成功结果可重放）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_tool_execution")
public class AgentToolExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String toolCallId;

    private String toolName;

    private String payloadHash;

    /** running / success / failed */
    private String status;

    /** 执行结果 JSON（{status, data, message, errorType, recommendedNextAction}） */
    private String result;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
