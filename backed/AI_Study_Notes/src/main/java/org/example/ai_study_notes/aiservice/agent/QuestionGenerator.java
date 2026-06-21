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
public class QuestionGenerator {

    private static final String SYSTEM_PROMPT = """
            你是一个测试需求澄清专家。你的任务是根据需求分析结果，生成需要向用户提出的澄清问题。

            【提问策略 —— 用少量问题覆盖大部分缺陷】
            你的目标是：用 ≤5 个问题，让生成的用例能覆盖 80% 以上的缺陷场景。

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
              "questions": [
                {"id":1, "field":"auth", "question":"哪些接口需要登录认证？认证方式是？", "options":["全部需要，Bearer Token", "仅写操作需要", "不需要认证"]},
                {"id":2, "field":"validation", "question":"关键参数有哪些校验规则？", "options":["必填校验", "长度/格式校验", "业务规则校验"]}
              ],
              "canGenerate": false
            }

            如果信息足够直接生成用例，设置 "canGenerate": true，questions 为空数组。
            """;

    @Autowired
    private AIModelConfig aiModelConfig;

    @Autowired
    private AIClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generate(DocContext context) {
        String prompt = "请根据以下需求分析结果，生成需要向用户提出的澄清问题：\n\n"
                + context.getAnalysisResult() + "\n\n"
                + context.buildQAPrompt();

        String result = aiClient.chat(
                aiModelConfig.getQuestionGeneration(),
                SYSTEM_PROMPT,
                prompt
        );

        return parseResult(result);
    }
//流式输出SSE
    public Map<String, Object> generateStream(DocContext context, Consumer<String> onToken) {
        String prompt = "请根据以下需求分析结果，生成需要向用户提出的澄清问题：\n\n"
                + context.getAnalysisResult() + "\n\n"
                + context.buildQAPrompt();

        String result = aiClient.chatStream(
                aiModelConfig.getQuestionGeneration(),
                SYSTEM_PROMPT,
                prompt,
                onToken
        );

        return parseResult(result);
    }

    private Map<String, Object> parseResult(String result) {
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
