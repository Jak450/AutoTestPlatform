package org.example.ai_study_notes.aiservice.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalyzeGapTool implements Tool {

    private static final String SYSTEM_PROMPT = """
            你是测试需求分析师。分析解析后的需求文档，识别信息缺口。
            输出 JSON：
            {
              "summary": "需求概述",
              "apiCount": 3,
              "gaps": ["缺少URL", "缺少断言"],
              "questions": [
                {"id": 1, "field": "auth", "question": "鉴权方式是什么？", "options": ["Bearer Token", "不需要"]}
              ]
            }
            最多 5 个问题，按影响面优先排序。
            """;

    @Autowired
    private AIClient aiClient;
    @Autowired
    private AIModelConfig aiModelConfig;

    @Override
    public String getName() {
        return "analyze_gap";
    }

    @Override
    public String getDescription() {
        return "分析需求文档的信息缺口，返回缺失信息和澄清问题。参数：parsedDoc（解析后的文档 JSON）";
    }

    @Override
    public String execute(String args) {
        try {
            String result = aiClient.chat(
                    aiModelConfig.getRequirementAnalysis(),
                    SYSTEM_PROMPT,
                    "请分析以下需求文档：\n\n" + args
            );
            log.info("AnalyzeGapTool 完成");
            return result;
        } catch (Exception e) {
            log.error("AnalyzeGapTool 执行失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}




