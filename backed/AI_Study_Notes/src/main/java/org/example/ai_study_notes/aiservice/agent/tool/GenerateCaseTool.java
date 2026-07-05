package org.example.ai_study_notes.aiservice.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.example.ai_study_notes.aiservice.skill.SkillLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GenerateCaseTool implements Tool {

    @Autowired
    private AIClient aiClient;
    @Autowired
    private AIModelConfig aiModelConfig;
    @Autowired
    private SkillLoader skillLoader;

    @Override
    public String getName() {
        return "generate_case";
    }

    @Override
    public String getDescription() {
        return "根据需求文档和问答记录生成 API 测试用例。参数：parsedDoc（解析后的文档），qaHistory（问答记录，JSON 数组字符串）";
    }

    @Override
    public String execute(String args) {
        try {
            var parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(args);
            String parsedDoc = parsed.has("parsedDoc") ? parsed.get("parsedDoc").asText() : "";
            String qaHistory = parsed.has("qaHistory") ? parsed.get("qaHistory").asText() : "[]";

            SkillLoader.SkillInfo skill = skillLoader.load("test-case-generator-api");
            if (skill == null || !skill.isEnabled()) {
                return "{\"error\": \"test-case-generator-api skill 未启用\"}";
            }

            String userPrompt = "请根据以下需求和问答记录，生成 API 测试用例：\n\n"
                    + "需求文档：\n" + parsedDoc + "\n\n"
                    + "问答记录：\n" + qaHistory + "\n\n"
                    + "请严格按照 SKILL.md 的格式输出 JSON。";

            String result = aiClient.chat(
                    aiModelConfig.getTestCaseGeneration(),
                    skill.getContent(),
                    userPrompt
            );

            log.info("GenerateCaseTool 完成");
            return result;
        } catch (Exception e) {
            log.error("GenerateCaseTool 执行失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
