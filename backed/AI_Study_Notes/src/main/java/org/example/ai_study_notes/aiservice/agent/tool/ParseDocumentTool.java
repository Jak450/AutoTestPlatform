package org.example.ai_study_notes.aiservice.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.example.ai_study_notes.aiservice.skill.SkillLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParseDocumentTool implements Tool {

    @Autowired
    private AIClient aiClient;
    @Autowired
    private AIModelConfig aiModelConfig;
    @Autowired
    private SkillLoader skillLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "parse_document";
    }

    @Override
    public String getDescription() {
        return "解析需求文档，返回结构化 JSON。参数：fileName（文件名），content（文档内容）";
    }

    @Override
    public String execute(String args) {
        try {
            var parsed = objectMapper.readTree(args);
            String fileName = parsed.get("fileName").asText();
            String content = parsed.get("content").asText();

            String skillName = skillLoader.resolveDocParserSkill(fileName);
            if (skillName == null) {
                return "{\"error\": \"不支持的文件格式: " + fileName + "\"}";
            }

            SkillLoader.SkillInfo skill = skillLoader.load(skillName);
            if (skill == null || !skill.isEnabled()) {
                return "{\"error\": \"skill 未启用: " + skillName + "\"}";
            }

            String result = aiClient.chat(
                    aiModelConfig.getDocParser(),
                    skill.getContent(),
                    "请解析以下需求文档，严格按照 SKILL.md 的输出格式返回 JSON：\n\n" + content
            );

            log.info("ParseDocumentTool 完成，文件: {}", fileName);
            return result;
        } catch (Exception e) {
            log.error("ParseDocumentTool 执行失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
