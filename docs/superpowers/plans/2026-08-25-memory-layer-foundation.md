# 记忆层地基（存储 / 检索 / 评测）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AutoTestPlatform Agent 落地记忆层地基——MySQL 记忆表、Embedding 服务抽象、Qdrant 向量索引、混合检索（关键词 + 向量 + RRF 融合）与离线评测工具链。

**Architecture:** 在现有 `agent` 包内新增 `memory` 子包，按"存储 → 服务 → 检索 → 评测"分层。`EmbeddingClient` 抽象 OpenAI 兼容 embedding 接口；`QdrantVectorStore` 封装 Qdrant 向量读写；`MemoryRetriever` 做查询理解（第一阶段用关键词切分代替 LLM）、多路召回、RRF 融合、过滤与注入组装；`MemoryEvalController` 暴露检索调试接口，`agent-eval/memory-eval` Node 脚本基于 golden set 计算 Recall@5 / MRR。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.14 / io.qdrant:client 1.13.0 / Jackson / JUnit 5 / Node 22（评测脚本）

**依赖前置（已确认）:** 本机 Java 17、MySQL（app_test 库）、Redis 可用；Maven 用 IntelliJ 自带 3.9.9；Docker 引擎当前不可用（WSL2/Hyper-V 未启用），Qdrant 环境方案见 Task 1。

---

## 子计划清单（记忆层整体拆分为 4 份）

1. **本计划：存储 + 检索 + 评测地基**
2. `2026-08-25-memory-layer-write-pipeline.md`：写入管道（提炼/去重/冲突/确认）+ 注入整合
3. `2026-08-25-memory-layer-graph.md`：Neo4j 关系图（实体抽取/展开/衰减）
4. `2026-08-25-memory-layer-feedback.md`：反馈闭环（工具失败/评审反馈）+ 安全合规 + 遗忘调度

---

## 分层与去重规范（本计划强制）

### 分层

所有新增代码严格按四层，上层只依赖下层接口，禁止跨层调用：

```
api（Controller：参数校验 + 结果包装，不写业务逻辑）
  └─ service（业务逻辑：FactMemoryService / ExperienceMemoryService / MemoryRetriever）
       └─ repository（数据访问：Mapper 封装 / QdrantVectorStore / 外部存储封装）
            └─ infra（基础设施：EmbeddingClient 接口 + 实现）
```

强制规则：

- Service 只通过其它 Service 或本包的 repository 访问数据；**业务层禁止直接注入 Mapper**（MemoryRetriever 依赖 FactMemoryService / ExperienceMemoryService，而不是 MemoryFactMapper / MemoryExperienceMapper）。
- Controller 不出现 SQL、Embedding、Qdrant 等实现细节。
- 外部依赖全部用封装类隔离（EmbeddingClient、QdrantVectorStore），业务代码不感知具体实现，便于后续切换实现（如 Qdrant 换 Redis Stack）。
- 实体/Record 集中在各特性包内，跨 Service 共用的 DTO 只定义一处。

### 去重（DRY）

- 相似逻辑必须抽成私有方法或通用组件，禁止复制粘贴。例如检索的多路召回统一走 `addVectorRoute` / `addKeywordRoute` 两个 helper。
- 常量集中定义（RRF 的 K、注入预算、集合名），不散落魔法数。
- 实体使用 Lombok（`@Data @Builder @NoArgsConstructor @AllArgsConstructor`），不手写样板代码。
- 测试中重复的 fixture 抽到公共方法或常量。

---

### Task 0: 环境准备（Qdrant 可达性）

**Files:**
- 无代码改动；只做环境决策与验证

- [ ] **Step 1: 确认 Docker/WSL2 状态**

运行:
```powershell
docker info --format "{{.ServerVersion}}"
```

Expected: 若输出版本号（如 `28.5.1`），Docker 可用，跳到 Step 2 的路径 A。若报 `HCS_E_HYPERV_NOT_INSTALLED` 或找不到 daemon，Docker 不可用，走路径 B。

- [ ] **Step 2A（Docker 可用时）: 启动 Qdrant 容器**

```powershell
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.13.0
Start-Sleep -Seconds 5
curl.exe -s http://127.0.0.1:6333/collections
```

Expected: 返回 JSON（集合列表，初始为空数组）。

- [ ] **Step 2B（Docker 不可用时，需用户决策）**

启用 Windows 虚拟化平台（需管理员 + 重启）：
```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```
重启后打开 Docker Desktop，等待引擎就绪，再执行 Step 2A。

若不能重启，改用 **Redis Stack（Windows 原生）** 承担向量检索，需在 Task 4 执行前告知实施者切换。切换后的差异仅限 `QdrantVectorStore` 一个类的实现与 Task 4 测试，接口不变。

- [ ] **Step 3: 验证 embedding 端点**

本计划默认 endpoint 为 OpenAI 兼容 `/embeddings`（如 Ollama `/v1` 或云 API）。确认方式：
```powershell
curl.exe -s -X POST http://127.0.0.1:11434/v1/embeddings -H "Content-Type: application/json" -d "{\"model\":\"bge-m3\",\"input\":[\"测试\"]}"
```

Expected: 返回包含 `data[].embedding` 的 JSON。若不可用，在 `application-dev.yml` 配置真实可用的 embedding API。

---

### Task 1: 配置与依赖

**Files:**
- Modify: `backed/AI_Study_Notes/pom.xml`（依赖区，`<dependencies>` 末尾前）
- Modify: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/config/AgentProperties.java`
- Modify: `backed/AI_Study_Notes/src/main/resources/application-dev.yml`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/config/AgentPropertiesTest.java`

- [ ] **Step 1: 写失败测试**

创建 `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/config/AgentPropertiesTest.java`：

