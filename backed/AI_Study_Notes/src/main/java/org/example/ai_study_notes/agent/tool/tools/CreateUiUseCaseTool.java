package org.example.ai_study_notes.agent.tool.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestStepDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiUseCaseDTO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.example.ai_study_notes.service.UIUseCaseService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新增 UI 用例（需用户确认）。
 */
@Component
public class CreateUiUseCaseTool implements ToolExecutor {

    private final UIUseCaseService uiUseCaseService;
    private final ObjectMapper objectMapper;

    public CreateUiUseCaseTool(UIUseCaseService uiUseCaseService, ObjectMapper objectMapper) {
        this.uiUseCaseService = uiUseCaseService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("create_ui_use_case")
                .label("新增UI用例")
                .description("新增一个 UI 测试用例，需要 projectId、name、url；browser/viewport/headless/timeout/steps/description 可选")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "projectId", Map.of("type", "string", "description", "UI 项目ID"),
                                "name", Map.of("type", "string", "description", "用例名称"),
                                "url", Map.of("type", "string", "description", "目标URL"),
                                "browser", Map.of("type", "string", "enum", List.of("chrome", "firefox", "edge")),
                                "viewport", Map.of("type", "string"),
                                "headless", Map.of("type", "boolean"),
                                "timeout", Map.of("type", "integer", "minimum", 1),
                                "description", Map.of("type", "string"),
                                "steps", Map.of("type", "array", "items", Map.of("type", "object"))),
                        "required", List.of("projectId", "name", "url")))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("写入")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        UiUseCaseDTO dto = new UiUseCaseDTO();
        dto.setProjectId(Args.str(args, "projectId"));
        dto.setName(Args.str(args, "name"));
        dto.setUrl(Args.str(args, "url"));
        dto.setBrowser(Args.str(args, "browser"));
        dto.setViewport(Args.str(args, "viewport"));
        dto.setHeadless(Args.bool(args, "headless", null));
        dto.setTimeout(Args.integer(args, "timeout", null));
        dto.setDescription(Args.str(args, "description"));
        List<Map<String, Object>> stepMaps = Args.listOfMaps(args, "steps");
        List<UiTestStepDTO> steps = new ArrayList<>();
        for (Map<String, Object> stepMap : stepMaps) {
            steps.add(objectMapper.convertValue(stepMap, UiTestStepDTO.class));
        }
        dto.setSteps(steps);
        uiUseCaseService.addUseCase(dto);
        return ToolResult.success("create_ui_use_case", dto, "UI 用例创建成功: " + dto.getName());
    }
}
