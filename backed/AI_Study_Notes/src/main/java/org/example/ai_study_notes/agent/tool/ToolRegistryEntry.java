package org.example.ai_study_notes.agent.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具注册表持久化表 agent_tool_registry。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_tool_registry")
public class ToolRegistryEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolName;

    private String label;

    private String description;

    private String inputSchema;

    private String category;

    private String permission;

    private Integer activeByDefault;

    private Integer enabled;

    private String version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