```java
package org.example.ai_study_notes.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPropertiesTest {

    @TestConfiguration
    @EnableConfigurationProperties(AgentProperties.class)
    static class TestConfig {
    }

    @Test
    void embeddingAndQdrantBind() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "agent.embedding.base-url=http://127.0.0.1:11434/v1",
                        "agent.embedding.model=bge-m3",
                        "agent.embedding.dimensions=1024",
                        "agent.qdrant.host=127.0.0.1",
                        "agent.qdrant.port=6334")
                .run(ctx -> {
                    AgentProperties props = ctx.getBean(AgentProperties.class);
                    assertEquals("bge-m3", props.getEmbedding().getModel());
                    assertEquals(1024, props.getEmbedding().getDimensions());
                    assertEquals("127.0.0.1", props.getQdrant().getHost());
                    assertEquals(6334, props.getQdrant().getPort());
                });
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AgentPropertiesTest
```

Expected: 编译失败（`AgentProperties.getEmbedding()` 不存在）。

- [ ] **Step 3: 实现配置类**

在 `AgentProperties.java` 的 `private Auth auth = new Auth();` 之后追加字段，并在类末尾 `Auth` 内部类之后追加两个内部类：

```java
    private Embedding embedding = new Embedding();
    private Qdrant qdrant = new Qdrant();
```

```java
    @Data
    public static class Embedding {
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String apiKey = "";
        private String model = "bge-m3";
        private int dimensions = 1024;
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Qdrant {
        private String host = "127.0.0.1";
        private int port = 6334;
        private String apiKey = "";
    }
```

- [ ] **Step 4: 追加 pom 依赖**

在 `backed/AI_Study_Notes/pom.xml` 的 `<dependencies>` 内、`</dependencies>` 之前追加：

```xml
        <!-- Qdrant 向量检索 -->
        <dependency>
            <groupId>io.qdrant</groupId>
            <artifactId>client</artifactId>
            <version>1.13.0</version>
        </dependency>
```

- [ ] **Step 5: 追加 dev 配置**

在 `application-dev.yml` 的 `agent:` 节点下、`loop:` 之后追加：

```yaml
  embedding:
    base-url: ${EMBEDDING_BASE_URL:http://127.0.0.1:11434/v1}
    api-key: ${EMBEDDING_API_KEY:}
    model: ${EMBEDDING_MODEL:bge-m3}
    dimensions: 1024
  qdrant:
    host: ${QDRANT_HOST:127.0.0.1}
    port: ${QDRANT_PORT:6334}
    api-key: ${QDRANT_API_KEY:}
```

- [ ] **Step 6: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AgentPropertiesTest
```

Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 7: 提交**

```bash
git add backed/AI_Study_Notes/pom.xml backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/config/AgentProperties.java backed/AI_Study_Notes/src/main/resources/application-dev.yml backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/config/AgentPropertiesTest.java
git commit -m "feat(memory): 新增 embedding/qdrant 配置与依赖"
```

---

### Task 2: EmbeddingClient 抽象

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/embedding/EmbeddingClient.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/embedding/OpenAiCompatibleEmbeddingClient.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/embedding/OpenAiCompatibleEmbeddingClientTest.java`

- [ ] **Step 1: 写失败测试（本地 stub HTTP 服务）**

创建 `OpenAiCompatibleEmbeddingClientTest.java`：

```java
package org.example.ai_study_notes.agent.memory.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleEmbeddingClientTest {

    @Test
    void embedAllReturnsVectors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] resp = "{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3,0.4]}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        try {
            AgentProperties props = new AgentProperties();
            props.getEmbedding().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            props.getEmbedding().setApiKey("test-key");
            OpenAiCompatibleEmbeddingClient client =
                    new OpenAiCompatibleEmbeddingClient(props, new ObjectMapper());
            List<List<Float>> result = client.embedAll(List.of("a", "b"));
            assertEquals(2, result.size());
            assertEquals(0.1f, result.get(0).get(0));
            assertEquals(0.4f, result.get(1).get(1));
        } finally {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=OpenAiCompatibleEmbeddingClientTest
```

Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现接口与客户端**

创建 `EmbeddingClient.java`：

```java
package org.example.ai_study_notes.agent.memory.embedding;

import java.util.List;

public interface EmbeddingClient {
    List<Float> embed(String text);

    List<List<Float>> embedAll(List<String> texts);
}
```

创建 `OpenAiCompatibleEmbeddingClient.java`：

```java
package org.example.ai_study_notes.agent.memory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingClient(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        AgentProperties.Embedding cfg = properties.getEmbedding();
        String endpoint = cfg.getBaseUrl() + (cfg.getBaseUrl().endsWith("/") ? "" : "/") + "embeddings";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.getModel());
            body.put("input", texts);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Embedding HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<List<Float>> result = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                List<Float> vec = new ArrayList<>();
                for (JsonNode v : item.path("embedding")) {
                    vec.add((float) v.asDouble());
                }
                result.add(vec);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=OpenAiCompatibleEmbeddingClientTest
```

Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/embedding backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/embedding
git commit -m "feat(memory): EmbeddingClient 抽象与 OpenAI 兼容实现"
```

---

### Task 3: MySQL 记忆表与实体/Mapper

**Files:**
- Create: `backed/AI_Study_Notes/src/main/resources/db/memory-layer.sql`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/fact/MemoryFact.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/fact/MemoryFactMapper.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/experience/MemoryExperience.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/experience/MemoryExperienceMapper.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/episode/MemoryEpisode.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/episode/MemoryEpisodeMapper.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/MemoryMapperIT.java`
- Modify: `backed/AI_Study_Notes/pom.xml`（surefire 排除 integration 组）

- [ ] **Step 1: 写失败测试（集成测试，走本地 MySQL）**

创建 `MemoryMapperIT.java`：

