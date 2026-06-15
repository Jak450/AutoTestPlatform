package org.example.ai_study_notes.aiservice.client;

public class AIModelConfig {

    private AIModelConfig() {}

    public static final String DOC_PARSER = "deepseek-v4-flash";
    public static final String REQUIREMENT_ANALYSIS = "doubao-seed-2.0-pro";
    public static final String QUESTION_GENERATION = "deepseek-v4-flash";
    public static final String TEST_CASE_GENERATION = "doubao-seed-code";
    public static final String RESULT_ANALYSIS = "deepseek-v4-pro";

    public static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/coding/v3";
    public static final String API_KEY = "<your ARK_API_KEY>"; // @secret ARK_API_KEY
}