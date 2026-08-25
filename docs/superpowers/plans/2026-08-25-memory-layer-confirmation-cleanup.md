# 记忆层会话式确认 + 遗忘补齐 + 前端清理 Implementation Plan（4b）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐记忆系统最后三块——Qdrant/Neo4j 遗忘（删向量、关系衰减归档）、"完整工作完成时"的会话式批量确认（超时自动确认兜底）、前端记忆面板移除与 `agent_memory` 旧链路彻底清理。

**Architecture:**
- `QdrantVectorStore.deleteByIds`：候选过期/事实版本归档时同步删向量。
- `Neo4jGraphRepository`：未确认关系衰减+过期归档（`decayUnconfirmedRelations`）、超时自动确认（`confirmUnconfirmedRelationsOlderThan`）、列待确认（`listUnconfirmedRelations`）、纠正上下文（`updateRelationContext`）、删除（`deleteRelation`）。
- 会话式确认：`PendingConfirmationService`（待确认列表 + 确认/纠正/拒绝）+ `CompletionDetector`（收尾语检测）+ `ConfirmationAnswerProcessor`（"对/不对，改成X"解析）；`AgentLoop` 在完成信号轮追加批量确认指令；`MemoryMaintenanceJob` 超时自动确认（`agent.confirm.window-hours`）。
- 前端清理：`ResourcePanel` 移除"记忆"分组，`Agent.vue` 移除记忆 API 调用；后端删 `MemoryController/MemoryService/MemoryMapper/MemoryEntry` 与 `agent_memory` 表，偏好提炼改走 `memory_fact`（entity="user"）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus / Qdrant / Neo4j / Vue 3 / JUnit 5 / Mockito

**前置：** 子计划 1-4 完成；环境就绪；`agent_memory` 仅剩开发测试数据（无需迁移）。

**范围边界：** 任务计划完成信号（`update_task_plan` 勾完）作为完成点的检测保留为后续增强，本计划用收尾语关键词 + 超时兜底；LLM 解析"对/不对"保留为后续增强，本计划用规则解析。

---

### Task 1: Qdrant 遗忘（删向量）

**Files:**
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/vector/QdrantVectorStore.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/MemoryMaintenanceJob.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/vector/QdrantVectorStoreIT.java`

- [ ] **Step 1: 写失败测试（deleteByIds）**

`QdrantVectorStoreIT` 追加：

```java
    @Test
    void deleteByIdsRemovesPoints() {
        String col = store.collectionName("facts");
        store.upsert(col, "d1", List.of(1f, 0f, 0f, 0f), Map.of("workspace_id", "1"));
        store.deleteByIds(col, List.of("d1"));
        List<QdrantVectorStore.Hit> hits = store.search(col, List.of(1f, 0f, 0f, 0f), "1", null, 2);
        assertTrue(hits.stream().noneMatch(h -> h.id().equals("d1")));
    }
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=QdrantVectorStoreIT "-Dsurefire.excludedGroups="
```

Expected: 编译失败（`deleteByIds` 不存在）。

- [ ] **Step 3: 实现 deleteByIds**

`QdrantVectorStore` 追加：

```java
    public void deleteByIds(String collection, List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        try {
            List<io.qdrant.client.grpc.Points.PointId> ids = pointIds.stream()
                    .map(id -> PointIdFactory.id(UUID.nameUUIDFromBytes(
                            id.getBytes(StandardCharsets.UTF_8))))
                    .toList();
            client.deleteAsync(collection, ids).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant delete 失败: " + e.getMessage(), e);
        }
    }
