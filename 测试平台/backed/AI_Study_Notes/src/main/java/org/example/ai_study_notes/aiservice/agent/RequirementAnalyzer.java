package org.example.ai_study_notes.aiservice.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.context.DocContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RequirementAnalyzer {

    private static final String SYSTEM_PROMPT = """
            你是一个测试需求分析师。你的任务是：
            1. 分析解析后的需求文档，提取所有 API 接口信息
            2. 识别信息缺口（缺少URL、方法、参数、断言等）
            3. 生成澄清问题列表
            4. 输出结构化分析结果

            输出格式必须是 JSON：
            {
              "summary": "需求概述",
              "apiCount": 3,
              "apis": [{"name":"...", "module":"...", "url":"...", "method":"...", "hasParams":true, "hasAssertions":false}],
              "missingInfo": ["缺少登录接口的URL", "缺少用户查询接口的断言条件"],
              "questions": [
                {"id":1, "field":"url", "question":"登录接口的完整URL是什么？", "context":"文档中提到了登录功能但未给出接口地址"}
              ]
            }
            """;

    @Autowired
    private AIClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> analyze(DocContext context) {
        String userPrompt = "请分析以下需求文档内容：\n\n" + context.getParsedJson();
        if (context.getProjectId() != null) {
            userPrompt += "\n\n目标项目ID: " + context.getProjectId();
        }

        String result = aiClient.chat(
                "doubao-seed-2.0-pro",
                SYSTEM_PROMPT,
                userPrompt
        );

        try {
            Map<String, Object> parsed = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            log.info("需求分析完成，识别到 {} 个接口", parsed.getOrDefault("apiCount", 0));
            return parsed;
        } catch (Exception e) {
            log.error("解析分析结果失败: {}", e.getMessage(), e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", "分析结果解析失败");
            fallback.put("raw", result);
            return fallback;
        }
    }
}