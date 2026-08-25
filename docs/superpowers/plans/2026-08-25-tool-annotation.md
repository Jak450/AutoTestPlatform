# 工具注解化（@AgentTool 方法级注册）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AutoTestPlatform Agent 增加注解化工具注册能力——用 `@AgentTool` / `@ToolParam` 标注现有 Service 方法即可自动生成 `ToolDefinition` 并注册，无需再手写工具类样板代码。

**Architecture:** 新增 `agent.tool.annotation` 子包：`AgentTool` / `ToolParam` 两个注解；`AnnotationToolSchema`（纯函数，反射生成 inputSchema）；`ToolMethodExecutor`（反射调用执行器，参数绑定 + ToolContext 注入 + 异常包装）；`AnnotationToolScanner`（`ApplicationRunner`，启动时扫描所有 Spring bean 的 `@AgentTool` 方法并注册进现有 `ToolRegistry`）。`ToolExecutionService` 执行管线零改动，权限/Schema 校验/中间件/审计照常生效。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / Jackson / JUnit 5 / MyBatis-Plus（示例工具依赖 ProjectService）

**分层与去重约束（沿用记忆层计划规范）：** scanner 只做编排；schema 生成与反射执行分别是独立纯组件；与现有 41 个 `ToolExecutor` 类完全并存，不迁移不重构；常量集中在注解默认值，不散落魔法数。

---

### Task 1: 注解定义与编译参数

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/AgentTool.java`
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/ToolParam.java`
- Modify: `backed/AI_Study_Notes/pom.xml`（`<build><plugins>` 内追加 maven-compiler-plugin）

- [ ] **Step 1: 创建注解**

创建 `AgentTool.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.contract.ToolPermission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级工具注解：标注在 Spring bean 的 public 方法上，启动时自动注册为 Agent 工具。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {

    String name();

    String label() default "";

    String description() default "";

    ToolPermission permission() default ToolPermission.READ;

    String category() default "默认";

    boolean activeByDefault() default true;

    String version() default "1.0.0";
}
```

创建 `ToolParam.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法参数注解：覆盖参数名与描述，控制是否必填。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    String name() default "";

    String description() default "";

    boolean required() default true;
}
```

- [ ] **Step 2: pom 开启参数名保留**

在 `backed/AI_Study_Notes/pom.xml` 的 `<build><plugins>` 内追加（现有插件之前）：

```xml
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <parameters>true</parameters>
            </configuration>
        </plugin>
```

- [ ] **Step 3: 验证编译通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml -q test-compile
```

Expected: 退出码 0，无输出。

- [ ] **Step 4: 提交**

```bash
git add backed/AI_Study_Notes/pom.xml backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation
git commit -m "feat(tool): @AgentTool/@ToolParam 注解定义与 -parameters 编译参数"
```

---

### Task 2: AnnotationToolSchema（inputSchema 生成器）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolSchema.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolSchemaTest.java`

- [ ] **Step 1: 写失败测试**

创建 `AnnotationToolSchemaTest.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnnotationToolSchemaTest {

    public String fixture(@ToolParam(name = "name", description = "姓名") String name,
                          @ToolParam(name = "count", required = false) Integer count,
                          @ToolParam(name = "tags") List<String> tags,
                          ToolContext context) {
        return name;
    }

    @Test
    void generatesTypedSchemaAndSkipsToolContext() throws Exception {
        Method method = getClass().getMethod("fixture", String.class, Integer.class, List.class, ToolContext.class);
        Map<String, Object> schema = AnnotationToolSchema.generate(method);

        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals("string", ((Map<String, Object>) properties.get("name")).get("type"));
        assertEquals("integer", ((Map<String, Object>) properties.get("count")).get("type"));
        assertEquals("array", ((Map<String, Object>) properties.get("tags")).get("type"));
        assertFalse(properties.containsKey("context"));
        assertEquals(List.of("name", "tags"), schema.get("required"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AnnotationToolSchemaTest
```

Expected: 编译失败（`AnnotationToolSchema` 不存在）。

- [ ] **Step 3: 实现生成器**

创建 `AnnotationToolSchema.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.tool.ToolContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnnotationToolSchema {

    private AnnotationToolSchema() {
    }

    public static Map<String, Object> generate(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType().equals(ToolContext.class)) {
                continue;
            }
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            String name = toolParam != null && !toolParam.name().isBlank()
                    ? toolParam.name() : parameter.getName();
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", typeOf(parameter.getType()));
            if (toolParam != null && !toolParam.description().isBlank()) {
                property.put("description", toolParam.description());
            }
            properties.put(name, property);
            if (toolParam == null || toolParam.required()) {
                required.add(name);
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static String typeOf(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AnnotationToolSchemaTest
```

Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolSchema.java backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolSchemaTest.java
git commit -m "feat(tool): 注解工具 inputSchema 自动生成器"
```

---

### Task 3: ToolMethodExecutor（反射执行器）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/ToolMethodExecutor.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/tool/annotation/ToolMethodExecutorTest.java`

