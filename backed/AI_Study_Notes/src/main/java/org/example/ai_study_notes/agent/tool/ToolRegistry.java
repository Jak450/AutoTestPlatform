package org.example.ai_study_notes.agent.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 工具注册表：工具名全局唯一，重名注册 fail-fast。
 */
@Component
public class ToolRegistry {

    private final ConcurrentMap<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> enabledOverrides = new ConcurrentHashMap<>();

    public void register(ToolDefinition definition) {
        ToolDefinition existing = definitions.putIfAbsent(definition.getName(), definition);
        if (existing != null) {
            throw new IllegalStateException("工具重名注册失败: " + definition.getName());
        }
    }

    public ToolDefinition get(String name) {
        return definitions.get(name);
    }

    public boolean contains(String name) {
        return definitions.containsKey(name);
    }

    public boolean isEnabled(String name) {
        return enabledOverrides.getOrDefault(name, true);
    }

    public void setEnabled(String name, boolean enabled) {
        if (!definitions.containsKey(name)) {
            throw new IllegalArgumentException("工具不存在: " + name);
        }
        enabledOverrides.put(name, enabled);
    }

    public List<ToolDefinition> activeTools() {
        return definitions.values().stream()
                .filter(d -> isEnabled(d.getName()) && d.isActiveByDefault())
                .toList();
    }

    public List<ToolSpecification> toLlmToolSpecifications() {
        return toLlmToolSpecifications(null, java.util.Set.of());
    }

    public List<ToolDefinition> activeDefinitions(Long conversationId, java.util.Set<String> extraToolNames) {
        List<ToolDefinition> result = new ArrayList<>();
        for (ToolDefinition definition : definitions.values()) {
            boolean active = (definition.isActiveByDefault() && isEnabled(definition.getName()))
                    || (extraToolNames != null && extraToolNames.contains(definition.getName()));
            if (active) {
                result.add(definition);
            }
        }
        return result;
    }

    /**
     * 按会话组装激活工具列表：默认激活工具 + 会话/技能激活工具。
     */
    public List<ToolSpecification> toLlmToolSpecifications(Long conversationId, java.util.Set<String> extraToolNames) {
        List<ToolSpecification> specs = new ArrayList<>();
        for (ToolDefinition definition : definitions.values()) {
            boolean active = definition.isActiveByDefault()
                    && isEnabled(definition.getName())
                    || (extraToolNames != null && extraToolNames.contains(definition.getName()));
            if (active) {
                specs.add(ToolSpecification.builder()
                        .name(definition.getName())
                        .description(definition.getDescription())
                        .parameters(JsonSchemaToLlm.convert(definition.getInputSchema()))
                        .build());
            }
        }
        return specs;
    }

    public List<Map<String, Object>> describeAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition definition : definitions.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", definition.getName());
            map.put("label", definition.getLabel());
            map.put("description", definition.getDescription());
            map.put("permission", definition.getPermission().value());
            map.put("category", definition.getCategory());
            map.put("activeByDefault", definition.isActiveByDefault());
            map.put("version", definition.getVersion());
            result.add(map);
        }
        return result;
    }
}