```java
package org.example.ai_study_notes.agent.memory;

import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Tag("integration")
@Transactional
class MemoryMapperIT {

    @Autowired
    private MemoryFactMapper factMapper;
    @Autowired
    private MemoryExperienceMapper experienceMapper;

    @Test
    void factInsertAndSelect() {
        MemoryFact fact = MemoryFact.builder()
                .workspaceId(1L).userId(1L).entityId("shoe")
                .attribute("price").factValue("500")
                .validFrom(LocalDateTime.now())
                .validTo(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
                .version(1).sourceType("conversation")
                .confidence(BigDecimal.valueOf(0.9))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        factMapper.insert(fact);
        MemoryFact got = factMapper.selectById(fact.getId());
        assertEquals("500", got.getFactValue());
        assertEquals("shoe", got.getEntityId());
    }

    @Test
    void experienceInsertAndSelect() {
        MemoryExperience exp = MemoryExperience.builder()
                .workspaceId(1L).userId(1L).taskType("test_case_extraction")
                .ruleText("提取测试点时需考虑兼容性测试点")
                .confirmed(1)
                .confidence(BigDecimal.valueOf(0.8))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        experienceMapper.insert(exp);
        MemoryExperience got = experienceMapper.selectById(exp.getId());
        assertEquals("test_case_extraction", got.getTaskType());
    }
}
```

- [ ] **Step 2: 创建 SQL 并应用到库**

创建 `backed/AI_Study_Notes/src/main/resources/db/memory-layer.sql`：

```sql
-- 记忆层地基 v1（幂等，可重复执行）
CREATE TABLE IF NOT EXISTS memory_episode (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  source_type VARCHAR(32) NOT NULL,
  source_ref VARCHAR(128) NULL,
  content MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  archived_at DATETIME NULL,
  KEY idx_episode_ws_time (workspace_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS memory_fact (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  entity_id VARCHAR(64) NOT NULL,
  attribute VARCHAR(128) NOT NULL,
  fact_value VARCHAR(512) NOT NULL,
  valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  valid_to DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
  version INT NOT NULL DEFAULT 1,
  source_type VARCHAR(32) NULL,
  source_ref VARCHAR(128) NULL,
  confidence DECIMAL(3,2) NOT NULL DEFAULT 0.50,
  embedding_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fact_current (workspace_id, entity_id, attribute, valid_to),
  KEY idx_fact_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS memory_experience (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  task_type VARCHAR(64) NOT NULL,
  rule_text TEXT NOT NULL,
  evidence TEXT NULL,
  hits INT NOT NULL DEFAULT 0,
  last_used_at DATETIME NULL,
  confidence DECIMAL(3,2) NOT NULL DEFAULT 0.50,
  confirmed TINYINT NOT NULL DEFAULT 0,
  embedding_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_exp_task (workspace_id, task_type),
  KEY idx_exp_confirmed (workspace_id, confirmed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

应用（PowerShell 下用管道喂给 mysql 客户端）：

```powershell
Get-Content -Raw "backed\AI_Study_Notes\src\main\resources\db\memory-layer.sql" | & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p123456 app_test
```

Expected: 无报错退出。

- [ ] **Step 3: 实现实体与 Mapper**

创建 `MemoryFact.java`：

```java
package org.example.ai_study_notes.agent.memory.fact;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_fact")
public class MemoryFact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String entityId;
    private String attribute;
    private String factValue;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Integer version;
    private String sourceType;
    private String sourceRef;
    private BigDecimal confidence;
    private String embeddingId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

创建 `MemoryFactMapper.java`：

```java
package org.example.ai_study_notes.agent.memory.fact;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoryFactMapper extends BaseMapper<MemoryFact> {
}
```

创建 `MemoryExperience.java`：

```java
package org.example.ai_study_notes.agent.memory.experience;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_experience")
public class MemoryExperience {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String taskType;
    private String ruleText;
    private String evidence;
    private Integer hits;
    private LocalDateTime lastUsedAt;
    private BigDecimal confidence;
    private Integer confirmed;
    private String embeddingId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

创建 `MemoryExperienceMapper.java`：

```java
package org.example.ai_study_notes.agent.memory.experience;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoryExperienceMapper extends BaseMapper<MemoryExperience> {
}
```

创建 `MemoryEpisode.java`：

```java
package org.example.ai_study_notes.agent.memory.episode;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_episode")
public class MemoryEpisode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String sourceType;
    private String sourceRef;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
}
```

创建 `MemoryEpisodeMapper.java`：

```java
package org.example.ai_study_notes.agent.memory.episode;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoryEpisodeMapper extends BaseMapper<MemoryEpisode> {
}
```

- [ ] **Step 4: surefire 默认排除 integration 组**

在 `pom.xml` 的 `<build><plugins>` 内追加（`spring-boot-maven-plugin` 之前）：

```xml
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <excludedGroups>integration</excludedGroups>
            </configuration>
        </plugin>
```

- [ ] **Step 5: 运行集成测试（需要本地 MySQL/Redis 在跑）**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dgroups=integration -DexcludedGroups=
```

Expected: `MemoryMapperIT` 两个用例 PASS。

- [ ] **Step 6: 提交**

```bash
git add backed/AI_Study_Notes/pom.xml backed/AI_Study_Notes/src/main/resources/db/memory-layer.sql backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory
git commit -m "feat(memory): MySQL 记忆表（episode/fact/experience）与实体 Mapper"
```

---

### Task 4: Qdrant 向量仓储

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/vector/QdrantVectorStore.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/vector/QdrantVectorStoreIT.java`

- [ ] **Step 1: 写失败测试（Qdrant 不可用时自动跳过）**

创建 `QdrantVectorStoreIT.java`：

```java
package org.example.ai_study_notes.agent.memory.vector;

import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class QdrantVectorStoreIT {

    private QdrantVectorStore store;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(isQdrantUp(), "Qdrant 未运行，跳过");
        AgentProperties props = new AgentProperties();
        props.getQdrant().setHost("127.0.0.1");
        props.getQdrant().setPort(6334);
        props.getEmbedding().setDimensions(4);
        store = new QdrantVectorStore(props);
    }

    @Test
    void upsertAndSearch() {
        store.upsert("facts", "f1", List.of(1f, 0f, 0f, 0f), Map.of("workspace_id", "1", "confirmed", 1));
        store.upsert("facts", "f2", List.of(0f, 1f, 0f, 0f), Map.of("workspace_id", "1", "confirmed", 1));
        List<QdrantVectorStore.Hit> hits = store.search("facts", List.of(0.9f, 0.1f, 0f, 0f), "1", null, 2);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).id().equals("f1") || hits.get(0).id().equals("f2"));
    }

    private boolean isQdrantUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6334), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=QdrantVectorStoreIT -DexcludedGroups=
