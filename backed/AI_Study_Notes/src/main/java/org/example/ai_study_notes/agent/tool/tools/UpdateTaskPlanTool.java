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
 * 更新任务计划：修改状态、勾选完成步骤（自动执行）。
 */
@Component
public class UpdateTaskPlanTool implements ToolExecutor {

    private final TaskPlanService taskPlanService;

    public UpdateTaskPlanTool(TaskPlanService taskPlanService) {
        this.taskPlanService = taskPlanService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("update_task_plan")
                .label("更新任务计划")
                .description("更新任务计划状态并勾选已完成步骤。参数：taskId 任务ID（必填）、status 状态（pending/in_progress/done/blocked/failed，可选）、doneItems 已完成步骤文本数组（可选）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "taskId", Map.of("type", "string", "description", "任务ID"),
                                "status", Map.of("type", "string", "description", "任务状态"),
                                "doneItems", Map.of("type", "array", "items", Map.of("type", "string"),
                                        "description", "已完成步骤（与创建时的步骤文本匹配）")),
                        "required", List.of("taskId")))
                .permission(ToolPermission.AUTO_WRITE)
                .category("任务")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        TaskPlan plan = taskPlanService.update(
                context.getUserId(),
                Args.str(args, "taskId"),
                Args.str(args, "status"),
                Args.strList(args, "doneItems"));
        return ToolResult.success("update_task_plan",
                Map.of("taskId", plan.taskId(), "title", plan.title(), "status", plan.status(),
                        "items", plan.items()),
                "任务计划已更新: " + plan.title() + " [" + plan.status() + "]");
    }
}
