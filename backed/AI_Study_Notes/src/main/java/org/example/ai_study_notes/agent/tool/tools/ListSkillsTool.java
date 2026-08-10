package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.skill.AgentSkill;
import org.example.ai_study_notes.agent.skill.SkillService;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列出可用技能。
 */
@Component
public class ListSkillsTool implements ToolExecutor {

    private final SkillService skillService;

    public ListSkillsTool(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_skills")
                .label("技能列表")
                .description("列出平台可用的技能目录（名称与描述），相关时调用 load_skill 加载")
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .permission(ToolPermission.READ)
                .category("系统")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<AgentSkill> skills = skillService.listSkills();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentSkill skill : skills) {
            result.add(Map.of(
                    "name", skill.getName(),
                    "description", skill.getDescription(),
                    "version", skill.getVersion(),
                    "enabled", skill.isEnabled(),
                    "tools", skill.getTools()));
        }
        return ToolResult.success("list_skills", result, "共 " + result.size() + " 个技能");
    }
}