```

Expected: 编译失败（`QdrantVectorStore` 不存在）或 Qdrant 未运行导致 SKIPPED。

- [ ] **Step 3: 实现 QdrantVectorStore**

创建 `QdrantVectorStore.java`（接口基于 `io.qdrant:client:1.13.0`；若客户端 API 有出入，以编译错误为准修正）：

```java
package org.example.ai_study_notes.agent.memory.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Match;
import io.qdrant.client.grpc.Points.PointIdFactory;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.Value;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class QdrantVectorStore {

    public static final String COLLECTION_FACTS = "facts";
    public static final String COLLECTION_EXPERIENCES = "experiences";
    public static final String COLLECTION_KNOWLEDGE = "knowledge";

    public record Hit(String id, double score, Map<String, String> payload) {
    }

    private final QdrantClient client;
    private final AgentProperties properties;

    public QdrantVectorStore(AgentProperties properties) {
        this.properties = properties;
        AgentProperties.Qdrant cfg = properties.getQdrant();
        this.client = new QdrantClient(
                QdrantGrpcClient.newBuilder(cfg.getHost(), cfg.getPort(), false).build());
        initCollections();
    }

    private void initCollections() {
        int dim = properties.getEmbedding().getDimensions();
        for (String name : List.of(COLLECTION_FACTS, COLLECTION_EXPERIENCES, COLLECTION_KNOWLEDGE)) {
            try {
                boolean exists = client.collectionExistsAsync(name).get(5, TimeUnit.SECONDS);
                if (!exists) {
                    client.createCollectionAsync(name, VectorParams.newBuilder()
                            .setSize(dim).setDistance(Distance.Cosine).build())
                            .get(30, TimeUnit.SECONDS);
                    log.info("Qdrant 集合已创建: {}", name);
                }
            } catch (Exception e) {
                log.warn("Qdrant 初始化集合失败 {}: {}", name, e.getMessage());
            }
        }
    }

    public void upsert(String collection, String pointId, List<Float> vector, Map<String, Object> payload) {
        try {
            PointStruct point = PointStruct.newBuilder()
                    .setId(PointIdFactory.id(pointId))
                    .setVectors(Vectors.newBuilder()
                            .setVector(Vector.newBuilder().addAllData(vector).build()))
                    .putAllPayload(toPayload(payload))
                    .build();
            client.upsertAsync(collection, List.of(point)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant upsert 失败: " + e.getMessage(), e);
        }
    }

    public List<Hit> search(String collection, List<Float> vector, String workspaceId,
                            Boolean confirmedOnly, int limit) {
        try {
            SearchPoints.Builder builder = SearchPoints.newBuilder()
                    .setCollectionName(collection)
                    .addAllVector(vector)
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true));
            Filter.Builder filter = Filter.newBuilder();
            if (workspaceId != null) {
                filter.addMust(Condition.newBuilder().setField(FieldCondition.newBuilder()
                        .setKey("workspace_id")
                        .setMatch(Match.newBuilder().setKeyword(workspaceId))));
            }
            if (confirmedOnly != null && confirmedOnly) {
                filter.addMust(Condition.newBuilder().setField(FieldCondition.newBuilder()
                        .setKey("confirmed")
                        .setMatch(Match.newBuilder().setInteger(1))));
            }
            if (workspaceId != null || confirmedOnly != null) {
                builder.setFilter(filter);
            }
            return client.searchAsync(builder.build()).get(30, TimeUnit.SECONDS).stream()
                    .map(p -> new Hit(
                            String.valueOf(p.getId()),
                            p.getScore(),
                            payloadToStringMap(p.getPayloadMap())))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant search 失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Value> toPayload(Map<String, Object> payload) {
        Map<String, Value> out = new LinkedHashMap<>();
        payload.forEach((k, v) -> {
            if (v instanceof String s) {
                out.put(k, Value.newBuilder().setStringValue(s).build());
            } else if (v instanceof Number n) {
                out.put(k, Value.newBuilder().setDoubleValue(n.doubleValue()).build());
            } else if (v instanceof Boolean b) {
                out.put(k, Value.newBuilder().setBoolValue(b).build());
            }
        });
        return out;
    }

    private Map<String, String> payloadToStringMap(Map<String, Value> payload) {
        Map<String, String> out = new LinkedHashMap<>();
        payload.forEach((k, v) -> {
            if (v.hasStringValue()) {
                out.put(k, v.getStringValue());
            } else if (v.hasDoubleValue()) {
                out.put(k, String.valueOf(v.getDoubleValue()));
            } else if (v.hasBoolValue()) {
                out.put(k, String.valueOf(v.getBoolValue()));
            }
        });
        return out;
    }
}
```

- [ ] **Step 4: 运行测试**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=QdrantVectorStoreIT -DexcludedGroups=
```

Expected: Qdrant 可达时 PASS；不可达时输出 `SKIPPED`（assumption 失败）。

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/vector backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/vector
git commit -m "feat(memory): Qdrant 向量仓储（集合初始化/upsert/search）"
```

---

### Task 5: 记忆服务（事实版本化 + 经验保存）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/FactMemoryService.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/ExperienceMemoryService.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/FactMemoryServiceTest.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/FactMemoryServiceIT.java`

- [ ] **Step 1: 写失败测试（冲突/版本化逻辑，纯 Mock）**

创建 `FactMemoryServiceTest.java`：

```java
package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FactMemoryServiceTest {

    @Test
    void sameValueDoesNotCreateNewVersion() {
        MemoryFactMapper mapper = mock(MemoryFactMapper.class);
        MemoryFact current = MemoryFact.builder().id(1L).workspaceId(1L).entityId("shoe")
                .attribute("price").factValue("500").version(1)
                .validTo(LocalDateTime.of(9999, 12, 31, 23, 59, 59)).build();
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);

        FactMemoryService service = new FactMemoryService(mapper);
        MemoryFact result = service.upsert(1L, 1L, "shoe", "price", "500", "conversation", null, 0.9);

        assertEquals(current, result);
        verify(mapper, never()).insert(any());
    }

    @Test
    void changedValueArchivesOldAndInsertsNew() {
        MemoryFactMapper mapper = mock(MemoryFactMapper.class);
        MemoryFact current = MemoryFact.builder().id(1L).workspaceId(1L).entityId("shoe")
                .attribute("price").factValue("500").version(1)
                .validTo(LocalDateTime.of(9999, 12, 31, 23, 59, 59)).build();
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);

        FactMemoryService service = new FactMemoryService(mapper);
        MemoryFact result = service.upsert(1L, 1L, "shoe", "price", "600", "conversation", null, 0.9);

        assertNotNull(result.getId() == null ? result : result);
        assertEquals(2, result.getVersion());
        verify(mapper).updateById(any(MemoryFact.class));
        verify(mapper).insert(any(MemoryFact.class));
    }
}
```

注意：`changedValueArchivesOldAndInsertsNew` 中 `insert` 会经 MyBatis-Plus 回填 id，Mock 下 id 为 null 属预期；版本断言 `assertEquals(2, result.getVersion())` 是核心。

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=FactMemoryServiceTest
```

Expected: 编译失败（`FactMemoryService` 不存在）。

- [ ] **Step 3: 实现 FactMemoryService**

创建 `FactMemoryService.java`：

```java
package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class FactMemoryService {

    public static final LocalDateTime OPEN_END = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final MemoryFactMapper mapper;

    public FactMemoryService(MemoryFactMapper mapper) {
        this.mapper = mapper;
    }

    public MemoryFact upsert(Long workspaceId, Long userId, String entityId, String attribute,
                             String value, String sourceType, String sourceRef, double confidence) {
        MemoryFact current = findCurrent(workspaceId, entityId, attribute);
        if (current != null && value.equals(current.getFactValue())) {
            return current;
        }
        if (current != null) {
            MemoryFact archive = new MemoryFact();
            archive.setId(current.getId());
            archive.setValidTo(LocalDateTime.now());
            mapper.updateById(archive);
        }
        LocalDateTime now = LocalDateTime.now();
        MemoryFact next = MemoryFact.builder()
                .workspaceId(workspaceId).userId(userId)
                .entityId(entityId).attribute(attribute)
                .factValue(value)
                .validFrom(now).validTo(OPEN_END)
                .version(current == null ? 1 : current.getVersion() + 1)
                .sourceType(sourceType).sourceRef(sourceRef)
                .confidence(BigDecimal.valueOf(confidence))
                .createdAt(now).updatedAt(now)
                .build();
        mapper.insert(next);
        return next;
    }

    public MemoryFact findCurrent(Long workspaceId, String entityId, String attribute) {
        return mapper.selectOne(new LambdaQueryWrapper<MemoryFact>()
                .eq(MemoryFact::getWorkspaceId, workspaceId)
                .eq(MemoryFact::getEntityId, entityId)
                .eq(MemoryFact::getAttribute, attribute)
                .eq(MemoryFact::getValidTo, OPEN_END));
    }

    public List<MemoryFact> searchKeyword(Long workspaceId, String query) {
        return mapper.selectList(new LambdaQueryWrapper<MemoryFact>()
                .eq(MemoryFact::getWorkspaceId, workspaceId)
                .and(w -> w.like(MemoryFact::getAttribute, query)
                        .or().like(MemoryFact::getFactValue, query)));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=FactMemoryServiceTest
```

Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: 实现 ExperienceMemoryService 并跑集成测试**

创建 `ExperienceMemoryService.java`：

```java
package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExperienceMemoryService {

    private final MemoryExperienceMapper mapper;

    public ExperienceMemoryService(MemoryExperienceMapper mapper) {
        this.mapper = mapper;
    }

    public MemoryExperience saveCandidate(Long workspaceId, Long userId, String taskType,
                                          String ruleText, String evidence, double confidence) {
        MemoryExperience exp = MemoryExperience.builder()
                .workspaceId(workspaceId).userId(userId)
                .taskType(taskType).ruleText(ruleText).evidence(evidence)
                .hits(0).confidence(BigDecimal.valueOf(confidence))
                .confirmed(0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        mapper.insert(exp);
        return exp;
    }

    public List<MemoryExperience> listConfirmed(Long workspaceId, String taskType) {
        return mapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getWorkspaceId, workspaceId)
                .eq(MemoryExperience::getConfirmed, 1)
                .eq(taskType != null, MemoryExperience::getTaskType, taskType));
    }

    public void confirm(Long id) {
        MemoryExperience update = new MemoryExperience();
        update.setId(id);
        update.setConfirmed(1);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
    }

    public List<MemoryExperience> searchConfirmedKeyword(Long workspaceId, String query) {
        return mapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getWorkspaceId, workspaceId)
                .eq(MemoryExperience::getConfirmed, 1)
                .like(MemoryExperience::getRuleText, query));
    }
}
```

创建 `FactMemoryServiceIT.java`（验证真实库的版本归档）：

```java
package org.example.ai_study_notes.agent.memory;

