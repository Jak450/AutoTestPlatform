# 记忆层 Neo4j 关系图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地业务关系记忆——Neo4j 图存储（实体/关系）、LLM 关系抽取（需求文档/对话 → 三元组）、1-2 跳展开注入，让 agent 分析实体（如"购物模块"）时能看到影响/依赖链。

**Architecture:** 新增 `memory.graph` 子包：`Neo4jGraphRepository`（实体 MERGE、关系 MERGE（confirmed=false）、按 workspaceId 过滤的 1-2 跳展开）；`RelationExtractor`（LLM 输出受控词表三元组 → 实体消歧 → 落图）；`GraphRetriever`（查询命中实体名 → 展开 → 格式化），由 `MemoryRetriever.inject` 组装进新增的 relations 注入段；示例工具 `extract_business_relations` 用 `@AgentTool` 注解暴露。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / org.neo4j.driver:neo4j-java-driver 5.26.0 / Docker（neo4j:5-community）/ Jackson / Mockito / JUnit 5

**前置：** Docker 可用、Qdrant 容器运行中（本计划不依赖 Qdrant，但环境保持一致）；DeepSeek flash + 硅基流动 embedding key 已配置。

**分层与去重约束（沿用）：** Repository 只做 Cypher；Extractor 只做编排（LLM + 消歧 + 落图）；Retriever 只做检索组装；谓词收敛为受控词表，不收口会乱；`@AgentTool` 注解工具复用既有注册管线。

---

### Task 1: Neo4j 环境 + 配置 + 驱动

**Files:**
- Modify: `backed/AI_Study_Notes/pom.xml`（追加 neo4j-java-driver）
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/config/AgentProperties.java`
- Modify: `backed/AI_Study_Notes/src/main/resources/application-dev.yml`
- Modify: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/config/AgentPropertiesTest.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph/Neo4jConnectionIT.java`

- [ ] **Step 1: 启动 Neo4j 容器**

```powershell
docker run -d --name neo4j -p 7474:7474 -p 7687:7687 `
  -e NEO4J_AUTH=neo4j/autotest123456 `
  -e NEO4J_server_memory_heap_max__size=1G `
  -v D:/桌面/AutoTestPlatform/deploy/neo4j-data:/data `
  neo4j:5-community
Start-Sleep -Seconds 15
curl.exe -s http://127.0.0.1:7474
```

Expected: HTTP 200（Neo4j Browser 页面）。Bolt 端口 7687 用于驱动。

- [ ] **Step 2: 写失败测试（配置绑定 + 连接探测）**

`AgentPropertiesTest` 的 `withPropertyValues` 追加 `"agent.neo4j.uri=bolt://127.0.0.1:7687"`、`"agent.neo4j.user=neo4j"`、`"agent.neo4j.password=autotest123456"`，断言 `props.getNeo4j()` 各字段。

创建 `Neo4jConnectionIT.java`：

```java
package org.example.ai_study_notes.agent.memory.graph;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class Neo4jConnectionIT {

    @Test
    void driverConnectsAndReturnsServerVersion() {
        Assumptions.assumeTrue(isNeo4jUp(), "Neo4j 未运行，跳过");
        try (org.neo4j.driver.Driver driver = Neo4jGraphRepository.createDriver(
                "bolt://127.0.0.1:7687", "neo4j", "autotest123456")) {
            String version = driver.verifyConnectivity().getServer().version();
            assertTrue(version.startsWith("5."));
        }
    }

    private boolean isNeo4jUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 7687), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=Neo4jConnectionIT "-Dsurefire.excludedGroups="
```

Expected: 编译失败（`Neo4jGraphRepository.createDriver` 不存在 / `AgentProperties.getNeo4j` 不存在）。

- [ ] **Step 4: 实现配置与驱动工厂**

`AgentProperties` 追加 `private Neo4j neo4j = new Neo4j();` 与：

```java
    @Data
    public static class Neo4j {
        private String uri = "bolt://127.0.0.1:7687";
        private String user = "neo4j";
        private String password = "autotest123456";
    }
```

`application-dev.yml` 的 `agent:` 下追加：

```yaml
  neo4j:
    uri: ${NEO4J_URI:bolt://127.0.0.1:7687}
    user: ${NEO4J_USER:neo4j}
    password: ${NEO4J_PASSWORD:autotest123456}
```

pom `<dependencies>` 追加：

