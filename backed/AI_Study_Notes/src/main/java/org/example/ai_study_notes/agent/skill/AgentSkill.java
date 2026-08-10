package org.example.ai_study_notes.agent.skill;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 技能元数据（SKILL.md frontmatter + body）。
 */
@Data
@Builder
public class AgentSkill {

    private String name;
    private String description;
    private String version;
    private boolean enabled;
    private List<String> tools;
    private String body;
}
