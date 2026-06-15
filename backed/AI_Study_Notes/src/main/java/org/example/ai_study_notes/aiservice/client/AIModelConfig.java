package org.example.ai_study_notes.aiservice.client;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AIModelConfig {

    @Value("${ai.ark.doc-parser:deepseek-v4-flash}")
    private String docParser;

    @Value("${ai.ark.requirement-analysis:doubao-seed-2.0-pro}")
    private String requirementAnalysis;

    @Value("${ai.ark.question-generation:deepseek-v4-flash}")
    private String questionGeneration;

    @Value("${ai.ark.test-case-generation:doubao-seed-code}")
    private String testCaseGeneration;

    @Value("${ai.ark.result-analysis:deepseek-v4-pro}")
    private String resultAnalysis;

    @Value("${ai.ark.base-url:https://ark.cn-beijing.volces.com/api/coding/v3}")
    private String baseUrl;

    @Value("${ai.ark.api-key}")
    private String apiKey;
}