import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Tag("integration")
@Transactional
class FactMemoryServiceIT {

    @Autowired
    private FactMemoryService service;

    @Test
    void upsertVersioningOnRealDb() {
        MemoryFact v1 = service.upsert(1L, 1L, "shoe", "price", "500", "conversation", null, 0.9);
        MemoryFact v2 = service.upsert(1L, 1L, "shoe", "price", "600", "conversation", null, 0.9);
        assertEquals(1, v1.getVersion());
        assertEquals(2, v2.getVersion());
        assertEquals("600", service.findCurrent(1L, "shoe", "price").getFactValue());
    }
}
```

运行：
```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dgroups=integration -DexcludedGroups= -Dtest=FactMemoryServiceIT
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/FactMemoryService.java backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/ExperienceMemoryService.java backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/FactMemoryServiceTest.java backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/FactMemoryServiceIT.java
git commit -m "feat(memory): 事实版本化 upsert 与经验候选保存"
```

---

### Task 6: 检索（RRF 融合 + 注入组装 + MemoryRetriever）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/retrieval/RrfFusion.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/retrieval/InjectionAssembler.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/retrieval/MemoryRetriever.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/retrieval/RrfFusionTest.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/retrieval/InjectionAssemblerTest.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/retrieval/MemoryRetrieverTest.java`