```xml
        <!-- Neo4j 关系图 -->
        <dependency>
            <groupId>org.neo4j.driver</groupId>
            <artifactId>neo4j-java-driver</artifactId>
            <version>5.26.0</version>
        </dependency>
```

创建 `Neo4jGraphRepository.java` 骨架（本 Task 只提供静态工厂，Task 2 补全）：

```java
package org.example.ai_study_notes.agent.memory.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class Neo4jGraphRepository {

    public static Driver createDriver(String uri, String user, String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=Neo4jConnectionIT,AgentPropertiesTest" "-Dsurefire.excludedGroups="
```

Expected: 全部 PASS。

- [ ] **Step 6: 提交**

```bash
git add backed/AI_Study_Notes/pom.xml backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/config backed/AI_Study_Notes/src/main/resources/application-dev.yml backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/config backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph
git commit -m "feat(graph): Neo4j 环境配置与驱动"
```

---

### Task 2: Neo4jGraphRepository（实体/关系 upsert + 1-2 跳展开）

**Files:**
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph/Neo4jGraphRepository.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph/RelationType.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph/Neo4jGraphRepositoryIT.java`

- [ ] **Step 1: 写失败测试**

创建 `RelationType.java`：

```java
package org.example.ai_study_notes.agent.memory.graph;

import java.util.Set;

public enum RelationType {
    DEPENDS_ON, AFFECTS, CONTAINED_IN, ASSOCIATED_WITH, BELONGS_TO, REFERENCES;

    public static final Set<String> NAMES = Set.of(
            DEPENDS_ON.name(), AFFECTS.name(), CONTAINED_IN.name(),
            ASSOCIATED_WITH.name(), BELONGS_TO.name(), REFERENCES.name());

    public static boolean isValid(String name) {
        return name != null && NAMES.contains(name);
    }
}
```

创建 `Neo4jGraphRepositoryIT.java`：

```java
package org.example.ai_study_notes.agent.memory.graph;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class Neo4jGraphRepositoryIT {

    private static Neo4jGraphRepository repo;
    private static Driver driver;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(isNeo4jUp(), "Neo4j 未运行，跳过");
        driver = Neo4jGraphRepository.createDriver("bolt://127.0.0.1:7687", "neo4j", "autotest123456");
        repo = new Neo4jGraphRepository(driver);
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    void upsertEntitiesAndRelationThenExpandOneHop() {
        String shopping = repo.upsertEntity("购物模块", "Module", 1L, List.of("商城购物"));
        String inventory = repo.upsertEntity("库存模块", "Module", 1L, List.of());
        repo.upsertRelation(shopping, RelationType.AFFECTS, inventory, "下单时校验库存", "需求文档#12", 1L);

        List<Neo4jGraphRepository.RelationHit> hits =
                repo.expand(shopping, 1L, 1);
        assertTrue(hits.stream().anyMatch(h -> h.objectEntityId().equals(inventory)));
        assertEquals(RelationType.AFFECTS, hits.get(0).predicate());
    }

    private static boolean isNeo4jUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 7687), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=Neo4jGraphRepositoryIT "-Dsurefire.excludedGroups="
