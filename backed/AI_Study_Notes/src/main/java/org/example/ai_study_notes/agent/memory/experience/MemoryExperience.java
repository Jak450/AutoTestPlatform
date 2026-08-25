package org.example.ai_study_notes.agent.memory.experience;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_experience")
public class MemoryExperience {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String taskType;
    private String ruleText;
    private String evidence;
    private Integer hits;
    private LocalDateTime lastUsedAt;
    private BigDecimal confidence;
    private Integer confirmed;
    private String embeddingId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
