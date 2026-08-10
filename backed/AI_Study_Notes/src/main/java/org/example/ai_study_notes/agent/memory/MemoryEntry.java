package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 长期记忆表 agent_memory。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_memory")
public class MemoryEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String scope;

    private String namespace;

    private String memKey;

    private String contentMd;

    private String tags;

    private String confidence;

    private Integer confirmed;

    private Long sourceSessionId;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
