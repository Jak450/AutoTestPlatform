package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.ReportQueryDTO;
import org.example.ai_study_notes.Pojo.entity.TestCaseReport;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.TestReportService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询测试执行报告，支持按模块/用例名/状态/时间范围筛选。
 */
@Component
public class QueryReportsTool implements ToolExecutor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TestReportService testReportService;

    public QueryReportsTool(TestReportService testReportService) {
        this.testReportService = testReportService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("query_reports")
                .label("查询测试报告")
                .description("查询测试执行报告，可按模块名 moduleName、用例名 caseName、状态 status(passed/failed/broken)、时间范围 startTime/endTime(格式 yyyy-MM-dd HH:mm:ss) 筛选")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "moduleName", Map.of("type", "string", "description", "模块名称"),
                                "caseName", Map.of("type", "string", "description", "用例名称关键字"),
                                "status", Map.of("type", "string", "enum", List.of("passed", "failed", "broken")),
                                "startTime", Map.of("type", "string", "description", "开始时间 yyyy-MM-dd HH:mm:ss"),
                                "endTime", Map.of("type", "string", "description", "结束时间 yyyy-MM-dd HH:mm:ss")),
                        "required", List.of()))
                .permission(ToolPermission.READ)
                .category("查询")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        ReportQueryDTO query = ReportQueryDTO.builder()
                .moduleName(Args.str(args, "moduleName"))
                .caseName(Args.str(args, "caseName"))
                .status(Args.str(args, "status"))
                .startTime(parseTime(Args.str(args, "startTime")))
                .endTime(parseTime(Args.str(args, "endTime")))
                .build();
        List<TestCaseReport> reports = testReportService.queryCaseReports(query);
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (TestCaseReport report : reports) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", report.getId());
            map.put("caseId", report.getCaseId());
            map.put("caseName", report.getCaseName());
            map.put("moduleName", report.getModuleName());
            map.put("status", report.getStatus());
            map.put("apiUrl", report.getApiUrl());
            map.put("requestMethod", report.getRequestMethod());
            map.put("responseStatus", report.getResponseStatus());
            map.put("duration", report.getDuration());
            map.put("startTime", report.getStartTime());
            map.put("endTime", report.getEndTime());
            summaries.add(map);
        }
        return ToolResult.success("query_reports", summaries, "共 " + summaries.size() + " 条执行记录");
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