```

补充 import：`io.qdrant.client.grpc.Points.PointId`。

- [ ] **Step 4: 维护任务同步删向量**

`MemoryMaintenanceJob` 注入 `QdrantVectorStore vectorStore`；`expireCandidates` 删除前收集 id：

```java
    private int expireCandidates() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(properties.getMaintenance().getCandidateRetentionDays());
        var candidates = experienceMapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getConfirmed, 0)
                .lt(MemoryExperience::getCreatedAt, cutoff));
        for (MemoryExperience candidate : candidates) {
            experienceMapper.deleteById(candidate.getId());
        }
        vectorStore.deleteByIds(vectorStore.collectionName(QdrantVectorStore.COLLECTION_EXPERIENCES),
                candidates.stream().map(c -> "experience:" + c.getId()).toList());
        return candidates.size();
    }
```

新增 `archiveArchivedFactVectors`：清理已归档事实（`valid_to != OPEN_END`）的旧向量：

```java
    private int archiveArchivedFactVectors() {
        var archived = factMapper.selectList(new LambdaQueryWrapper<MemoryFact>()
                .ne(MemoryFact::getValidTo, FactMemoryService.OPEN_END));
        vectorStore.deleteByIds(vectorStore.collectionName(QdrantVectorStore.COLLECTION_FACTS),
                archived.stream().map(f -> "fact:" + f.getId()).toList());
        return archived.size();
    }
```

`maintain()` 中调用并计入日志；注入 `MemoryFactMapper factMapper`。

- [ ] **Step 5: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=QdrantVectorStoreIT "-Dsurefire.excludedGroups="
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add -A backed/AI_Study_Notes/src/main/java backed/AI_Study_Notes/src/test/java
git commit -m "feat(memory): Qdrant 遗忘（候选过期/事实归档删向量）"
```

---

### Task 2: Neo4j 关系衰减/归档 + 确认支撑方法

**Files:**
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph/Neo4jGraphRepository.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/MemoryMaintenanceJob.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph/Neo4jGraphRepositoryIT.java`

- [ ] **Step 1: 写失败测试**

`Neo4jGraphRepositoryIT` 追加：

```java
    @Test
    void listConfirmDeleteAndDecayRelations() {
        String shopping = repo.upsertEntity("订单模块", "Module", 1L, List.of());
        String payment = repo.upsertEntity("支付模块", "Module", 1L, List.of());
        repo.upsertRelation(shopping, RelationType.DEPENDS_ON, payment, "下单依赖支付", "测试", 1L);

        var pending = repo.listUnconfirmedRelations(1L, 5);
        assertTrue(pending.stream().anyMatch(h -> h.subjectName().equals("订单模块")));

        repo.confirmRelation(shopping, RelationType.DEPENDS_ON, payment, 1L);
        assertTrue(repo.expand(shopping, 1L, 1).stream()
                .anyMatch(h -> h.objectName().equals("支付模块")));

        repo.deleteRelation(shopping, RelationType.DEPENDS_ON, payment, 1L);
        assertTrue(repo.expand(shopping, 1L, 1).stream()
                .noneMatch(h -> h.objectName().equals("支付模块")));
    }
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=Neo4jGraphRepositoryIT "-Dsurefire.excludedGroups="
```

Expected: 编译失败（方法不存在）。

- [ ] **Step 3: 实现仓储方法**

```java
    public List<RelationHit> listUnconfirmedRelations(Long workspaceId, int limit) {
        try (var session = driver.session()) {
            var result = session.run("""
                    MATCH (a:Entity {workspaceId: $workspaceId})-[r]->(b:Entity {workspaceId: $workspaceId})
                    WHERE r.confirmed = false AND r.validTo IS NULL
                    RETURN a.entityId AS subjectId, a.name AS subjectName,
                           b.entityId AS objectId, b.name AS objectName,
                           type(r) AS predicate, r.context AS context
                    LIMIT $limit
                    """, Map.of("workspaceId", workspaceId, "limit", limit));
            return result.list(record -> new RelationHit(
                    record.get("subjectId").asString(),
                    record.get("subjectName").asString(),
                    record.get("objectId").asString(),
                    record.get("objectName").asString(),
                    RelationType.valueOf(record.get("predicate").asString()),
                    record.get("context").isNull() ? "" : record.get("context").asString()));
        }
    }

    public void deleteRelation(String subjectId, RelationType predicate, String objectId,
                               Long workspaceId) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH (a:Entity {entityId: $subjectId, workspaceId: $workspaceId})
                          -[r:%s]->(b:Entity {entityId: $objectId, workspaceId: $workspaceId})
                    DELETE r
                    """.formatted(predicate.name()), Map.of(
                    "subjectId", subjectId, "objectId", objectId, "workspaceId", workspaceId));
        }
    }

    public void updateRelationContext(String subjectId, RelationType predicate, String objectId,
                                     String context, Long workspaceId) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH (a:Entity {entityId: $subjectId, workspaceId: $workspaceId})
                          -[r:%s]->(b:Entity {entityId: $objectId, workspaceId: $workspaceId})
                    SET r.context = $context, r.confirmed = true
                    """.formatted(predicate.name()), Map.of(
                    "subjectId", subjectId, "objectId", objectId,
                    "workspaceId", workspaceId, "context", context));
        }
    }

    public void decayUnconfirmedRelations(int ageDays, double decayFactor, double archiveThreshold) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH ()-[r]->()
                    WHERE r.confirmed = false AND r.validTo IS NULL
                      AND r.createdAt < datetime() - duration({days: $ageDays})
                    SET r.weight = coalesce(r.weight, 1.0) * $decayFactor
                    """, Map.of("ageDays", ageDays, "decayFactor", decayFactor));
            session.run("""
                    MATCH ()-[r]->()
                    WHERE r.confirmed = false AND r.validTo IS NULL
                      AND coalesce(r.weight, 1.0) < $threshold
                    SET r.validTo = datetime()
                    """, Map.of("threshold", archiveThreshold));
        }
    }

    public void confirmUnconfirmedRelationsOlderThan(int hours) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH ()-[r]->()
                    WHERE r.confirmed = false AND r.validTo IS NULL
                      AND r.createdAt < datetime() - duration({hours: $hours})
                    SET r.confirmed = true
                    """, Map.of("hours", hours));
        }
    }
