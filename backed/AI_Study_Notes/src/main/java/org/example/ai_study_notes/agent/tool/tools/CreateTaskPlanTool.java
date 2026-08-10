package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.task.TaskPlan;
import org.example.ai_study_notes.agent.task.TaskPlanService;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 创建任务计划（MD 清单，自动执行）：复杂/多步任务先建清单再执行。
 */
@Component
public class CreateTaskPlanTool implements ToolExecutor {

    private final TaskPlanService taskPlanService;

    public CreateTaskPlanTool(TaskPlanService taskPlanService) {
        this.taskPlanService = taskPlanService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("create_task_plan")
                .label("创建任务计划")
                .description("为复杂/多步任务创建任务清单（MD 存储，带状态）。参数：title 任务标题、items 步骤数组（至少 1 步）。创建后请按步骤执行，每完成一步调用 update_task_plan 勾选")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "任务标题"),
                                "items", Map.of("type", "array", "items", Map.of("type", "string"),
                                        "description", "步骤清单（按执行顺序）")),
                        "required", List.of("title", "items")))
                .permission(ToolPermission.AUTO_WRITE)
                .category("任务")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        TaskPlan plan = taskPlanService.create(
                context.getUserId(),
                context.getConversationId(),
                Args.str(args, "title"),
                Args.strList(args, "items"));
        return ToolResult.success("create_task_plan",
                Map.of("taskId", plan.taskId(), "title", plan.title(), "status", plan.status(),
                        "items", plan.items()),
                "任务计划已创建: " + plan.title());
    }
}
