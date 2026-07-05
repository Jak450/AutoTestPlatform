package org.example.ai_study_notes.aiservice.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AskUserTool implements Tool {

    private static final String SYSTEM_PROMPT = """
            你是测试需求澄清专家。根据当前分析结果生成澄清问题。
            输出 JSON：
            {
              "questions": [
                {"id": 1, "field": "auth", "question": "鉴权方式？", "options": ["Bearer Token", "不需要"]}
              ],
              "canGenerate": false
            }
            最多 5 个问题。信息足够时 canGenerate 设 true，questions 为空数组。
            """;

    @Autowired
    private AIClient aiClient;
    @Autowired
    private AIModelConfig aiModelConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "ask_user";
    }

    @Override
    public String getDescription() {
        return "生成澄清问题返回给用户。参数：analysisResult（分析结果 JSON）";
    }

    @Override
    public String execute(String args) {
        try {
            String result = aiClient.chat(
                    aiModelConfig.getQuestionGeneration(),
                    SYSTEM_PROMPT,
                    "请根据以下分析结果生成澄清问题：\n\n" + args
            );
            log.info("AskUserTool 完成");
            return result;
        } catch (Exception e) {
            log.error("AskUserTool 执行失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
