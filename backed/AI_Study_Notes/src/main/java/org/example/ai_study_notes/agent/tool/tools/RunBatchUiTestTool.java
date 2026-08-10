package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiBatchExecuteResultVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.UiTestService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * UI 批量执行（需用户确认）。
 */
@Component
public class RunBatchUiTestTool implements ToolExecutor {

    private final UiTestService uiTestService;

    public RunBatchUiTestTool(UiTestService uiTestService) {
        this.uiTestService = uiTestService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("run_batch_ui_test")
                .label("UI批量执行")
                .description("批量并发执行 UI 测试用例，需要 useCaseIds 数组；executionCount 默认1，maxConcurrency 默认5")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "useCaseIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                                "executionCount", Map.of("type", "integer", "minimum", 1, "maximum", 100),
                                "maxConcurrency", Map.of("type", "integer", "minimum", 1, "maximum", 50)),
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
        UiBatchExecuteResultVO result = uiTestService.batchTest(dto);
        return ToolResult.success("run_batch_ui_test", result,
                "UI 批量执行完成：成功 " + result.getSuccess() + "，失败 " + result.getFailed());
    }
}
