package org.example.ai_study_notes.agent.task;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务计划 API（按用户隔离）。
 */
@RestController
@RequestMapping("/api/agent/tasks")
public class TaskPlanController {

    private final TaskPlanService taskPlanService;

    public TaskPlanController(TaskPlanService taskPlanService) {
        this.taskPlanService = taskPlanService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "conversationId", required = false) Long conversationId) {
        List<TaskPlan> plans = taskPlanService.list(UserContext.userId(), conversationId);
        return Result.success(plans.stream().map(this::toMap).toList());
    }

    @GetMapping("/{taskId}")
    public Result<Map<String, Object>> detail(@PathVariable("taskId") String taskId) {
        return Result.success(toMap(taskPlanService.get(UserContext.userId(), taskId)));
    }

    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable("taskId") String taskId) {
        taskPlanService.delete(UserContext.userId(), taskId);
        return Result.success();
    }

    private Map<String, Object> toMap(TaskPlan plan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", plan.taskId());
        map.put("title", plan.title());
        map.put("status", plan.status());
        map.put("sourceConversationId", plan.sourceConversationId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (TaskPlan.TaskItem item : plan.items()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", item.text());
            m.put("checked", item.checked());
            items.add(m);
        }
        map.put("items", items);
        map.put("updatedAt", plan.updatedAt());
        return map;
    }
}