```

Expected: 编译失败（`upsertEntity` 等方法不存在）。

- [ ] **Step 3: 实现图仓储**

`Neo4jGraphRepository.java` 补全（保留 `createDriver`）：

```java
    public record RelationHit(String subjectEntityId, String subjectName,
                              String objectEntityId, String objectName,
                              RelationType predicate, String context) {
    }

    private final Driver driver;

    public Neo4jGraphRepository(Driver driver) {
        this.driver = driver;
    }

    public String upsertEntity(String name, String type, Long workspaceId, List<String> aliases) {
        String entityId = java.util.UUID.randomUUID().toString();
        try (var session = driver.session()) {
            session.run("""
                    MERGE (e:Entity {entityId: $entityId, workspaceId: $workspaceId})
                    SET e.name = $name, e.type = $type, e.aliases = $aliases,
                        e.scope = 'TEAM', e.createdAt = datetime()
                    """, Map.of(
                    "entityId", entityId,
                    "workspaceId", workspaceId,
                    "name", name,
                    "type", type,
                    "aliases", aliases));
        }
        return entityId;
    }

    public void upsertRelation(String subjectId, RelationType predicate, String objectId,
                               String context, String source, Long workspaceId) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH (a:Entity {entityId: $subjectId, workspaceId: $workspaceId})
                    MATCH (b:Entity {entityId: $objectId, workspaceId: $workspaceId})
                    MERGE (a)-[r:%s]->(b)
                    SET r.context = $context, r.source = $source, r.confirmed = false,
                        r.weight = coalesce(r.weight, 1.0), r.validTo = null,
                        r.workspaceId = $workspaceId, r.createdAt = datetime()
                    """.formatted(predicate.name()), Map.of(
                    "subjectId", subjectId,
                    "objectId", objectId,
                    "workspaceId", workspaceId,
                    "context", context,
                    "source", source));
        }
    }

    public String resolveEntity(String name, Long workspaceId) {
        try (var session = driver.session()) {
            var result = session.run("""
                    MATCH (e:Entity {workspaceId: $workspaceId})
                    WHERE e.name = $name OR any(a IN e.aliases WHERE a = $name)
                    RETURN e.entityId AS entityId LIMIT 1
                    """, Map.of("name", name, "workspaceId", workspaceId));
            return result.hasNext() ? result.next().get("entityId").asString() : null;
        }
    }

    public List<RelationHit> expand(String entityId, Long workspaceId, int depth) {
        List<RelationHit> hits = expandOnce(entityId, workspaceId);
        if (depth >= 2) {
            int base = hits.size();
            for (int i = 0; i < base; i++) {
                hits.addAll(expandOnce(hits.get(i).objectEntityId(), workspaceId));
            }
        }
        return hits.stream().distinct().toList();
    }

    private List<RelationHit> expandOnce(String entityId, Long workspaceId) {
        try (var session = driver.session()) {
            var result = session.run("""
                    MATCH (a:Entity {entityId: $entityId, workspaceId: $workspaceId})
                          -[r]->(b:Entity {workspaceId: $workspaceId})
                    WHERE r.confirmed = true AND r.validTo IS NULL
                    RETURN a.name AS subjectName, b.entityId AS objectId, b.name AS objectName,
                           type(r) AS predicate, r.context AS context
                    LIMIT 20
                    """, Map.of("entityId", entityId, "workspaceId", workspaceId));
            return result.list(record -> new RelationHit(
                    entityId,
                    record.get("subjectName").asString(),
                    record.get("objectId").asString(),
                    record.get("objectName").asString(),
                    RelationType.valueOf(record.get("predicate").asString()),
                    record.get("context").isNull() ? "" : record.get("context").asString()));
        }
    }
```

注意：`distinct()` 需要 `RelationHit` 为 record（已自动生成 equals/hashCode）。

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=Neo4jGraphRepositoryIT "-Dsurefire.excludedGroups="
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph
git commit -m "feat(graph): Neo4j 实体/关系 upsert 与 1-2 跳展开"
```

---

### Task 3: RelationExtractor（LLM 三元组抽取）+ 示例注解工具

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph/RelationExtractor.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/tools/annotated/RelationToolGroup.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph/RelationExtractorTest.java`

- [ ] **Step 1: 写失败测试**

创建 `RelationExtractorTest.java`：

```java
package org.example.ai_study_notes.agent.memory.graph;

import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationExtractorTest {

    @Test
    void parsesTriplesAndCallsRepository() {
        AgentAiClient aiClient = mock(AgentAiClient.class);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("{\"relations\":[{\"subject\":\"购物模块\",\"predicate\":\"AFFECTS\","
                        + "\"object\":\"库存模块\",\"context\":\"下单时校验库存\"}]}");
        Neo4jGraphRepository repo = mock(Neo4jGraphRepository.class);
        when(repo.resolveEntity("购物模块", 1L)).thenReturn("s1");
        when(repo.resolveEntity("库存模块", 1L)).thenReturn("o1");

        RelationExtractor extractor = new RelationExtractor(aiClient, repo);
        int saved = extractor.extract("购物模块影响库存模块，因为下单时要校验库存", 1L);

        assertEquals(1, saved);
        org.mockito.Mockito.verify(repo).upsertRelation(
                "s1", RelationType.AFFECTS, "o1", "下单时校验库存", "对话", 1L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=RelationExtractorTest
```

Expected: 编译失败（`RelationExtractor` 不存在）。

- [ ] **Step 3: 实现 RelationExtractor**

```java
package org.example.ai_study_notes.agent.memory.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RelationExtractor {

    private static final String PROMPT = """
            你是业务关系抽取器。从文本中抽取实体之间的业务关系，只输出 JSON：
            {"relations":[{"subject":"...","predicate":"AFFECTS","object":"...","context":"一句话原因"}]}
            谓词只能取：DEPENDS_ON(依赖) / AFFECTS(影响) / CONTAINED_IN(包含) /
            ASSOCIATED_WITH(关联) / BELONGS_TO(归属) / REFERENCES(引用)。
            主语/宾语必须是业务实体（模块/需求/业务对象/文档），宁缺毋滥，不虚构。
            没有关系输出 {"relations":[]}，不要输出其他文字。
            """;

    private final AgentAiClient aiClient;
    private final Neo4jGraphRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RelationExtractor(AgentAiClient aiClient, Neo4jGraphRepository repository) {
        this.aiClient = aiClient;
        this.repository = repository;
    }

    public int extract(String text, Long workspaceId) {
        try {
            Map<String, Object> root = parse(aiClient.chat(PROMPT, text));
            List<?> relations = root.get("relations") instanceof List<?> list ? list : List.of();
            int saved = 0;
            for (Object item : relations) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String subject = str(map.get("subject"));
                String object = str(map.get("object"));
                String predicate = str(map.get("predicate"));
                String context = str(map.get("context"));
                if (subject.isBlank() || object.isBlank() || !RelationType.isValid(predicate)) {
                    continue;
                }
                String subjectId = repository.resolveEntity(subject, workspaceId);
                if (subjectId == null) {
                    subjectId = repository.upsertEntity(subject, "Concept", workspaceId, List.of());
                }
                String objectId = repository.resolveEntity(object, workspaceId);
                if (objectId == null) {
                    objectId = repository.upsertEntity(object, "Concept", workspaceId, List.of());
                }
                repository.upsertRelation(subjectId, RelationType.valueOf(predicate),
                        objectId, context, "对话", workspaceId);
                saved++;
            }
            return saved;
        } catch (Exception e) {
            log.warn("关系抽取失败: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> parse(String raw) {
        String cleaned = raw.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("关系 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=RelationExtractorTest
```

Expected: PASS。

- [ ] **Step 5: 暴露为注解工具**

创建 `RelationToolGroup.java`：

```java
package org.example.ai_study_notes.agent.tool.tools.annotated;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.memory.graph.RelationExtractor;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.annotation.AgentTool;
import org.example.ai_study_notes.agent.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RelationToolGroup {

    private final RelationExtractor extractor;

    public RelationToolGroup(RelationExtractor extractor) {
        this.extractor = extractor;
    }

    @AgentTool(name = "extract_business_relations", label = "抽取业务关系",
               description = "从需求文档/对话文本中抽取业务实体之间的关系并入库（候选，需人工确认后生效）",
               permission = ToolPermission.CONFIRM_WRITE, category = "知识")
    public Map<String, Object> extract(
            @ToolParam(name = "content", description = "待抽取的业务文本") String content,
            ToolContext context) {
        int saved = extractor.extract(content, context.getUserId());
        return Map.of("saved", saved);
    }
}
```

- [ ] **Step 6: 编译验证（注解扫描器会注册该工具）**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AnnotationToolScannerIT "-Dsurefire.excludedGroups="
```

Expected: PASS（`extract_business_relations` 随扫描器注册，无需改 IT）。

- [ ] **Step 7: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/tools/annotated backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph
git commit -m "feat(graph): 关系抽取 RelationExtractor + extract_business_relations 工具"
```

---

### Task 4: 检索注入关系段（GraphRetriever + MemoryInjection.relations）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph/GraphRetriever.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/retrieval/MemoryRetriever.java`
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/core/AgentLoop.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph/GraphRetrieverTest.java`

- [ ] **Step 1: 写失败测试**

创建 `GraphRetrieverTest.java`：

```java
package org.example.ai_study_notes.agent.memory.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphRetrieverTest {

    @Test
    void expandsWhenQueryMentionsEntity() {
        Neo4jGraphRepository repo = mock(Neo4jGraphRepository.class);
        when(repo.resolveEntity("购物模块", 1L)).thenReturn("s1");
        when(repo.expand("s1", 1L, 2)).thenReturn(List.of(
                new Neo4jGraphRepository.RelationHit("s1", "购物模块", "o1", "库存模块",
                        RelationType.AFFECTS, "下单时校验库存")));
        GraphRetriever retriever = new GraphRetriever(repo);

        String out = retriever.expandForQuery("分析购物模块需求", 1L, 512);
        assertTrue(out.contains("购物模块 --AFFECTS--> 库存模块"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=GraphRetrieverTest
```

Expected: 编译失败（`GraphRetriever` 不存在）。

- [ ] **Step 3: 实现 GraphRetriever**

```java
package org.example.ai_study_notes.agent.memory.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GraphRetriever {

    private static final List<String> ENTITY_HINTS = List.of(
            "模块", "需求", "系统", "服务", "订单", "库存", "支付", "用户", "商品", "购物");

    private final Neo4jGraphRepository repository;

    public GraphRetriever(Neo4jGraphRepository repository) {
        this.repository = repository;
    }

    public String expandForQuery(String query, Long workspaceId, int budgetChars) {
        try {
            List<String> sections = new ArrayList<>();
            for (String candidate : candidateEntities(query)) {
                String entityId = repository.resolveEntity(candidate, workspaceId);
                if (entityId == null) {
                    continue;
                }
                for (Neo4jGraphRepository.RelationHit hit : repository.expand(entityId, workspaceId, 2)) {
                    sections.add(format(hit.subjectName(), hit.predicate(), hit.objectName(), hit.context()));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String section : sections) {
                if (sb.length() + section.length() + 1 > budgetChars) {
                    break;
                }
                sb.append(section).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("关系展开失败: {}", e.getMessage());
            return "";
        }
    }

    private List<String> candidateEntities(String query) {
        List<String> candidates = new ArrayList<>();
        for (String hint : ENTITY_HINTS) {
            if (query.contains(hint)) {
                candidates.add(hint);
            }
        }
        return candidates;
    }

    private String format(String subjectName, RelationType predicate, String objectName, String context) {
        return "关系: " + subjectName + " --" + predicate + "--> " + objectName
                + (context == null || context.isBlank() ? "" : "（" + context + "）");
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=GraphRetrieverTest
```

Expected: PASS。

- [ ] **Step 5: 接入 MemoryRetriever.inject 与 AgentLoop 格式化**

`MemoryRetriever.MemoryInjection` record 增加 `String relations` 字段；构造处传 `graphRetriever.expandForQuery(query, workspaceId, third)`；`MemoryRetriever` 构造函数增加 `GraphRetriever graphRetriever` 依赖。

`AgentLoop.formatInjection` 增加：

```java
        if (injection.relations() != null && !injection.relations().isBlank()) {
            sb.append("### 关系\n").append(injection.relations());
        }
```

同步更新 `MemoryRetrieverTest`（构造参数补 `mock(GraphRetriever.class)`）。

- [ ] **Step 6: 编译 + 测试**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=MemoryRetrieverTest,GraphRetrieverTest,SystemPromptBuilderTest"
```

Expected: 全部 PASS。

- [ ] **Step 7: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/graph backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/retrieval backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/core backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/graph backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/retrieval
git commit -m "feat(graph): 关系检索注入（GraphRetriever + MemoryInjection.relations）"
```

---

### Task 5: 全量验证

**Files:**
- 无新文件；只做收尾验证

- [ ] **Step 1: 全量单元测试**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 全量集成测试（需 MySQL + Qdrant + Neo4j + Redis）**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dsurefire.excludedGroups="
```

Expected: 全部 PASS（Redis 需启动，见执行记录）。

- [ ] **Step 3: 打包**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml -DskipTests package
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git status --short
git add -A
git commit -m "chore(graph): 关系图全量验证"
```

---

## 自检记录

- **Spec 覆盖**：设计文档 §6.4（图模型/受控词表/多租户）、§7.1（文档解析触发）、§8.2（关系路 1-2 跳）由 Task 1-5 覆盖；关系衰减/业务变更归档（§10）保留给子计划 4。
- **占位符**：无 TBD/TODO；`RelationHit` 从 Task 2 即携带 `subjectName`，`format` 在 Task 4 一次实现正确，无遗留占位。
- **类型一致性**：`RelationHit(subjectEntityId, subjectName, objectEntityId, objectName, predicate, context)` 在 Task 2/3/4 使用一致；`RelationType` 受控词表在 Extract/Repository/Test 间一致；`MemoryInjection` 新增 `relations` 字段在 Retriever/AgentLoop/测试间一致。
- **分层/DRY**：Repository 只管 Cypher；Extractor 只编排；Retriever 只组装；谓词枚举单一来源；注解工具复用既有注册管线。
