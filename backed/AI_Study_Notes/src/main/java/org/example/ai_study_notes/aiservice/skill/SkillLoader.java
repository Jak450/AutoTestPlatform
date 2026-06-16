package org.example.ai_study_notes.aiservice.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class SkillLoader {

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?", Pattern.DOTALL);

    private final Map<String, CachedSkill> skillCache = new ConcurrentHashMap<>();
    private final Path skillsRoot;

    public SkillLoader() {
        String userDir = System.getProperty("user.dir").replace("\\", "/");
        Path resolved = null;
        String[] candidates = {
                userDir + "/../../../skills",
                userDir + "/../../skills",
                userDir + "/../skills",
                userDir.replace("/backed/AI_Study_Notes", "") + "/skills",
                userDir + "/skills"
        };
        for (String c : candidates) {
            Path path = Path.of(c).normalize();
            if (Files.isDirectory(path) && hasAnySkill(path)) {
                resolved = path;
                break;
            }
        }
        if (resolved == null) {
            resolved = Path.of(candidates[candidates.length - 1]).normalize();
        }
        skillsRoot = resolved;
        log.info("Skills root resolved to: {}", skillsRoot);
    }

    private boolean hasAnySkill(Path root) {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .anyMatch(d -> Files.exists(d.resolve("SKILL.md")));
        } catch (IOException e) {
            return false;
        }
    }

    public static class SkillInfo {
        private String name;
        private String description;
        private String body;
        private String rawContent;
        private boolean enabled;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }

        public String getRawContent() { return rawContent; }
        public void setRawContent(String rawContent) { this.rawContent = rawContent; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getContent() { return body; }
    }

    private static class CachedSkill {
        final SkillInfo info;
        final long mtime;
        CachedSkill(SkillInfo info, long mtime) {
            this.info = info;
            this.mtime = mtime;
        }
    }

    public SkillInfo load(String skillName) {
        Path skillDir = skillsRoot.resolve(skillName);
        Path skillFile = skillDir.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            log.warn("Skill not found: {}", skillName);
            return null;
        }

        long mtime;
        try {
            mtime = Files.getLastModifiedTime(skillFile).toMillis();
        } catch (IOException e) {
            mtime = 0L;
        }

        CachedSkill cached = skillCache.get(skillName);
        if (cached != null && cached.mtime == mtime) {
            return cached.info;
        }

        try {
            String raw = Files.readString(skillFile, StandardCharsets.UTF_8);
            SkillInfo info = parseSkill(skillName, raw);
            skillCache.put(skillName, new CachedSkill(info, mtime));
            return info;
        } catch (IOException e) {
            log.error("Failed to load skill {}: {}", skillName, e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private SkillInfo parseSkill(String skillName, String raw) {
        SkillInfo info = new SkillInfo();
        info.setRawContent(raw);

        Matcher matcher = FRONTMATTER_PATTERN.matcher(raw);
        if (matcher.find()) {
            String yamlText = matcher.group(1);
            String body = raw.substring(matcher.end());
            info.setBody(body);

            try {
                Object parsed = new Yaml().load(yamlText);
                if (parsed instanceof Map) {
                    Map<String, Object> meta = (Map<String, Object>) parsed;
                    Object name = meta.get("name");
                    Object desc = meta.get("description");
                    Object enabled = meta.get("enabled");
                    info.setName(name != null ? name.toString() : skillName);
                    info.setDescription(desc != null ? desc.toString() : null);
                    info.setEnabled(enabled == null || Boolean.TRUE.equals(enabled)
                            || "true".equalsIgnoreCase(String.valueOf(enabled)));
                    return info;
                }
            } catch (Exception e) {
                log.warn("Frontmatter parse failed for {}: {}", skillName, e.getMessage());
            }
        } else {
            info.setBody(raw);
        }

        info.setName(skillName);
        info.setEnabled(true);
        return info;
    }

    public String loadReference(String skillName, String referencePath) {
        Path refFile = skillsRoot.resolve(skillName).resolve(referencePath).normalize();
        Path skillDir = skillsRoot.resolve(skillName).normalize();
        if (!refFile.startsWith(skillDir)) {
            log.warn("Reference path escapes skill dir: {}/{}", skillName, referencePath);
            return null;
        }
        if (!Files.exists(refFile)) {
            log.warn("Reference not found: {}/{}", skillName, referencePath);
            return null;
        }
        try {
            return Files.readString(refFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read reference {}/{}: {}", skillName, referencePath, e.getMessage());
            return null;
        }
    }

    public List<String> listReferences(String skillName) {
        Path refDir = skillsRoot.resolve(skillName).resolve("references");
        if (!Files.isDirectory(refDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(refDir)) {
            List<String> names = new ArrayList<>();
            stream.filter(Files::isRegularFile).forEach(p -> names.add(p.getFileName().toString()));
            return names;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public String resolveDocParserSkill(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".mdx")) return "doc-parser-markdown";
        if (lower.endsWith(".pdf")) return "doc-parser-pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "doc-parser-word";
        return null;
    }
}
