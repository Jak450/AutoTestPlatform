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
 * Agent 会话表 agent_conversation。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_conversation")
public class AgentConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String status;

    private String activeToolNames;

    private String contextSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
