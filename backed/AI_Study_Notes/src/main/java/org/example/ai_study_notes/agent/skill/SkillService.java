package org.example.ai_study_notes.agent.skill;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话技能状态：加载/卸载，依赖工具激活。
 */
@Service
public class SkillService {

    private final AgentSkillRegistry registry;
    private final Map<Long, List<AgentSkill>> activeByConversation = new ConcurrentHashMap<>();

    public SkillService(AgentSkillRegistry registry) {
        this.registry = registry;
    }

    public List<AgentSkill> listSkills() {
        return registry.list();
    }

    public AgentSkill load(Long conversationId, String name) {
        AgentSkill skill = registry.get(name);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + name);
        }
        if (!skill.isEnabled()) {
            throw new IllegalArgumentException("技能已禁用: " + name);
        }
        List<AgentSkill> active = activeByConversation.computeIfAbsent(conversationId, k -> new ArrayList<>());
        if (active.stream().noneMatch(s -> s.getName().equals(name))) {
            active.add(skill);
        }
        return skill;
    }

    public void unload(Long conversationId, String name) {
        List<AgentSkill> active = activeByConversation.get(conversationId);
        if (active != null) {
            active.removeIf(s -> s.getName().equals(name));
        }
    }

    public List<AgentSkill> activeSkills(Long conversationId) {
        return activeByConversation.getOrDefault(conversationId, List.of());
    }

    public Set<String> activatedTools(Long conversationId) {
        Set<String> tools = new LinkedHashSet<>();
        for (AgentSkill skill : activeSkills(conversationId)) {
            tools.addAll(skill.getTools());
        }
        return tools;
    }
}
