package org.example.ai_study_notes.agent.task;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务计划（对应 {data-dir}/tasks/{userId}/{taskId}.md）。
 * 状态：pending / in_progress / done / blocked / failed。
 */
public record TaskPlan(
        String taskId,
        String title,
        String status,
        Long sourceConversationId,
        List<TaskItem> items,
        LocalDateTime updatedAt) {

    public record TaskItem(String text, boolean checked) {
    }
}
