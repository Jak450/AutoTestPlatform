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
 * 查看任务计划详情（只读）。
 */
@Component
public class GetTaskPlanTool implements ToolExecutor {

    private final TaskPlanService taskPlanService;

    public GetTaskPlanTool(TaskPlanService taskPlanService) {
        this.taskPlanService = taskPlanService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("get_task_plan")
                .label("任务计划详情")
                .description("查看任务计划详情（标题/状态/清单）。参数：taskId 任务ID")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("taskId", Map.of("type", "string", "description", "任务ID")),
                        "required", List.of("taskId")))
                .permission(ToolPermission.READ)
                .category("任务")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        TaskPlan plan = taskPlanService.get(context.getUserId(), Args.str(args, "taskId"));
        return ToolResult.success("get_task_plan",
                Map.of("taskId", plan.taskId(), "title", plan.title(), "status", plan.status(),
                        "items", plan.items()),
                "任务计划: " + plan.title() + " [" + plan.status() + "]");
    }
}
