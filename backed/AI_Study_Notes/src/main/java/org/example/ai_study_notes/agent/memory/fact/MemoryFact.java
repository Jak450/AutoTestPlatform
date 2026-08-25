package org.example.ai_study_notes.agent.memory.fact;

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
@TableName("memory_fact")
public class MemoryFact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String entityId;
    private String attribute;
    private String factValue;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Integer version;
    private String sourceType;
    private String sourceRef;
    private BigDecimal confidence;
    private String embeddingId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