```

- [ ] **Step 4: 维护任务接入关系衰减**

`MemoryMaintenanceJob` 注入 `Neo4jGraphRepository graphRepository`；`maintain()` 中调用：

```java
            graphRepository.decayUnconfirmedRelations(30, 0.9, 0.2);
            graphRepository.confirmUnconfirmedRelationsOlderThan(
                    properties.getConfirm().getWindowHours());
```

（`getConfirm()` 在 Task 3 添加，本 Task 先用 `agent.confirm.window-hours` 的默认常量 24；Task 3 完成后统一改为配置引用。）

- [ ] **Step 5: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=Neo4jGraphRepositoryIT "-Dsurefire.excludedGroups="
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add -A backed/AI_Study_Notes/src/main/java backed/AI_Study_Notes/src/test/java
git commit -m "feat(memory): Neo4j 关系衰减/归档与确认支撑方法"
```

---

### Task 3: 会话式批量确认（完成点触发 + 超时自动确认）

**Files:**
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/config/AgentProperties.java`
- Modify: `backed/AI_Study_Notes/src/main/resources/application-dev.yml`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/confirm/PendingConfirmationService.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/confirm/CompletionDetector.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/confirm/ConfirmationAnswerProcessor.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/core/AgentLoop.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/MemoryMaintenanceJob.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/confirm/CompletionDetectorTest.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/confirm/ConfirmationAnswerProcessorTest.java`

- [ ] **Step 1: 写失败测试**

`CompletionDetectorTest`：

