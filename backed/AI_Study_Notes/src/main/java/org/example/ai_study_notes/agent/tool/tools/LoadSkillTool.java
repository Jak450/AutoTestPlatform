package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.skill.AgentSkill;
import org.example.ai_study_notes.agent.skill.SkillService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 加载技能：注入技能正文并激活其依赖工具。
 */
@Component
public class LoadSkillTool implements ToolExecutor {

    private final SkillService skillService;

    public LoadSkillTool(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("load_skill")
                .label("加载技能")
                .description("加载指定技能到当前会话，注入技能正文并激活其依赖工具，需要 name")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string", "description", "技能名称")),
                        "required", List.of("name")))
                .permission(ToolPermission.READ)
                .category("系统")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        AgentSkill skill = skillService.load(context.getConversationId(), Args.str(args, "name"));
        return ToolResult.success("load_skill",
                Map.of("name", skill.getName(), "tools", skill.getTools()),
                "技能已加载: " + skill.getName());
    }
}
