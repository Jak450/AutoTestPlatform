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
 * 列出任务计划（只读）。
 */
@Component
public class ListTaskPlansTool implements ToolExecutor {

    private final TaskPlanService taskPlanService;

    public ListTaskPlansTool(TaskPlanService taskPlanService) {
        this.taskPlanService = taskPlanService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("list_task_plans")
                .label("任务计划列表")
                .description("列出任务计划（可选按会话过滤）。参数：conversationId 会话ID（可选）")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("conversationId", Map.of("type", "integer",
                                "description", "会话ID，不传列出全部")),
                        "required", List.of()))
                .permission(ToolPermission.READ)
                .category("任务")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        Integer conversationId = Args.integer(args, "conversationId", null);
        List<TaskPlan> plans = taskPlanService.list(
                context.getUserId(), conversationId == null ? null : conversationId.longValue());
        List<Map<String, Object>> data = plans.stream().map(p -> Map.<String, Object>of(
                "taskId", p.taskId(),
                "title", p.title(),
                "status", p.status(),
                "sourceConversationId", p.sourceConversationId(),
                "itemCount", p.items().size(),
                "doneCount", p.items().stream().filter(TaskPlan.TaskItem::checked).count())).toList();
        return ToolResult.success("list_task_plans", data, "共 " + data.size() + " 个任务计划");
    }
}