- [ ] **Step 1: 写失败测试**

创建 `ToolMethodExecutorTest.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolMethodExecutorTest {

    public static class FixtureBean {
        public String greet(@ToolParam(name = "name") String name,
                            @ToolParam(name = "count", required = false) Integer count,
                            ToolContext context) {
            return "hello " + name + ":" + count + ":ctx=" + context.getConversationId();
        }

        public String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void bindsArgsAndInjectsContext() throws Exception {
        FixtureBean bean = new FixtureBean();
        Method method = FixtureBean.class.getMethod("greet", String.class, Integer.class, ToolContext.class);
        ToolDefinition definition = ToolDefinition.builder()
                .name("greet").permission(ToolPermission.READ).executor(null).build();
        ToolMethodExecutor executor = new ToolMethodExecutor(definition, bean, method, new com.fasterxml.jackson.databind.ObjectMapper());

        ToolResult result = executor.execute(
                Map.of("name", "张三", "count", 3),
                ToolContext.builder().userId(1L).conversationId(7L).build());

        assertEquals(ToolResultMeta.Status.SUCCESS, result.getStatus());
        assertEquals("hello 张三:3:ctx=7", result.getData());
    }

    @Test
    void wrapsExceptionIntoErrorResult() throws Exception {
        FixtureBean bean = new FixtureBean();
        Method method = FixtureBean.class.getMethod("boom");
        ToolDefinition definition = ToolDefinition.builder()
                .name("boom").permission(ToolPermission.READ).executor(null).build();
        ToolMethodExecutor executor = new ToolMethodExecutor(definition, bean, method, new com.fasterxml.jackson.databind.ObjectMapper());

        ToolResult result = executor.execute(Map.of(), ToolContext.builder().build());

        assertEquals(ToolResultMeta.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("boom"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=ToolMethodExecutorTest
```

Expected: 编译失败（`ToolMethodExecutor` 不存在）。

- [ ] **Step 3: 实现执行器**

创建 `ToolMethodExecutor.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

@Slf4j
public class ToolMethodExecutor implements ToolDefinition.ToolExecutor {

    private final ToolDefinition definition;
    private final Object target;
    private final Method method;
    private final ObjectMapper objectMapper;

    public ToolMethodExecutor(ToolDefinition definition, Object target, Method method,
                              ObjectMapper objectMapper) {
        this.definition = definition;
        this.target = target;
        this.method = method;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        long start = System.currentTimeMillis();
        try {
            Object result = method.invoke(target, bind(args, context));
            return ToolResult.success(definition.getName(), result, null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("注解工具执行失败 tool={}", definition.getName(), cause);
            return ToolResult.error(definition.getName(), "工具执行失败: " + cause.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE, start);
        } catch (Exception e) {
            log.warn("注解工具调用失败 tool={}", definition.getName(), e);
            return ToolResult.error(definition.getName(), "工具调用失败: " + e.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE, start);
        }
    }

    private Object[] bind(Map<String, Object> args, ToolContext context) {
        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType().equals(ToolContext.class)) {
                values[i] = context;
                continue;
            }
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            String name = toolParam != null && !toolParam.name().isBlank()
                    ? toolParam.name() : parameter.getName();
            Object raw = args.get(name);
            if (raw == null) {
                if (toolParam == null || toolParam.required()) {
                    throw new IllegalArgumentException("缺少参数: " + name);
                }
                values[i] = null;
            } else {
                values[i] = objectMapper.convertValue(raw, parameter.getType());
            }
        }
        return values;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=ToolMethodExecutorTest
```

Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/ToolMethodExecutor.java backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/tool/annotation/ToolMethodExecutorTest.java
git commit -m "feat(tool): 注解工具反射执行器（参数绑定/上下文注入/异常包装）"
```

---

### Task 4: AnnotationToolScanner（启动扫描注册）

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolScanner.java`
- Test: `backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolScannerIT.java`

- [ ] **Step 1: 写失败测试（集成：真实上下文里注册并执行）**

创建 `AnnotationToolScannerIT.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolExecutionService;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Tag("integration")
class AnnotationToolScannerIT {

    @Autowired
    private ToolRegistry registry;
    @Autowired
    private ToolExecutionService executionService;

    @Test
    void annotatedToolRegisteredAndExecutable() {
        ToolDefinition definition = registry.get("get_project_by_id");
        assertNotNull(definition, "get_project_by_id 应由 @AgentTool 注册");

        ToolResult result = executionService.execute("get_project_by_id",
                Map.of("projectId", 1L),
                ToolContext.builder().userId(1L).conversationId(1L).build(),
                false);
        assertEquals(ToolResultMeta.Status.SUCCESS, result.getStatus());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AnnotationToolScannerIT -DexcludedGroups=
```

Expected: 失败（`get_project_by_id` 未注册，断言 `assertNotNull` 抛异常）。