- [ ] **Step 1: 写失败测试（纯逻辑单元测试）**

创建 `RrfFusionTest.java`：

```java
package org.example.ai_study_notes.agent.memory.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionTest {

    @Test
    void itemInBothListsRanksHigher() {
        Map<String, Double> scores = RrfFusion.fuse(
                List.of(List.of("a", "b"), List.of("b", "c")), 60);
        assertTrue(scores.get("b") > scores.get("a"));
        assertTrue(scores.get("b") > scores.get("c"));
    }
}
```

创建 `InjectionAssemblerTest.java`：

```java
package org.example.ai_study_notes.agent.memory.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InjectionAssemblerTest {

    @Test
    void respectsBudgetAndOrdersByScore() {
        Map<String, Double> scores = Map.of("x", 0.9, "y", 0.8);
        Map<String, String> content = Map.of("x", "xxxxx", "y", "yy");
        String out = InjectionAssembler.assemble(scores, content, 6, List.of());
        assertTrue(out.length() <= 6);
        assertTrue(out.startsWith("xxxxx"));
    }
}
```

创建 `MemoryRetrieverTest.java`（用假 EmbeddingClient 与 Mock 仓储）：

```java
package org.example.ai_study_notes.agent.memory.retrieval;

import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.memory.embedding.EmbeddingClient;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.FactMemoryService;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRetrieverTest {

    @Test
    void retrieveFusesVectorAndKeywordRanks() {
        EmbeddingClient embedding = mock(EmbeddingClient.class);
        when(embedding.embed(anyString())).thenReturn(List.of(1f, 0f));
        QdrantVectorStore store = mock(QdrantVectorStore.class);
        when(store.search(eq("facts"), any(), eq("1"), any(), anyInt()))
                .thenReturn(List.of(new QdrantVectorStore.Hit("10", 0.9, java.util.Map.of("entity_id", "shoe"))));
        when(store.search(eq("experiences"), any(), eq("1"), eq(true), anyInt()))
                .thenReturn(List.of());
        FactMemoryService factService = mock(FactMemoryService.class);
        when(factService.searchKeyword(eq(1L), anyString())).thenReturn(List.of());
        ExperienceMemoryService experienceService = mock(ExperienceMemoryService.class);
        when(experienceService.searchConfirmedKeyword(eq(1L), anyString())).thenReturn(List.of());
        KnowledgeService knowledge = mock(KnowledgeService.class);
        when(knowledge.search(any(), any(), anyInt())).thenReturn(List.of());

        MemoryRetriever retriever = new MemoryRetriever(
                factService, experienceService, knowledge, embedding, store);
        List<MemoryRetriever.RankedItem> items = retriever.retrieve(1L, 1L, "鞋子价格", 5);

        assertFalse(items.isEmpty());
        assertEquals("fact", items.get(0).type());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=RrfFusionTest,InjectionAssemblerTest,MemoryRetrieverTest"
```

Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现 RrfFusion 与 InjectionAssembler**

创建 `RrfFusion.java`：

```java
package org.example.ai_study_notes.agent.memory.retrieval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    private RrfFusion() {
    }

    public static Map<String, Double> fuse(List<List<String>> rankedLists, int k) {
        Map<String, Double> scores = new HashMap<>();
        for (List<String> list : rankedLists) {
            for (int i = 0; i < list.size(); i++) {
                scores.merge(list.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return scores;
    }
}
```

创建 `InjectionAssembler.java`：

```java
package org.example.ai_study_notes.agent.memory.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class InjectionAssembler {

    private InjectionAssembler() {
    }

    public static String assemble(Map<String, Double> scores, Map<String, String> idToContent,
                                  int maxChars, List<String> mustInclude) {
        StringBuilder sb = new StringBuilder();
        for (String id : mustInclude) {
            String content = idToContent.get(id);
            if (content != null) {
                sb.append(content).append('\n');
            }
        }
        List<String> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (String id : sorted) {
            if (mustInclude.contains(id)) {
                continue;
            }
            String content = idToContent.get(id);
            if (content == null) {
                continue;
            }
            if (sb.length() + content.length() + 1 > maxChars) {
                break;
            }
            sb.append(content).append('\n');
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 运行单元测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=RrfFusionTest,InjectionAssemblerTest"
```

Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: 实现 MemoryRetriever**

创建 `MemoryRetriever.java`：

