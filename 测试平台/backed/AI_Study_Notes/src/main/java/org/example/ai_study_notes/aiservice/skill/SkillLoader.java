package org.example.ai_study_notes.aiservice.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SkillLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SkillInfo> skillCache = new HashMap<>();
    private final Path skillsRoot;

    public SkillLoader() {
        String userDir = System.getProperty("user.dir").replace("\\", "/");
        Path p = null;
        String[] candidates = {
                userDir + "/../../../skills",
                userDir + "/../../skills",
                userDir + "/../skills",
                userDir.replace("/backed/AI_Study_Notes", "") + "/skills",
                userDir + "/skills"
        };
        for (String c : candidates) {
            Path path = Path.of(c).normalize();
            if (Files.exists(path.resolve("register.json"))) {
                p = path;
                break;
            }
        }
        if (p == null) {
            p = Path.of(candidates[candidates.length - 1]).normalize();
        }
        skillsRoot = p;
        log.info("Skills root resolved to: {}", skillsRoot);
    }

    public static class SkillInfo {
        private String name;
        private String content;
        private boolean enabled;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public SkillInfo load(String skillName) {
        if (skillCache.containsKey(skillName)) {
            return skillCache.get(skillName);
        }

        Path skillDir = skillsRoot.resolve(skillName);
        Path skillFile = skillDir.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            log.warn("Skill not found: {}", skillName);
            return null;
        }

        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);

            Path registerFile = skillsRoot.resolve("register.json");
            boolean enabled = true;
            if (Files.exists(registerFile)) {
                Map<String, Object> register = objectMapper.readValue(
                        registerFile.toFile(), new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> skills = (List<Map<String, Object>>) register.get("skills");
                for (Map<String, Object> s : skills) {
                    if (skillName.equals(s.get("name"))) {
                        enabled = Boolean.TRUE.equals(s.get("enabled"));
                        break;
                    }
                }
            }

            SkillInfo info = new SkillInfo();
            info.setName(skillName);
            info.setContent(content);
            info.setEnabled(enabled);
            skillCache.put(skillName, info);
            return info;

        } catch (IOException e) {
            log.error("Failed to load skill {}: {}", skillName, e.getMessage(), e);
            return null;
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