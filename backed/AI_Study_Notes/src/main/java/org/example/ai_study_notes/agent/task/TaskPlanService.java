package org.example.ai_study_notes.agent.task;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 任务计划：以 MD 文件按用户存储（{data-dir}/tasks/{userId}/{taskId}.md），
 * frontmatter 记录状态与来源会话，正文为可勾选清单（- [ ] / - [x]）。
 * 用于长任务执行前的清单规划与执行过程中的状态维护/注入。
 */
@Slf4j
@Service
public class TaskPlanService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_BLOCKED = "blocked";
    public static final String STATUS_FAILED = "failed";
    private static final Set<String> VALID_STATUS =
            Set.of(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_DONE, STATUS_BLOCKED, STATUS_FAILED);
    private static final String CHECKLIST_HEADER = "## 任务清单";

    private final AgentProperties properties;

    public TaskPlanService(AgentProperties properties) {
        this.properties = properties;
    }

    public Path userRoot(Long userId) {
        return Path.of(properties.getDataDir()).resolve("tasks").resolve(String.valueOf(userId));
    }

    public TaskPlan create(Long userId, Long conversationId, String title, List<String> items) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("任务标题不能为空");
        }
        if (items == null || items.stream().noneMatch(s -> s != null && !s.isBlank())) {
            throw new IllegalArgumentException("任务清单至少包含一个步骤");
        }
        String taskId = sanitize(title) + "-" + UUID.randomUUID().toString().substring(0, 6);
        Path target = userRoot(userId).resolve(taskId + ".md");
        try {
            writePlan(target, taskId, title.trim(), STATUS_PENDING, conversationId, items);
        } catch (IOException e) {
            throw new IllegalStateException("任务计划写入失败: " + e.getMessage(), e);
        }
        log.info("任务计划已创建 userId={} taskId={} title={}", userId, taskId, title);
        return get(userId, taskId);
    }

    public TaskPlan get(Long userId, String taskId) {
        String safe = sanitize(taskId);
        Path target = userRoot(userId).resolve(safe + ".md");
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("任务计划不存在: " + safe);
        }
        return readPlan(target);
    }

    public List<TaskPlan> list(Long userId, Long conversationId) {
        Path root = userRoot(userId);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<TaskPlan> plans = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            TaskPlan plan = readPlan(p);
                            if (conversationId == null
                                    || conversationId.equals(plan.sourceConversationId())) {
                                plans.add(plan);
                            }
                        } catch (Exception e) {
                            log.warn("任务计划读取失败，跳过 {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("任务计划目录扫描失败 {}", root);
        }
        plans.sort(Comparator.comparing(TaskPlan::updatedAt,
                Comparator.nullsFirst(Comparator.reverseOrder())));
        return plans;
    }

    /**
     * 更新状态与勾选项：status 可选；doneItems 中的文本（支持前缀匹配）被勾选为完成。
     */
    public TaskPlan update(Long userId, String taskId, String status, List<String> doneItems) {
        TaskPlan current = get(userId, taskId);
        String newStatus = status == null || status.isBlank() ? current.status() : status.trim();
        if (!VALID_STATUS.contains(newStatus)) {
            throw new IllegalArgumentException("非法任务状态: " + newStatus + "，可选 " + VALID_STATUS);
        }
        List<TaskPlan.TaskItem> items = new ArrayList<>();
        for (TaskPlan.TaskItem item : current.items()) {
            boolean checked = item.checked();
            if (doneItems != null) {
                for (String done : doneItems) {
                    if (done != null && !done.isBlank()
                            && (item.text().equals(done.trim()) || item.text().startsWith(done.trim()))) {
                        checked = true;
                        break;
                    }
                }
            }
            items.add(new TaskPlan.TaskItem(item.text(), checked));
        }
        Path target = userRoot(userId).resolve(sanitize(taskId) + ".md");
        try {
            writePlan(target, current.taskId(), current.title(), newStatus,
                    current.sourceConversationId(),
                    items.stream().map(TaskPlan.TaskItem::text).toList(),
                    items.stream().map(TaskPlan.TaskItem::checked).toList());
        } catch (IOException e) {
            throw new IllegalStateException("任务计划更新失败: " + e.getMessage(), e);
        }
        log.info("任务计划已更新 userId={} taskId={} status={}",
                userId, current.taskId(), newStatus);
        return get(userId, taskId);
    }

    public void delete(Long userId, String taskId) {
        Path target = userRoot(userId).resolve(sanitize(taskId) + ".md");
        try {
            if (!Files.deleteIfExists(target)) {
                throw new IllegalArgumentException("任务计划不存在: " + taskId);
            }
        } catch (IOException e) {
            throw new IllegalStateException("任务计划删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 当前会话的活动任务计划（pending/in_progress），无则返回 null。
     */
    public TaskPlan activePlan(Long userId, Long conversationId) {
        return list(userId, conversationId).stream()
                .filter(p -> STATUS_PENDING.equals(p.status()) || STATUS_IN_PROGRESS.equals(p.status()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 注入用的紧凑清单文本；无活动计划返回空串。
     */
    public String injectable(Long userId, Long conversationId) {
        TaskPlan plan = activePlan(userId, conversationId);
        if (plan == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(plan.title()).append(" [").append(plan.status()).append("]\n");
        for (TaskPlan.TaskItem item : plan.items()) {
            sb.append(item.checked() ? "- [x] " : "- [ ] ").append(item.text()).append('\n');
        }
        return sb.toString();
    }

    public String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "task";
        }
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "-")
                .replaceAll("^[\\.-]+|[\\.-]+$", "");
        if (cleaned.isBlank()) {
            return "task";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private void writePlan(Path target, String taskId, String title, String status, Long conversationId,
                           List<String> items) throws IOException {
        writePlan(target, taskId, title, status, conversationId, items, null);
    }

    private void writePlan(Path target, String taskId, String title, String status, Long conversationId,
                           List<String> items, List<Boolean> checked) throws IOException {
        Files.createDirectories(target.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("task_id: ").append(taskId).append('\n');
        sb.append("title: ").append(singleLine(title)).append('\n');
        sb.append("status: ").append(status).append('\n');
        if (conversationId != null) {
            sb.append("source_conversation: ").append(conversationId).append('\n');
        }
        sb.append("updated: ").append(LocalDateTime.now()).append('\n');
        sb.append("---\n\n");
        sb.append(CHECKLIST_HEADER).append('\n');
        for (int i = 0; i < items.size(); i++) {
            String text = items.get(i);
            if (text == null || text.isBlank()) {
                continue;
            }
            boolean isChecked = checked != null && i < checked.size() && Boolean.TRUE.equals(checked.get(i));
            sb.append("- [").append(isChecked ? 'x' : ' ').append("] ")
                    .append(singleLine(text)).append('\n');
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private TaskPlan readPlan(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String taskId = "";
            String title = "";
            String status = STATUS_PENDING;
            Long conversationId = null;
            LocalDateTime updatedAt = LocalDateTime.now();
            int bodyStart = 0;
            if (lines.size() >= 2 && "---".equals(lines.get(0).trim())) {
                int i = 1;
                while (i < lines.size() && !"---".equals(lines.get(i).trim())) {
                    String line = lines.get(i);
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        switch (key) {
                            case "task_id" -> taskId = value;
                            case "title" -> title = value;
                            case "status" -> status = value;
                            case "source_conversation" -> {
                                try {
                                    conversationId = Long.parseLong(value);
                                } catch (NumberFormatException ignored) { }
                            }
                            case "updated" -> updatedAt = parseTime(value, updatedAt);
                            default -> { }
                        }
                    }
                    i++;
                }
                bodyStart = i + 1;
            }
            List<TaskPlan.TaskItem> items = new ArrayList<>();
            for (int j = bodyStart; j < lines.size(); j++) {
                String line = lines.get(j).trim();
                if (line.startsWith("- [ ] ")) {
                    items.add(new TaskPlan.TaskItem(line.substring(6).trim(), false));
                } else if (line.startsWith("- [x] ")) {
                    items.add(new TaskPlan.TaskItem(line.substring(6).trim(), true));
                }
            }
            if (taskId.isEmpty()) {
                taskId = path.getFileName().toString().replaceFirst("\\.md$", "");
            }
            return new TaskPlan(taskId, title, status, conversationId, items, updatedAt);
        } catch (IOException e) {
            throw new IllegalStateException("任务计划读取失败: " + path, e);
        }
    }

    private LocalDateTime parseTime(String value, LocalDateTime fallback) {
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private String singleLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