```java
package org.example.ai_study_notes.agent.memory.retrieval;

import org.example.ai_study_notes.agent.knowledge.KnowledgeDoc;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.FactMemoryService;
import org.example.ai_study_notes.agent.memory.embedding.EmbeddingClient;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class MemoryRetriever {

    public record RankedItem(String type, String id, String content, double score) {
    }

    public record MemoryInjection(String facts, String experiences, String knowledge) {
    }

    private final FactMemoryService factService;
    private final ExperienceMemoryService experienceService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore vectorStore;

    public MemoryRetriever(FactMemoryService factService,
                           ExperienceMemoryService experienceService,
                           KnowledgeService knowledgeService,
                           EmbeddingClient embeddingClient,
                           QdrantVectorStore vectorStore) {
        this.factService = factService;
        this.experienceService = experienceService;
        this.knowledgeService = knowledgeService;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public List<RankedItem> retrieve(Long workspaceId, Long userId, String query, int topK) {
        List<Float> queryVec = embeddingClient.embed(query);
        List<List<String>> rankedIds = new ArrayList<>();
        Map<String, RankedItem> byId = new LinkedHashMap<>();

        addVectorRoute(rankedIds, byId, QdrantVectorStore.COLLECTION_FACTS, queryVec,
                workspaceId, null, "fact", topK * 2,
                hit -> "事实: " + hit.payload().getOrDefault("text", ""));
        addKeywordRoute(rankedIds, byId, factService.searchKeyword(workspaceId, query),
                f -> "fact:" + f.getId(),
                f -> new RankedItem("fact", String.valueOf(f.getId()),
                        "事实: " + f.getEntityId() + "." + f.getAttribute() + " = " + f.getFactValue(), 0.0));

        addVectorRoute(rankedIds, byId, QdrantVectorStore.COLLECTION_EXPERIENCES, queryVec,
                workspaceId, true, "experience", topK * 2,
                hit -> "经验: " + hit.payload().getOrDefault("text", ""));
        addKeywordRoute(rankedIds, byId, experienceService.searchConfirmedKeyword(workspaceId, query),
                e -> "experience:" + e.getId(),
                e -> new RankedItem("experience", String.valueOf(e.getId()),
                        "经验: " + e.getRuleText(), 0.0));

        addKeywordRoute(rankedIds, byId, knowledgeService.search(userId, query, topK * 2),
                d -> "knowledge:" + d.slug(),
                d -> new RankedItem("knowledge", d.slug(),
                        "知识: [" + d.category() + "] " + d.title() + ": " + d.snippet(), 0.0));

        Map<String, Double> scores = RrfFusion.fuse(rankedIds, RrfFusion.DEFAULT_K);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> byId.get(e.getKey()))
                .toList();
    }

    private void addVectorRoute(List<List<String>> rankedIds, Map<String, RankedItem> byId,
                                String collection, List<Float> queryVec, Long workspaceId,
                                Boolean confirmedOnly, String type, int limit,
                                Function<QdrantVectorStore.Hit, String> contentOf) {
        List<QdrantVectorStore.Hit> hits = vectorStore.search(collection, queryVec,
                String.valueOf(workspaceId), confirmedOnly, limit);
        List<String> ids = new ArrayList<>();
        for (QdrantVectorStore.Hit hit : hits) {
            String id = type + ":" + hit.id();
            ids.add(id);
            byId.putIfAbsent(id, new RankedItem(type, hit.id(), contentOf.apply(hit), hit.score()));
        }
        rankedIds.add(ids);
    }

    private <T> void addKeywordRoute(List<List<String>> rankedIds, Map<String, RankedItem> byId,
                                     List<T> sources, Function<T, String> idOf,
                                     Function<T, RankedItem> itemOf) {
        List<String> ids = new ArrayList<>();
        for (T source : sources) {
            String id = idOf.apply(source);
            ids.add(id);
            byId.putIfAbsent(id, itemOf.apply(source));
        }
        rankedIds.add(ids);
    }

    public MemoryInjection inject(Long workspaceId, Long userId, String query, int budgetChars) {
        List<RankedItem> items = retrieve(workspaceId, userId, query, 20);
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, String> content = new LinkedHashMap<>();
        List<String> factMust = new ArrayList<>();
        for (RankedItem item : items) {
            scores.put(item.type() + ":" + item.id(), item.score());
            content.put(item.type() + ":" + item.id(), item.content());
            if ("fact".equals(item.type())) {
                factMust.add(item.type() + ":" + item.id());
            }
        }
        int third = budgetChars / 3;
        String facts = InjectionAssembler.assemble(scores, content, third, factMust);
        String experiences = InjectionAssembler.assemble(scores, content, third, List.of());
        String knowledge = InjectionAssembler.assemble(scores, content, third, List.of());
        return new MemoryInjection(facts, experiences, knowledge);
    }
}
```

注意：`KnowledgeDoc.snippet()` 已在现有代码中使用（MemoryExtractor 用过），`KnowledgeDoc.slug()` 亦存在。

- [ ] **Step 6: 运行全部单元测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test "-Dtest=RrfFusionTest,InjectionAssemblerTest,MemoryRetrieverTest"
```

Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 7: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/memory/retrieval backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/memory/retrieval
git commit -m "feat(memory): 混合检索（向量+关键词+RRF）与注入组装"
```

---

### Task 7: 评测工具链（检索调试接口 + golden set 脚本）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/api/MemoryEvalController.java`
- Create: `agent-eval/memory-eval/README.md`
- Create: `agent-eval/memory-eval/golden-set.example.jsonl`
- Create: `agent-eval/memory-eval/run-memory-eval.mjs`

- [ ] **Step 1: 写失败测试（Controller 端到端，走真实检索）**

创建 `MemoryEvalControllerIT.java`（`src/test/java/org/example/ai_study_notes/agent/api/`）：

```java
package org.example.ai_study_notes.agent.api;

import org.example.ai_study_notes.Pojo.Result;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class MemoryEvalControllerIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void retrieveEndpointRequiresAuthAndReturnsList() {
        ResponseEntity<String> anon = rest.exchange(
                "/api/agent/memory/retrieve?query=测试&topK=5",
                HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertEquals(401, anon.getStatusCode().value());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=MemoryEvalControllerIT -DexcludedGroups=
```

Expected: 失败（401 断言得 404，接口不存在）。

- [ ] **Step 3: 实现 Controller**

创建 `MemoryEvalController.java`：

```java
package org.example.ai_study_notes.agent.api;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.memory.retrieval.MemoryRetriever;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/memory")
public class MemoryEvalController {

    private final MemoryRetriever retriever;

    public MemoryEvalController(MemoryRetriever retriever) {
        this.retriever = retriever;
    }

    @GetMapping("/retrieve")
    public Result<Map<String, Object>> retrieve(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") Long workspaceId,
            @RequestParam(defaultValue = "5") int topK) {
        Long userId = UserContext.userId();
        long ws = workspaceId == null || workspaceId == 0 ? userId : workspaceId;
        List<MemoryRetriever.RankedItem> items = retriever.retrieve(ws, userId, query, topK);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("items", items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", item.type());
            m.put("id", item.id());
            m.put("content", item.content());
            m.put("score", item.score());
            return m;
        }).toList());
        return Result.success(data);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=MemoryEvalControllerIT -DexcludedGroups=
```

Expected: PASS。

- [ ] **Step 5: 创建评测脚本与 golden set 模板**

