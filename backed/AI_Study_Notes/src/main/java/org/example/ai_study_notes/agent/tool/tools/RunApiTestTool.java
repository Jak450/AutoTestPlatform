package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
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
 * 单接口执行（需用户确认）。
 */
@Component
public class RunApiTestTool implements ToolExecutor {

    private final ApiTestService apiTestService;

    public RunApiTestTool(ApiTestService apiTestService) {
        this.apiTestService = apiTestService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("run_api_test")
                .label("单接口执行")
                .description("执行单个 API 请求并返回响应与断言结果，需要 method 与 url，header/param/assertStr 为 JSON 字符串")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "method", Map.of("type", "string", "enum", List.of("GET", "POST", "PUT", "DELETE", "PATCH")),
                                "url", Map.of("type", "string", "description", "完整接口地址"),
                                "header", Map.of("type", "string", "description", "请求头 JSON 字符串"),
                                "param", Map.of("type", "string", "description", "请求参数 JSON 字符串"),
                                "assertStr", Map.of("type", "string", "description", "断言配置 JSON 字符串")),
                        "required", List.of("method", "url")))
                .permission(ToolPermission.CONFIRM_EXECUTE)
                .category("执行")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        ApiRequestDTO request = ApiRequestDTO.builder()
                .method(Args.str(args, "method"))
                .url(Args.str(args, "url"))
                .header(Args.str(args, "header"))
                .param(Args.str(args, "param"))
                .assertStr(Args.str(args, "assertStr"))
                .build();
        ApiResponseVO response = apiTestService.run(request);
        return ToolResult.success("run_api_test", response, "接口已执行，HTTP " + response.getStatus());
    }
}
