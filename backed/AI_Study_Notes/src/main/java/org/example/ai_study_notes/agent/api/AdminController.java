package org.example.ai_study_notes.agent.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.AgentUserService;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolRegistryEntry;
import org.example.ai_study_notes.agent.tool.ToolRegistryEntryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理接口（admin）：工具启停管理。
 */
@RestController
@RequestMapping("/api/agent/admin")
public class AdminController {

    private final AgentUserService userService;
    private final ToolRegistry toolRegistry;
    private final ToolRegistryEntryMapper entryMapper;

    public AdminController(AgentUserService userService,
                           ToolRegistry toolRegistry,
                           ToolRegistryEntryMapper entryMapper) {
        this.userService = userService;
        this.toolRegistry = toolRegistry;
        this.entryMapper = entryMapper;
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
}