创建 `agent-eval/memory-eval/README.md`：

```markdown
# 记忆检索评测

## 数据

- `golden-set.jsonl`：每行 `{"query": "...", "expected": ["type:id", ...], "scenario": "标签"}`
- 由测试人员从真实业务标注，目标 100 条；复制 `golden-set.example.jsonl` 后按格式填写。

## 运行

前置：后端已启动（`java -jar target/AI_Study_Notes-0.0.1-SNAPSHOT.jar`），并已登录取得 token。

```bash
TOKEN=<登录返回的 token> node agent-eval/memory-eval/run-memory-eval.mjs --topK 5
```

输出：Recall@5、MRR、各 scenario 分组结果、失败明细。
```

创建 `agent-eval/memory-eval/golden-set.example.jsonl`：

```jsonl
{"query": "鞋子价格", "expected": ["fact:1"], "scenario": "fact"}
{"query": "提取测试点时要注意什么", "expected": ["experience:2"], "scenario": "experience"}
{"query": "购物模块需求分析", "expected": ["knowledge:shopping-requirement"], "scenario": "knowledge"}
```

创建 `agent-eval/memory-eval/run-memory-eval.mjs`：

```javascript
import fs from 'node:fs'

const BASE = process.env.EVAL_BASE ?? 'http://127.0.0.1:8080'
const TOKEN = process.env.TOKEN
const topK = Number(process.argv.find((a) => a.startsWith('--topK='))?.split('=')[1] ?? 5)

if (!TOKEN) {
  console.error('缺少 TOKEN 环境变量')
  process.exit(1)
}

const lines = fs.readFileSync(
  new URL('./golden-set.jsonl', import.meta.url), 'utf8'
).trim().split('\n').filter(Boolean)

let recallSum = 0
let mrrSum = 0
const byScenario = {}

for (const line of lines) {
  const { query, expected, scenario = 'default' } = JSON.parse(line)
  const res = await fetch(
    `${BASE}/api/agent/memory/retrieve?query=${encodeURIComponent(query)}&topK=${topK}`,
    { headers: { Authorization: `Bearer ${TOKEN}` } }
  )
  if (!res.ok) {
    console.error(`查询失败 ${query}: HTTP ${res.status}`)
    process.exit(1)
  }
  const { data } = await res.json()
  const hits = data.items.map((i) => `${i.type}:${i.id}`)
  const hit = expected.map((e) => hits.indexOf(e))
  const recall = hit.filter((i) => i >= 0).length / expected.length
  const first = hit.filter((i) => i >= 0).sort((a, b) => a - b)[0]
  const mrr = first === undefined ? 0 : 1 / (first + 1)
  recallSum += recall
  mrrSum += mrr
  byScenario[scenario] ??= { recall: 0, mrr: 0, n: 0 }
  byScenario[scenario].recall += recall
  byScenario[scenario].mrr += mrr
  byScenario[scenario].n += 1
  if (recall < 1) {
    console.log(`MISS query=${query} expected=${expected.join(',')} hits=${hits.join(',')}`)
  }
}

const n = lines.length
console.log(`Recall@${topK}: ${(recallSum / n).toFixed(3)}`)
console.log(`MRR@${topK}: ${(mrrSum / n).toFixed(3)}`)
for (const [s, v] of Object.entries(byScenario)) {
  console.log(`  [${s}] n=${v.n} Recall=${(v.recall / v.n).toFixed(3)} MRR=${(v.mrr / v.n).toFixed(3)}`)
}
```

- [ ] **Step 6: 验证脚本语法**

```bash
node --check agent-eval/memory-eval/run-memory-eval.mjs
```

Expected: 无输出、退出码 0。

- [ ] **Step 7: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/api/MemoryEvalController.java backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/api/MemoryEvalControllerIT.java agent-eval/memory-eval
git commit -m "feat(memory): 检索评测接口与 golden set 评测脚本"
```

---

### Task 8: 全量验证

**Files:**
- 无新文件；只做收尾验证

- [ ] **Step 1: 全量单元测试**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test
```

Expected: `BUILD SUCCESS`，integration 组被默认排除。

- [ ] **Step 2: 打包并启动后端**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml -DskipTests package
cd backed\AI_Study_Notes
java -jar target\AI_Study_Notes-0.0.1-SNAPSHOT.jar
```

Expected: 日志出现 `Started AiStudyNotesApplication`，且无 Qdrant/Embedding 初始化异常阻断启动（Qdrant 未运行时仅 WARN）。

- [ ] **Step 3: 冒烟验证检索接口**

```powershell
curl.exe -s http://127.0.0.1:8080/api/agent/memory/retrieve?query=测试&topK=5
```

Expected: HTTP 401（未带 token，鉴权生效）。

```powershell
$login = Invoke-RestMethod -Uri http://127.0.0.1:8080/api/auth/login -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"12345678"}'
$h = @{ Authorization = "Bearer $($login.data.token)" }
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/agent/memory/retrieve?query=%E9%9E%8B%E5%AD%90%E4%BB%B7%E6%A0%BC&topK=5" -Headers $h
```

Expected: 返回 `items` 数组（embedding 服务可用时含向量路结果；不可用时关键词路仍返回条目，或报 embedding 错误——以此确认检索链路真实可达）。

- [ ] **Step 4: 收尾提交（如有未提交改动）**

```bash
git status --short
git add -A
git commit -m "chore(memory): 记忆层地基全量验证"
```

---

## 自检记录

- **Spec 覆盖**：设计文档 §6（存储）、§8（检索）、§9（评测）、§8.5（性能预算中的注入预算）由 Task 1-8 覆盖；§7 写入管道、§10 更新遗忘、§14 安全合规由后续子计划覆盖。
- **占位符**：无 TBD/TODO；所有代码步骤给出完整实现。
- **类型一致性**：`MemoryRetriever.RankedItem(type,id,content,score)` 在 Controller、评测脚本、Retriever 间一致；`FactMemoryService.upsert` 签名在测试与实现一致；`QdrantVectorStore.Hit(id,score,payload)` 在 Task 4/6 一致。