```java
package org.example.ai_study_notes.agent.memory.confirm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionDetectorTest {

    private final CompletionDetector detector = new CompletionDetector();

    @Test
    void detectsCompletionPhrases() {
        assertTrue(detector.isCompletionSignal("这个需求分析完成了"));
        assertTrue(detector.isCompletionSignal("先这样吧"));
        assertFalse(detector.isCompletionSignal("继续分析购物模块"));
    }
}
```

`ConfirmationAnswerProcessorTest`：

```java
package org.example.ai_study_notes.agent.memory.confirm;

import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConfirmationAnswerProcessorTest {

    @Test
    void positiveAnswerConfirmsFirstPending() {
        PendingConfirmationService pending = mock(PendingConfirmationService.class);
        ConfirmationAnswerProcessor processor = new ConfirmationAnswerProcessor(pending);

        int handled = processor.process(1L, "对");
        verify(pending).confirmNextExperience(1L);
    }

    @Test
    void negativeAnswerWithoutCorrectionRejectsFirstPending() {
        PendingConfirmationService pending = mock(PendingConfirmationService.class);
        ConfirmationAnswerProcessor processor = new ConfirmationAnswerProcessor(pending);

        processor.process(1L, "不对");
        verify(pending).rejectNextExperience(1L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=CompletionDetectorTest,ConfirmationAnswerProcessorTest"
```

Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现配置**

`AgentProperties` 追加：

```java
    private Confirm confirm = new Confirm();
```

```java
    @Data
    public static class Confirm {
        private boolean enabled = true;
        private int windowHours = 24;
        private int askLimit = 5;
    }
```

yml：

```yaml
  confirm:
    enabled: ${CONFIRM_ENABLED:true}
    window-hours: 24
    ask-limit: 5
```

- [ ] **Step 4: 实现三个组件**

`CompletionDetector`：

```java
package org.example.ai_study_notes.agent.memory.confirm;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompletionDetector {

    private static final List<String> SIGNALS = List.of(
            "完成了", "搞定了", "好了", "就这样", "先这样", "可以了", "结束了", "做完");

    public boolean isCompletionSignal(String userText) {
        if (userText == null || userText.isBlank()) {
            return false;
        }
        return SIGNALS.stream().anyMatch(userText::contains);
    }
}
```

`PendingConfirmationService`：

```java
package org.example.ai_study_notes.agent.memory.confirm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.example.ai_study_notes.agent.memory.graph.Neo4jGraphRepository;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PendingConfirmationService {

    public record PendingExperience(Long id, String taskType, String rule) {
    }

    private final MemoryExperienceMapper experienceMapper;
    private final ExperienceMemoryService experienceService;
    private final Neo4jGraphRepository graphRepository;
    private final MemoryIndexer indexer;
    private final QdrantVectorStore vectorStore;
    private final AgentProperties properties;

    public PendingConfirmationService(MemoryExperienceMapper experienceMapper,
                                      ExperienceMemoryService experienceService,
                                      Neo4jGraphRepository graphRepository,
                                      MemoryIndexer indexer,
                                      QdrantVectorStore vectorStore,
                                      AgentProperties properties) {
        this.experienceMapper = experienceMapper;
        this.experienceService = experienceService;
        this.graphRepository = graphRepository;
        this.indexer = indexer;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public String pendingPrompt(Long userId, int limit) {
        List<String> lines = new ArrayList<>();
        int n = 0;
        var experiences = experienceMapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getUserId, userId)
                .eq(MemoryExperience::getConfirmed, 0)
                .orderByAsc(MemoryExperience::getCreatedAt)
                .last("limit " + Math.max(1, limit)));
        for (MemoryExperience exp : experiences) {
            lines.add(++n + ") 经验【" + exp.getTaskType() + "】" + exp.getRuleText());
        }
        for (Neo4jGraphRepository.RelationHit hit :
                graphRepository.listUnconfirmedRelations(userId, Math.max(1, limit - n))) {
            lines.add(++n + ") 关系【" + hit.subjectName() + " " + hit.predicate() + " "
                    + hit.objectName() + "】" + hit.context());
        }
        if (lines.isEmpty()) {
            return "";
        }
        return "用户已完成当前工作。请在回复末尾逐条向用户确认以下待确认记忆（用户可回答“对 / 不对，改成X”）：\n"
                + String.join("\n", lines);
    }

    public void confirmNextExperience(Long userId) {
        MemoryExperience exp = firstPending(userId);
        if (exp != null) {
            experienceService.confirm(exp.getId());
            exp.setConfirmed(1);
            indexer.indexExperience(exp);
        }
    }

    public void rejectNextExperience(Long userId) {
        MemoryExperience exp = firstPending(userId);
        if (exp != null) {
            experienceMapper.deleteById(exp.getId());
            vectorStore.deleteByIds(vectorStore.collectionName(QdrantVectorStore.COLLECTION_EXPERIENCES),
                    List.of("experience:" + exp.getId()));
        }
    }

    public void correctNextExperience(Long userId, String correctedRule) {
        MemoryExperience exp = firstPending(userId);
        if (exp != null && correctedRule != null && !correctedRule.isBlank()) {
            MemoryExperience update = new MemoryExperience();
            update.setId(exp.getId());
            update.setRuleText(correctedRule);
            update.setConfirmed(1);
            experienceMapper.updateById(update);
            exp.setRuleText(correctedRule);
            exp.setConfirmed(1);
            indexer.indexExperience(exp);
        }
    }

    private MemoryExperience firstPending(Long userId) {
        return experienceMapper.selectOne(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getUserId, userId)
                .eq(MemoryExperience::getConfirmed, 0)
                .orderByAsc(MemoryExperience::getCreatedAt)
                .last("limit 1"));
    }
}
```

