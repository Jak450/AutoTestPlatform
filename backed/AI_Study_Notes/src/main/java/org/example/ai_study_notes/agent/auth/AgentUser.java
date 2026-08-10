package org.example.ai_study_notes.agent.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 用户表 agent_user。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_user")
public class AgentUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String displayName;

    private String role;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
