package org.example.ai_study_notes.agent.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话附件表 agent_attachment。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_attachment")
public class AgentAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    private String fileName;

    private String mimeType;

    private Long sizeBytes;

    private String storagePath;

    private String parseStatus;

    private String parseResultRef;

    private LocalDateTime createdAt;
}