`ConfirmationAnswerProcessor`：

```java
package org.example.ai_study_notes.agent.memory.confirm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ConfirmationAnswerProcessor {

    private static final Pattern CORRECT_PATTERN =
            Pattern.compile("(?:改成|应该是|其实是|改为)[：:\\s]*(.+)");

    private final PendingConfirmationService pending;

    public ConfirmationAnswerProcessor(PendingConfirmationService pending) {
        this.pending = pending;
    }

    public int process(Long userId, String answer) {
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        if (answer.contains("不对") || answer.contains("错了") || answer.contains("不是")) {
            Matcher matcher = CORRECT_PATTERN.matcher(answer);
            if (matcher.find()) {
                pending.correctNextExperience(userId, matcher.group(1).trim());
            } else {
                pending.rejectNextExperience(userId);
            }
            return 1;
        }
        if (answer.contains("对") || answer.contains("是的") || answer.contains("正确")
                || answer.contains("可以") || answer.contains("没问题")) {
            pending.confirmNextExperience(userId);
            return 1;
        }
        return 0;
    }
}
```

注意：关系维度的确认/纠正由 `PendingConfirmationService` 扩展方法（Task 3 Step 5）补齐后，处理器同步调用。

- [ ] **Step 5: 扩展 PendingConfirmationService 支持关系确认/纠正**

追加方法（签名与仓储 Task 2 一致）：

```java
    public void confirmNextRelation(Long userId) {
        var pending = graphRepository.listUnconfirmedRelations(userId, 1);
        if (!pending.isEmpty()) {
            var hit = pending.get(0);
            graphRepository.confirmRelation(hit.subjectEntityId(), hit.predicate(),
                    hit.objectEntityId(), userId);
        }
    }

    public void rejectNextRelation(Long userId) {
        var pending = graphRepository.listUnconfirmedRelations(userId, 1);
        if (!pending.isEmpty()) {
            var hit = pending.get(0);
            graphRepository.deleteRelation(hit.subjectEntityId(), hit.predicate(),
                    hit.objectEntityId(), userId);
        }
    }

    public void correctNextRelation(Long userId, String correctedText) {
        var pending = graphRepository.listUnconfirmedRelations(userId, 1);
        if (!pending.isEmpty() && correctedText != null && !correctedText.isBlank()) {
            var hit = pending.get(0);
            graphRepository.updateRelationContext(hit.subjectEntityId(), hit.predicate(),
                    hit.objectEntityId(), correctedText, userId);
        }
    }
```

