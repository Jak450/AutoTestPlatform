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
public class QuestionGenerator {

    private static final String SYSTEM_PROMPT = """
            你是一个测试需求澄清专家。你的任务是根据需求分析结果，生成需要向用户提出的澄清问题。

            问题类型包括：
            - URL缺失：请提供完整的接口地址
            - 方法缺失：该接口使用什么HTTP方法？
            - 参数不明确：请提供请求参数示例
            - 断言不明确：如何判断接口返回正确？
            - 场景遗漏：是否需要考虑异常流程？

            输出格式必须是 JSON：
            {
              "questions": [
                {"id":1, "field":"url", "question":"登录接口的完整URL是什么？", "options":["请提供完整URL", "我可以从文档中推断"]},
                {"id":2, "field":"method", "question":"该接口使用什么HTTP方法？", "options":["GET", "POST", "PUT", "DELETE"]}
              ],
              "canGenerate": false
            }

            如果信息足够直接生成用例，设置 "canGenerate": true。
            """;

    @Autowired
    private AIClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generate(DocContext context) {
        String prompt = "请根据以下需求分析结果，生成需要向用户提出的澄清问题：\n\n"
                + context.getAnalysisResult() + "\n\n"
                + context.buildQAPrompt();

        String result = aiClient.chat(
                "deepseek-v4-flash",
                SYSTEM_PROMPT,
                prompt
        );

        try {
            return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析问题生成结果失败: {}", e.getMessage(), e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("canGenerate", true);
            fallback.put("questions", List.of());
            return fallback;
        }
    }
}