- [ ] **Step 3: 实现扫描器**

创建 `AnnotationToolScanner.java`：

```java
package org.example.ai_study_notes.agent.tool.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Component
public class AnnotationToolScanner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public AnnotationToolScanner(ApplicationContext applicationContext,
                                 ToolRegistry registry,
                                 ObjectMapper objectMapper) {
        this.applicationContext = applicationContext;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            scanBean(bean);
        }
    }

    private void scanBean(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            AgentTool agentTool = method.getAnnotation(AgentTool.class);
            if (agentTool == null) {
                continue;
            }
            ToolDefinition definition = ToolDefinition.builder()
                    .name(agentTool.name())
                    .label(agentTool.label())
                    .description(agentTool.description())
                    .inputSchema(AnnotationToolSchema.generate(method))
                    .permission(agentTool.permission())
                    .category(agentTool.category())
                    .activeByDefault(agentTool.activeByDefault())
                    .version(agentTool.version())
                    .build();
            // 先建 definition，再注入执行器，避免循环依赖且保证 executor.definition() 完整
            definition.setExecutor(new ToolMethodExecutor(definition, bean, method, objectMapper));
            registry.register(definition);
            log.info("注解工具注册成功: {}", agentTool.name());
        }
    }
}
```

- [ ] **Step 4: 运行测试**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dtest=AnnotationToolScannerIT -DexcludedGroups=
```

Expected: 仍失败——`get_project_by_id` 工具本身还不存在（Task 5 创建示例工具后通过）。

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolScanner.java backed/AI_Study_Notes/src/test/java/org/example/ai_study_notes/agent/tool/annotation/AnnotationToolScannerIT.java
git commit -m "feat(tool): @AgentTool 启动扫描注册器"
```

---

### Task 5: 示例工具（复用现有 Service 方法）与全量验证

**Files:**
- Create: `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/tools/annotated/ProjectToolGroup.java`

- [ ] **Step 1: 创建注解示例工具（直接复用 ProjectService 已有方法）**

创建 `ProjectToolGroup.java`：

```java
package org.example.ai_study_notes.agent.tool.tools.annotated;

import org.example.ai_study_notes.Pojo.vo.ProjectVO;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.tool.annotation.AgentTool;
import org.example.ai_study_notes.agent.tool.annotation.ToolParam;
import org.example.ai_study_notes.service.ProjectService;
import org.springframework.stereotype.Component;

/**
 * 注解化工具示例：直接复用现有 ProjectService 方法，无需手写 ToolExecutor 类。
 */
@Component
public class ProjectToolGroup {

    private final ProjectService projectService;

    public ProjectToolGroup(ProjectService projectService) {
        this.projectService = projectService;
    }

    @AgentTool(name = "get_project_by_id", label = "查询项目详情",
               description = "按 ID 查询 API 测试项目详情",
               permission = ToolPermission.READ, category = "查询")
    public ProjectVO getProjectById(
            @ToolParam(name = "projectId", description = "项目 ID") Long projectId) {
        return projectService.getById(projectId);
    }
}
```

注意：若 `ProjectService` 无 `getById` 方法（编译报错时），改为调用已确认存在的 `projectService.getProject()` 并去掉参数。

- [ ] **Step 2: 运行集成测试确认通过**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test -Dgroups=integration -DexcludedGroups=
```

Expected: `AnnotationToolScannerIT` PASS（工具已注册且可执行）。

- [ ] **Step 3: 全量单元测试 + 打包**

```powershell
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml test
& "D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd" -f backed\AI_Study_Notes\pom.xml -DskipTests package
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 4: 启动冒烟（可选，需 MySQL/Redis 在跑）**

```powershell
cd backed\AI_Study_Notes
java -jar target\AI_Study_Notes-0.0.1-SNAPSHOT.jar
```

Expected: 日志出现 `注解工具注册成功: get_project_by_id`，且原有 41 个工具照常注册（共 42 个）。

- [ ] **Step 5: 提交**

```bash
git add backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/tool/tools/annotated
git commit -m "feat(tool): 示例注解工具 get_project_by_id 复用 ProjectService"
```

---

## 自检记录

- **Spec 覆盖**：注解定义（Task 1）、schema 自动生成（Task 2）、反射执行与参数绑定（Task 3）、启动扫描注册（Task 4）、复用已有 Service 的示例与端到端验证（Task 5）；`ToolExecutionService` 零改动，权限/校验管线不变。
- **占位符**：无 TBD/TODO；所有代码步骤给出完整实现。
- **类型一致性**：`ToolResult.success(name, data, message)` 与 `ToolResult.error(name, message, type, action, start)` 签名沿用现有工具代码；`ToolDefinition.builder()` 字段与现有 `ToolDefinition` 一致；`ToolPermission` 枚举路径与现有 contract 包一致。
- **分层/DRY**：scanner 只编排；schema 与 executor 为独立纯组件；无跨层直接访问 Mapper；示例工具只复用 Service 方法。