`ConfirmationAnswerProcessor` 的 `correctNextExperience` 调用处改为先经验后关系（按 pendingPrompt 顺序：先经验后关系），统一走 `pending` 的 next 方法。

- [ ] **Step 6: AgentLoop 集成（完成信号轮追加确认指令 + 回答处理）**

字段追加：`CompletionDetector completionDetector`、`ConfirmationAnswerProcessor confirmationAnswerProcessor`、`PendingConfirmationService pendingConfirmationService`（构造函数同步）。

`run()` 中、`agentExecutor` 执行前（用户消息已落库后），追加：

```java
            if (userText != null) {
                confirmationAnswerProcessor.process(userId, userText);
            }
```

`loop()` 组装 system 消息处，`buildWithMemory(...)` 之后：

```java
            String systemContent = systemPromptBuilder.buildWithMemory(memorySection, skillBodies, taskPlan);
            if (userId != null && completionSignal) {
                String pendingPrompt = pendingConfirmationService.pendingPrompt(
                        userId, properties.getConfirm().getAskLimit());
                if (!pendingPrompt.isBlank()) {
                    systemContent += "\n\n" + pendingPrompt;
                }
            }
```

`loop()` 签名增加 `boolean completionSignal`；`run()` 调用处传 `userText != null && completionDetector.isCompletionSignal(userText)`。

- [ ] **Step 7: 维护任务超时自动确认**

`MemoryMaintenanceJob` 注入 `PendingConfirmationService`；`maintain()` 中追加：

```java
            graphRepository.confirmUnconfirmedRelationsOlderThan(
                    properties.getConfirm().getWindowHours());
            autoConfirmExpiredExperiences();
```

```java
    private int autoConfirmExpiredExperiences() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(properties.getConfirm().getWindowHours());
        var candidates = experienceMapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getConfirmed, 0)
                .lt(MemoryExperience::getCreatedAt, cutoff));
        for (MemoryExperience candidate : candidates) {
            experienceService.confirm(candidate.getId());
            candidate.setConfirmed(1);
            indexer.indexExperience(candidate);
        }
        return candidates.size();
    }
```

（注入 `MemoryIndexer indexer`。）

- [ ] **Step 8: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=CompletionDetectorTest,ConfirmationAnswerProcessorTest,AgentPropertiesTest,MemoryDistillerTest"
```

Expected: 全部 PASS。

- [ ] **Step 9: 提交**

```bash
git add -A backed/AI_Study_Notes/src/main/java backed/AI_Study_Notes/src/main/resources backed/AI_Study_Notes/src/test/java
git commit -m "feat(memory): 会话式批量确认（完成点触发 + 超时自动确认）"
```

---

### Task 4: 前端记忆面板移除 + agent_memory 旧链路清理

**Files:**
- Modify: `AutoTest_fronted/src/views/Agent.vue`
- Modify: `AutoTest_fronted/src/components/agent/ResourcePanel.vue`
- Delete: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/api/MemoryController.java`
- Delete: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/MemoryService.java`
- Delete: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/MemoryMapper.java`
- Delete: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/MemoryEntry.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/distill/MemoryDistiller.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/distill/MemoryDistillerTest.java`
- Modify: `backed/init.sql`

- [ ] **Step 1: 确认引用面（删除前排查）**

```powershell
rg -n "MemoryService|MemoryMapper|MemoryEntry|MemoryController|/agent/memory" backed\AI_Study_Notes\src\main\java AutoTest_fronted\src | Select-Object -First 30
```

确认除 Agent.vue/ResourcePanel.vue 与 MemoryDistiller 外无其它引用。

- [ ] **Step 2: 前端移除记忆分组**

`ResourcePanel.vue`：删除"记忆"分组块（`.rp-group-title` 记忆 + 记忆列表 + `confirm-memory` 按钮）与 `emits` 中的 `'confirm-memory'`。

`Agent.vue`：
- `loadResources` 中 `Promise.all` 去掉 `axios.get('/agent/memory')` 与 `memories.value = ...`
- 删除 `memories` 响应式声明与 `confirmMemory` 函数
- 模板中传给 `ResourcePanel` 的 `memories` prop 与 `@confirm-memory` 事件删除
- `openStream` 中如引用 memories 重置逻辑一并删除

- [ ] **Step 3: 后端删除旧链路**

删除 `MemoryController / MemoryService / MemoryMapper / MemoryEntry` 四个文件。

`MemoryDistiller` 的 preferences 分支改为写 `memory_fact`：

```java
                factService.upsert(userId, userId, "user", key, content,
                        "conversation", String.valueOf(conversationId), 0.9);
