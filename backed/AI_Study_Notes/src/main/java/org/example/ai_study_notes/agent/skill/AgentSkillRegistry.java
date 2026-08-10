package org.example.ai_study_notes.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 技能注册表：启动时扫描 skills 目录下各技能的 SKILL.md，解析 frontmatter 与正文。
 */
@Slf4j
@Component
public class AgentSkillRegistry {

    private static final String ENABLED_HASH = "agent:admin:skills";

    private final Map<String, AgentSkill> skills = new LinkedHashMap<>();
    private final AgentProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    public AgentSkillRegistry(AgentProperties properties, RedisTemplate<String, Object> redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        refresh();
    }

    /**
     * 热重载：重新扫描 skills 目录（内容签名简化版：直接重扫）。
     */
    public synchronized void refresh() {
        skills.clear();
        Path root = resolveSkillsRoot(properties.getSkillsDir());
        if (root == null) {
            log.warn("未找到 skills 目录，技能系统不可用");
            return;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path skillFile = dir.resolve("SKILL.md");
                if (Files.exists(skillFile)) {
                    try {
                        AgentSkill skill = parseSkill(skillFile);
                        skills.put(skill.getName(), skill);
                    } catch (Exception e) {
                        log.warn("技能解析失败 {}: {}", dir, e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            log.warn("扫描技能目录失败: {}", e.getMessage());
        }
        log.info("Agent 技能注册完成，共 {} 个: {}", skills.size(), skills.keySet());
    }

    public AgentSkill get(String name) {
        return skills.get(name);
    }

    public boolean isEnabled(String name) {
        try {
            Object override = redisTemplate.opsForHash().get(ENABLED_HASH, name);
            if (override != null) {
                return Boolean.parseBoolean(String.valueOf(override));
            }
        } catch (Exception ignored) {
            // ignore
        }
        AgentSkill skill = skills.get(name);
        return skill == null || skill.isEnabled();
    }

    /**
     * 技能根目录（供管理端新建技能写入 SKILL.md 使用）。
     */
    public Path skillsRoot() {
        return resolveSkillsRoot(properties.getSkillsDir());
    }

    public void setEnabled(String name, boolean enabled) {
        if (!skills.containsKey(name)) {
            throw new IllegalArgumentException("技能不存在: " + name);
        }
        redisTemplate.opsForHash().put(ENABLED_HASH, name, String.valueOf(enabled));
    }

    public List<AgentSkill> list() {
        return new ArrayList<>(skills.values());
    }

    private Path resolveSkillsRoot(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isDirectory(path) ? path : null;
        }
        Path userDir = Path.of(System.getProperty("user.dir", "."));
        List<Path> candidates = List.of(
                userDir.resolve("skills"),
                userDir.resolve("../skills"),
                userDir.resolve("../../skills"),
                userDir.resolve("../../../skills"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private AgentSkill parseSkill(Path skillFile) throws IOException {
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        Map<String, String> frontmatter = new LinkedHashMap<>();
        String body = content;
        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end > 0) {
                String fm = content.substring(3, end);
                body = content.substring(end + 4);
                for (String line : fm.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        frontmatter.put(line.substring(0, colon).trim(),
                                line.substring(colon + 1).trim().replaceAll("^[\"']|[\"']$", ""));
                    }
                }
            }
        }
        String name = frontmatter.getOrDefault("name", skillFile.getParent().getFileName().toString());
        boolean enabled = !"false".equalsIgnoreCase(frontmatter.getOrDefault("enabled", "true"));
        List<String> tools = parseTools(frontmatter.get("tools"));
        return AgentSkill.builder()
                .name(name)
                .description(frontmatter.getOrDefault("description", ""))
                .version(frontmatter.getOrDefault("version", "1.0.0"))
                .enabled(enabled)
                .tools(tools)
                .body(body.trim())
                .build();
    }

    private List<String> parseTools(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String cleaned = raw.replaceAll("[\\[\\]\"']", "");
        for (String part : cleaned.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
