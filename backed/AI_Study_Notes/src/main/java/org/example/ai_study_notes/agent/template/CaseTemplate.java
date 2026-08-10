package org.example.ai_study_notes.agent.template;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用例模板表 agent_case_template。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_case_template")
public class CaseTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String description;

    private String caseShape;

    private String coverageRules;

    private String assertRules;

    private String examples;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
