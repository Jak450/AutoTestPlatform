package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
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
 * 卸载技能，恢复默认工具池。
 */
@Component
public class UnloadSkillTool implements ToolExecutor {

    private final SkillService skillService;

    public UnloadSkillTool(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("unload_skill")
                .label("卸载技能")
                .description("卸载当前会话的指定技能，需要 name")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string")),
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
        String name = Args.str(args, "name");
        skillService.unload(context.getConversationId(), name);
        return ToolResult.success("unload_skill", Map.of("name", name), "技能已卸载: " + name);
    }
}
