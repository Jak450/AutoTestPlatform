package org.example.ai_study_notes.aiservice.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.context.DocContext;
import org.example.ai_study_notes.aiservice.skill.SkillLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ApiTestCaseGenerator implements TestCaseGenerator {

    @Autowired
    private AIClient aiClient;

    @Autowired
    private SkillLoader skillLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String type) {
        return "API".equals(type);
    }

    @Override
    public String generate(DocContext context) {
        SkillLoader.SkillInfo skill = skillLoader.load("test-case-generator-api");
        if (skill == null || !skill.isEnabled()) {
            throw new IllegalStateException("test-case-generator-api skill 未启用或不存在");
        }

        String userPrompt = "请根据以下需求和问答记录，生成 API 测试用例：\n\n"
                + context.buildQAPrompt()
                + "\n\n目标项目ID: " + context.getProjectId();

        if (context.getProjectName() != null) {
            userPrompt += "\n目标项目名称: " + context.getProjectName();
        }

        userPrompt += "\n\n请严格按照 SKILL.md 的格式输出 JSON 数组。";

        String result = aiClient.chat(
                "doubao-seed-code",
                skill.getContent(),
                userPrompt
        );

        try {
            Map<String, Object> parsed = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> cases = (List<Map<String, Object>>) parsed.get("cases");
            log.info("生成 {} 个 API 测试用例", cases != null ? cases.size() : 0);
        } catch (Exception e) {
            log.warn("解析生成结果失败，返回原始文本: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public List<String> validate(String generatedJson) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(generatedJson, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> cases = (List<Map<String, Object>>) parsed.get("cases");
            if (cases == null || cases.isEmpty()) {
                return List.of("未生成任何用例");
            }
            return List.of();
        } catch (Exception e) {
            return List.of("JSON 格式校验失败: " + e.getMessage());
        }
    }
}