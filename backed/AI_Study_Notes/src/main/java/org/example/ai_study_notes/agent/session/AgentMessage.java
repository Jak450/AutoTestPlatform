package org.example.ai_study_notes.agent.session;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 消息表 agent_message。
 * role: user / assistant / tool / system；type 对应契约 messageTypes。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_message")
public class AgentMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;

    private String type;

    private String content;

    private String toolMeta;

    private Integer seq;

    private LocalDateTime createdAt;
}
