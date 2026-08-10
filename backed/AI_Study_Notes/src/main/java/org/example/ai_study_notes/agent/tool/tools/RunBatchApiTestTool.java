package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.report.AgentReportRecorder;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.ApiTestService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * API 批量执行（需用户确认）。
 */
@Component
public class RunBatchApiTestTool implements ToolExecutor {

    private final ApiTestService apiTestService;
    private final AgentReportRecorder reportRecorder;

    public RunBatchApiTestTool(ApiTestService apiTestService, AgentReportRecorder reportRecorder) {
        this.apiTestService = apiTestService;
        this.reportRecorder = reportRecorder;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("run_batch_api_test")
                .label("API批量执行")
                .description("批量并发执行 API 测试用例，需要 useCaseIds 数组；executionCount 默认1，maxConcurrency 默认5")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "useCaseIds", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "用例ID数组"),
                                "executionCount", Map.of("type", "integer", "minimum", 1, "maximum", 100, "description", "每个用例执行次数"),
                                "maxConcurrency", Map.of("type", "integer", "minimum", 1, "maximum", 50, "description", "最大并发数")),
                        "required", List.of("useCaseIds")))
                .permission(ToolPermission.CONFIRM_EXECUTE)
                .category("执行")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        BatchExecuteDTO dto = BatchExecuteDTO.builder()
                .useCaseIds(Args.integerList(args, "useCaseIds"))
                .executionCount(Args.integer(args, "executionCount", 1))
                .maxConcurrency(Args.integer(args, "maxConcurrency", 5))
                .build();
        BatchExecuteResultVO result = apiTestService.batchExecute(dto);
        reportRecorder.recordBatch(dto, result);
        return ToolResult.success("run_batch_api_test", result,
                "批量执行完成：成功 " + result.getSuccess() + "，失败 " + result.getFailed());
    }
}
