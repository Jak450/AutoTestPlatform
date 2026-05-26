package org.example.ai_study_notes.aiservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.mapper.TestCaseReportMapper;
import org.example.ai_study_notes.Pojo.entity.TestCaseReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class ResultAnalyzer {

    private static final String SYSTEM_PROMPT = """
            你是一个测试结果分析专家。你的任务是分析 API 测试执行结果，诊断失败根因。

            分析维度：
            1. HTTP 状态码分析（4xx/5xx 的具体含义）
            2. 响应体内容分析（错误信息、异常堆栈）
            3. 断言失败分析（期望值 vs 实际值）
            4. 耗时异常分析（响应时间过长）

            输出格式必须是 JSON：
            {
              "verdict": "passed|failed|broken",
              "rootCause": "错误根因描述",
              "errorType": "http_error|assert_error|timeout|server_error",
              "analysis": "详细分析说明",
              "suggestion": "修复建议",
              "confidence": "high|medium|low"
            }
            """;

    @Autowired
    private AIClient aiClient;

    @Autowired
    private TestCaseReportMapper reportMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String analyze(Integer reportId) {
        TestCaseReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return "{\"verdict\":\"unknown\",\"analysis\":\"未找到报告ID: " + reportId + "\"}";
        }

        String prompt = String.format("""
                请分析以下 API 测试执行结果：

                【基本信息】
                用例名称: %s
                模块名称: %s
                接口地址: %s
                HTTP方法: %s
                执行状态: %s
                响应耗时: %d ms

                【请求信息】
                请求头: %s
                请求体: %s

                【响应信息】
                响应状态码: %d
                响应头: %s
                响应体: %s

                【断言结果】
                %s
                """,
                safe(report.getCaseName()),
                safe(report.getModuleName()),
                safe(report.getApiUrl()),
                safe(report.getRequestMethod()),
                safe(report.getStatus()),
                report.getDuration() != null ? report.getDuration() : 0,
                safe(report.getRequestHeaders()),
                safe(report.getRequestBody()),
                report.getResponseStatus() != null ? report.getResponseStatus() : 0,
                safe(report.getResponseHeaders()),
                safe(report.getResponseBody()),
                safe(report.getAssertDetail())
        );

        return aiClient.chat("deepseek-v4-pro", SYSTEM_PROMPT, prompt);
    }

    private String safe(String s) {
        return s != null ? s : "(空)";
    }
}