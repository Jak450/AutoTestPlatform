package org.example.ai_study_notes.agent.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.AgentUserService;
import org.example.ai_study_notes.agent.skill.AgentSkill;
import org.example.ai_study_notes.agent.skill.AgentSkillRegistry;
import org.example.ai_study_notes.agent.template.CaseTemplate;
import org.example.ai_study_notes.agent.template.CaseTemplateMapper;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolRegistryEntry;
import org.example.ai_study_notes.agent.tool.ToolRegistryEntryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 管理接口（admin）：工具启停管理。
 */
@RestController
@RequestMapping("/api/agent/admin")
public class AdminController {

    private final AgentUserService userService;
    private final ToolRegistry toolRegistry;
    private final ToolRegistryEntryMapper entryMapper;
    private final AgentSkillRegistry skillRegistry;
    private final CaseTemplateMapper caseTemplateMapper;

    public AdminController(AgentUserService userService,
                           ToolRegistry toolRegistry,
                           ToolRegistryEntryMapper entryMapper,
                           AgentSkillRegistry skillRegistry,
                           CaseTemplateMapper caseTemplateMapper) {
        this.userService = userService;
        this.toolRegistry = toolRegistry;
        this.entryMapper = entryMapper;
        this.skillRegistry = skillRegistry;
        this.caseTemplateMapper = caseTemplateMapper;
    }

    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> listTools() {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> definition : toolRegistry.describeAll()) {
            Map<String, Object> map = new LinkedHashMap<>(definition);
            map.put("enabled", toolRegistry.isEnabled(String.valueOf(map.get("name"))));
            result.add(map);
        }
        return Result.success(result);
    }

    @PostMapping("/tools/{name}/{action}")
    public Result<Map<String, Object>> toggleTool(@PathVariable("name") String name,
                                                  @PathVariable("action") String action) {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        boolean enabled = "enable".equalsIgnoreCase(action);
        if (!"enable".equalsIgnoreCase(action) && !"disable".equalsIgnoreCase(action)) {
            return Result.error("action 必须是 enable 或 disable");
        }
        try {
            toolRegistry.setEnabled(name, enabled);
            persist(name, enabled);
            return Result.success(Map.of("name", name, "enabled", enabled));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/skills")
    public Result<Map<String, Object>> createSkill(@RequestBody CreateSkillRequest request) {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        try {
            String name = sanitizeSkillName(request.getName());
            Path root = skillRegistry.skillsRoot();
            if (root == null) {
                return Result.error("skills 目录不存在");
            }
            Path skillFile = root.resolve(name).resolve("SKILL.md");
            if (Files.exists(skillFile)) {
                return Result.error("技能已存在: " + name);
            }
            String description = request.getDescription() == null ? "" : request.getDescription().trim();
            String content = request.getContent() == null ? "" : request.getContent().trim();
            if (content.isEmpty()) {
                return Result.error("技能正文不能为空");
            }
            Files.createDirectories(skillFile.getParent());
            Files.writeString(skillFile, buildSkillMarkdown(name, description, content), StandardCharsets.UTF_8);
            skillRegistry.refresh();
            return Result.success(Map.of("name", name,
                    "path", root.relativize(skillFile).toString().replace('\\', '/')));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (IOException e) {
            return Result.error("技能创建失败: " + e.getMessage());
        }
    }

    private String sanitizeSkillName(String raw) {
        if (raw == null || !Pattern.matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$", raw.trim())) {
            throw new IllegalArgumentException("技能名只能包含字母/数字/下划线/连字符（1-64位）");
        }
        return raw.trim();
    }

    private String buildSkillMarkdown(String name, String description, String content) {
        return "---\n"
                + "name: " + singleLine(name) + "\n"
                + "description: " + singleLine(description) + "\n"
                + "enabled: true\n"
                + "version: 1.0.0\n"
                + "---\n\n"
                + content + "\n";
    }

    private String singleLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    @Data
    public static class CreateSkillRequest {
        private String name;
        private String description;
        private String content;
    }

    private void persist(String name, boolean enabled) {
        ToolRegistryEntry entry = entryMapper.selectOne(new LambdaQueryWrapper<ToolRegistryEntry>()
                .eq(ToolRegistryEntry::getToolName, name));
        if (entry == null) {
            ToolDefinition definition = toolRegistry.get(name);
            entry = ToolRegistryEntry.builder()
                    .toolName(name)
                    .label(definition.getLabel())
                    .description(definition.getDescription())
                    .category(definition.getCategory())
                    .permission(definition.getPermission().value())
                    .activeByDefault(definition.isActiveByDefault() ? 1 : 0)
                    .enabled(enabled ? 1 : 0)
                    .version(definition.getVersion())
                    .build();
            entryMapper.insert(entry);
        } else {
            ToolRegistryEntry update = new ToolRegistryEntry();
            update.setId(entry.getId());
            update.setEnabled(enabled ? 1 : 0);
            entryMapper.updateById(update);
        }
    }

    @GetMapping("/skills")
    public Result<List<Map<String, Object>>> listSkills() {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        skillRegistry.refresh();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentSkill skill : skillRegistry.list()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", skill.getName());
            map.put("description", skill.getDescription());
            map.put("version", skill.getVersion());
            map.put("enabled", skillRegistry.isEnabled(skill.getName()));
            map.put("tools", skill.getTools());
            result.add(map);
        }
        return Result.success(result);
    }

    @PostMapping("/skills/{name}/{action}")
    public Result<Map<String, Object>> toggleSkill(@PathVariable("name") String name,
                                                   @PathVariable("action") String action) {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        boolean enabled = "enable".equalsIgnoreCase(action);
        if (!"enable".equalsIgnoreCase(action) && !"disable".equalsIgnoreCase(action)) {
            return Result.error("action 必须是 enable 或 disable");
        }
        try {
            skillRegistry.setEnabled(name, enabled);
            return Result.success(Map.of("name", name, "enabled", enabled));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> listAllTemplates() {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        List<CaseTemplate> templates = caseTemplateMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CaseTemplate template : templates) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", template.getId());
            map.put("userId", template.getUserId());
            map.put("name", template.getName());
            map.put("description", template.getDescription());
            map.put("updatedAt", template.getUpdatedAt());
            result.add(map);
        }
        return Result.success(result);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable("id") Long id) {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        caseTemplateMapper.deleteById(id);
        return Result.success();
    }
}
