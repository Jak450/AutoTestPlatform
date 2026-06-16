package org.example.ai_study_notes.aiservice.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.example.ai_study_notes.aiservice.context.DocContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class RequirementAnalyzer {

    private static final String SYSTEM_PROMPT = """
            你是一个测试需求分析师。你的任务是：
            1. 分析解析后的需求文档，提取所有 API 接口信息
            2. 识别信息缺口（缺少URL、方法、参数、断言等）
            3. 生成澄清问题列表
            4. 输出结构化分析结果

            【提问策略 —— 用少量问题覆盖大部分缺陷】
            你的目标是：用 ≤5 个问题，让生成的用例能覆盖 80% 以上的缺陷场景。
            遵循以下策略：

            1. 汇总确认 > 逐个询问
               把同类信息合并成一个问题。例如：
               "请确认以下接口信息是否正确：\n- 登录: POST /api/login\n- 查询: GET /api/users"
               而不是对每个接口分别问 URL、方法。

            2. 优先问影响面大的问题
               - 鉴权方式（影响所有接口的异常用例）
               - 参数校验规则（影响所有接口的边界/异常用例）
               - 错误码规范（影响所有接口的断言）

            3. 能推断的不要问
               - RESTful 风格接口的方法可以从命名推断
               - Content-Type 默认 application/json
               - 分页参数默认 page/pageSize

            4. 每个问题尽量带选项，确实无法提供时可以不填

            输出格式必须是 JSON：
            {
              "summary": "需求概述",
              "apiCount": 3,
              "apis": [{"name":"...", "module":"...", "url":"...", "method":"...", "hasParams":true, "hasAssertions":false}],
              "missingInfo": ["缺少登录接口的URL", "缺少用户查询接口的断言条件"],
              "questions": [
                {"id":1, "field":"auth", "question":"哪些接口需要登录认证？认证方式是？", "options":["全部需要，Bearer Token", "仅写操作需要", "不需要认证"]},
                {"id":2, "field":"validation", "question":"关键参数有哪些校验规则？", "options":["必填校验", "长度/格式校验", "业务规则校验"]}
              ]
            }
            """;

    @Autowired
    private AIModelConfig aiModelConfig;

    @Autowired
    private AIClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> analyze(DocContext context) {
        String userPrompt = "请分析以下需求文档内容：\n\n" + context.getParsedJson();
        if (context.getProjectId() != null) {
            userPrompt += "\n\n目标项目ID: " + context.getProjectId();
        }

        String result = aiClient.chat(
                aiModelConfig.getRequirementAnalysis(),
                SYSTEM_PROMPT,
                userPrompt
        );

        return parseResult(result);
    }

    public Map<String, Object> analyzeStream(DocContext context, Consumer<String> onToken) {
        String userPrompt = "请分析以下需求文档内容：\n\n" + context.getParsedJson();
        if (context.getProjectId() != null) {
            userPrompt += "\n\n目标项目ID: " + context.getProjectId();
        }

        String result = aiClient.chatStream(
                aiModelConfig.getRequirementAnalysis(),
                SYSTEM_PROMPT,
                userPrompt,
                onToken
        );

        return parseResult(result);
    }

    private Map<String, Object> parseResult(String result) {
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
