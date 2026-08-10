package org.example.ai_study_notes.agent.tool.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestCaseRequestDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestStepDTO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiTestResultVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.UiTestService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI 测试执行（需用户确认）。
 */
@Component
public class RunUiTestTool implements ToolExecutor {

    private final UiTestService uiTestService;
    private final ObjectMapper objectMapper;

    public RunUiTestTool(UiTestService uiTestService, ObjectMapper objectMapper) {
        this.uiTestService = uiTestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("run_ui_test")
                .label("UI测试执行")
                .description("执行单个 UI 测试请求，需要 url 与 steps（步骤对象数组），可选 browser/viewport/headless/timeout")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of("type", "string", "description", "目标URL"),
                                "browser", Map.of("type", "string", "enum", List.of("chrome", "firefox", "edge")),
                                "viewport", Map.of("type", "string", "description", "窗口大小，如 1920x1080"),
                                "headless", Map.of("type", "boolean"),
                                "timeout", Map.of("type", "integer", "minimum", 1),
                                "steps", Map.of("type", "array", "items", Map.of("type", "object"), "description", "测试步骤数组")),
                        "required", List.of("url", "steps")))
                .permission(ToolPermission.CONFIRM_EXECUTE)
                .category("执行")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        UiTestCaseRequestDTO request = new UiTestCaseRequestDTO();
        request.setUrl(Args.str(args, "url"));
        request.setBrowser(Args.str(args, "browser"));
        request.setViewport(Args.str(args, "viewport"));
        request.setHeadless(Args.bool(args, "headless", null));
        request.setTimeout(Args.integer(args, "timeout", null));
        request.setSteps(convertSteps(Args.listOfMaps(args, "steps")));
        UiTestResultVO result = uiTestService.run(request);
        return ToolResult.success("run_ui_test", result, "UI 测试执行完成");
    }

    private List<UiTestStepDTO> convertSteps(List<Map<String, Object>> steps) {
        List<UiTestStepDTO> result = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            result.add(objectMapper.convertValue(step, UiTestStepDTO.class));
        }
        return result;
    }
}
