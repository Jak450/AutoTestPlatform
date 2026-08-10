package org.example.ai_study_notes.agent.skill;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

/**
 * 会话技能状态：加载/卸载，依赖工具激活。
 */
@Service
public class SkillService {

    private final AgentSkillRegistry registry;
    private final RedisTemplate<String, Object> redisTemplate;

    public SkillService(AgentSkillRegistry registry, RedisTemplate<String, Object> redisTemplate) {
        this.registry = registry;
        this.redisTemplate = redisTemplate;
    }

    public List<AgentSkill> listSkills() {
        registry.refresh();
        return registry.list();
    }

    public AgentSkill load(Long conversationId, String name) {
        registry.refresh();
        AgentSkill skill = registry.get(name);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + name);
        }
        if (!registry.isEnabled(name)) {
            throw new IllegalArgumentException("技能已禁用: " + name);
        }
        String key = skillsKey(conversationId);
        List<Object> active = redisTemplate.opsForList().range(key, 0, -1);
        if (active == null || active.stream().noneMatch(v -> String.valueOf(v).equals(name))) {
            redisTemplate.opsForList().rightPush(key, name);
        }
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        return skill;
    }

    public void unload(Long conversationId, String name) {
        redisTemplate.opsForList().remove(skillsKey(conversationId), 0, name);
    }

    public List<AgentSkill> activeSkills(Long conversationId) {
        registry.refresh();
        List<Object> names = redisTemplate.opsForList().range(skillsKey(conversationId), 0, -1);
        if (names == null) {
            return List.of();
        }
        return names.stream()
                .map(n -> registry.get(String.valueOf(n)))
                .filter(s -> s != null && registry.isEnabled(s.getName()))
                .toList();
    }

    public Set<String> activatedTools(Long conversationId) {
        Set<String> tools = new LinkedHashSet<>();
        for (AgentSkill skill : activeSkills(conversationId)) {
            tools.addAll(skill.getTools());
        }
        return tools;
    }

    private String skillsKey(Long conversationId) {
        return "agent:conv:" + conversationId + ":skills";
    }
}