```

并删除 `MemoryService` 依赖（构造函数参数、字段、赋值）。

`MemoryDistillerTest` 的偏好断言改为：

```java
        verify(factService).upsert(eq(1L), eq(1L), eq("user"), eq("default_env"),
                eq("默认环境是 staging"), anyString(), anyString(), anyDouble());
```

`backed/init.sql` 删除 `agent_memory` 建表段。

- [ ] **Step 4: 删除 dev 库表并验证编译**

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p123456 -e "USE app_test; DROP TABLE IF EXISTS agent_memory;"
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=MemoryDistillerTest,AgentPropertiesTest"
```

Expected: PASS；`rg "MemoryService" backed` 无结果。

- [ ] **Step 5: 前端构建验证**

```powershell
cd AutoTest_fronted
npm.cmd run build
```

Expected: 构建成功（无 memory 引用报错）。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "refactor(memory): 移除前端记忆面板与 agent_memory 旧链路（偏好迁入 memory_fact）"
```

---

### Task 5: 全量验证 + 端到端冒烟

**Files:**
- 无新文件；只做收尾验证

- [ ] **Step 1: 全量单元 + 集成 + 打包**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dsurefire.excludedGroups="
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml -DskipTests package
```

Expected: 全部 BUILD SUCCESS（需 MySQL + Redis + Qdrant + Neo4j）。

- [ ] **Step 2: 端到端冒烟（可选）**

启动后端（带 key），验证：
1. 发消息 → 提炼经验候选 → 回复"完成了" → agent 回复末尾出现确认提问
2. 回复"对" → 候选 confirmed=1；`/api/agent/memory/retrieve?query=提取测试用例` 命中该经验
3. 新会话产生候选后不回复 → 等窗口（可临时把 window-hours 调小验证）→ 自动确认
4. 前端资源面板不再出现"记忆"分组

- [ ] **Step 3: 收尾提交**

```bash
git status --short
git add -A
git commit -m "chore(memory): 会话式确认全量验证"
```

---

## 自检记录

- **Spec 覆盖**：设计文档 §10（Qdrant/Neo4j 遗忘）、§7.1（评审反馈直接确认）、用户新增的"完成点批量确认 + 超时自动确认"语义由 Task 1-5 覆盖；任务计划完成信号与 LLM 答案解析为明确留口。
- **占位符**：无 TBD/TODO；代码步骤完整。
- **类型一致性**：`PendingConfirmationService` 的 confirm/reject/correct 方法与 `ConfirmationAnswerProcessor` 调用一致；`Neo4jGraphRepository` 新方法与 IT/Service 一致；`MemoryDistiller` 偏好改道后测试断言同步。
- **分层/DRY**：确认服务只做组装；处理器只做解析；维护任务只做调度；前端只删不改结构。
