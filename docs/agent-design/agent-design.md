# AutoTestPlatform Agent 技术实现文档

> 状态：草稿 v0.2
> 说明：本文档用于指导后续 Agent 的正式开发。内容会根据需求补充持续迭代，确认前不代表最终设计。
>
> 综合三份参考设计后的完整技术实现蓝图见：[technical-blueprint.md](technical-blueprint.md)
> 可直接实现的规格见：[implementation-spec.md](implementation-spec.md)

## 1. 背景与目标

AutoTestPlatform 目前已经具备：

- 项目管理（API 项目、UI 项目）
- 测试用例管理（API 用例、UI 用例）
- 测试执行（单接口执行、API 批量执行、UI 执行、UI 批量执行）
- 测试报告查询与 Allure 导出

本次要设计并实现一个可交互的 Agent，把它接入平台，让用户通过自然语言对话完成测试工作流中的常见操作。

## 2. 核心能力需求

### 2.1 对话交流

- 支持普通的多轮对话。
- 对话中需要识别用户意图，并在必要时调用内置工具。
- 对话上下文应随会话持久化，支持多轮追问和修正。

### 2.2 多会话窗口

- 用户可以创建多个对话窗口（会话）。
- 支持会话列表、新建会话、切换会话、删除会话。
- 每个会话拥有独立的上下文，包括：
  - 对话历史
  - 上传的文件
  - 文件解析结果
  - 问答记录
  - 生成的用例/模板引用
- 会话按用户隔离，不同用户互不可见。
- 会话保留 7 天，线性历史，不实现分支。

### 2.3 文件上传与文件问答/操作

- 会话内支持上传文件。
- 上传后可以针对文件内容进行问答。
- 支持解析需求文档并从中提取测试需求信息。

### 2.4 平台数据查询

用户可以直接通过对话查询平台已有数据，Agent 调用内置工具访问数据库/后端接口，例如：

- 查看已有项目列表
- 查看某项目下的测试用例列表
- 查看用例详情
- 查询测试执行报告

### 2.5 测试用例执行

- 通过对话识别要执行的用例，调用现有执行接口执行。
- 支持 API 用例和 UI 用例。
- 执行结果需要以可读形式返回给用户。

### 2.6 用例生成与存储

- 上传文档后，Agent 可以根据文档生成测试用例。
- 生成结果需要先展示给用户确认，再调用新增用例接口存储。
- 需要支持 API 用例生成，UI 用例生成作为后续扩展点。

### 2.7 用例模板上下文

- 支持保存“用例模板”作为后续用例生成的上下文依据。
- 用户可以选择模板，Agent 在生成新用例时参考模板格式、字段和覆盖策略。

### 2.8 跨会话记忆

- 支持记住用户偏好与生成约定（默认项目、命名/断言风格、常用环境等）。
- 记忆可查看、修改、删除，重要写入需用户确认。
- 平台数据仍以实时查询为准，记忆不缓存项目/用例/报告快照。

### 2.9 权限与确认

- 读操作（查询项目、用例、报告）自动放行。
- 写与执行操作（新增/修改/删除用例、执行测试）必须经用户确认。
- 会话、文件、模板、记忆按用户隔离。

### 2.10 登录与用户体系

- 新增账号密码登录，密码 BCrypt 存储，JWT 认证。
- 角色分为 user / admin，Agent 接口全部要求登录。
- 登录后的 user_id 贯穿会话、文件、记忆、模板与工具调用。
- 现有项目/用例/执行/报告/UI 接口也全部要求登录；业务数据暂不做按用户隔离。

## 3. 产品交互形态（初步）

建议形态：平台内新增“AI Agent”页面，采用左侧会话列表 + 右侧对话工作台。

- 左侧：会话列表，支持新建/切换/删除。
- 右侧：消息流、输入框、附件上传、工具调用状态、执行结果卡片。
- 对话消息中支持展示：
  - 普通文本
  - 结构化数据（项目/用例/报告列表）
  - 工具调用过程（可选展示）
  - 执行结果与断言详情
  - 生成的用例预览与“确认保存”操作

## 4. 现有平台能力盘点（Agent 可调用工具）

以下接口均已在当前后端存在，Agent 工具层可以直接复用，不需要重复实现业务逻辑。

### 4.1 API 项目管理

| 方法 | 路径 | 用途 | 主要入参 | 返回 |
| --- | --- | --- | --- | --- |
| GET | `/api/projects` | 查询项目列表 | - | `List<ProjectVO>` |
| POST | `/api/projects` | 新增项目 | `ProjectDTO(id, name)` | `Result` |
| PUT | `/api/projects/{id}` | 更新项目 | `ProjectDTO(id, name)` | `Result` |
| DELETE | `/api/projects/{id}` | 删除项目 | `id` | `Result` |

### 4.2 API 用例管理

| 方法 | 路径 | 用途 | 主要入参 | 返回 |
| --- | --- | --- | --- | --- |
| GET | `/api/use-cases?pid={pid}` | 查询项目下用例列表 | `pid` | `List<UseCaseVO>` |
| GET | `/api/use-cases/{id}` | 查询用例详情 | `id` | `UseCase` |
| POST | `/api/use-cases` | 新增用例 | `UseCaseUpdateDTO` | `Result` |
| PUT | `/api/use-cases/{id}` | 更新用例 | `UseCase` | `Result` |
| DELETE | `/api/use-cases/{id}` | 删除用例 | `id` | `Result` |

用例关键字段：

- `id`、`pid`：用例 ID、所属项目 ID
- `name`：用例名称
- `url`：完整接口地址
- `method`：GET/POST/PUT/DELETE/PATCH
- `header`：JSON 字符串
- `param`：JSON 字符串
- `assertStr`：JSON 字符串断言配置
- `description`：用例描述

注意：新增接口 DTO 中描述字段名为 `desc`，实体与详情接口中使用 `description`，工具层映射时需要注意。

### 4.3 API 测试执行

| 方法 | 路径 | 用途 | 主要入参 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/test` | 单接口执行 | `ApiRequestDTO(method, url, header, param, assertStr)` | `ApiResponseVO` |
| POST | `/api/execute` | API 批量执行 | `BatchExecuteDTO(useCaseIds, executionCount, maxConcurrency)` | `BatchExecuteResultVO` |

返回结构：

- `ApiResponseVO`：`status`、`result`、`headers`、`time`、`size`、`assertResults`
- `BatchExecuteResultVO`：`total`、`success`、`failed`、`totalTime`、`details`
- `details[].result` 为 `ApiResponseVO`，`details[].success` 为是否成功

### 4.4 UI 项目管理与用例管理

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/ui-projects` | UI 项目管理 |
| GET | `/api/ui-use-cases?pid={pid}` | 查询项目下 UI 用例 |
| POST | `/api/ui-use-cases` | 新增 UI 用例 |
| PUT | `/api/ui-use-cases/{id}` | 更新 UI 用例 |
| DELETE | `/api/ui-use-cases/{id}` | 删除 UI 用例 |

UI 用例字段：`projectId`、`name`、`description`、`url`、`browser`、`viewport`、`headless`、`timeout`、`steps`。

### 4.5 UI 测试执行

| 方法 | 路径 | 用途 | 主要入参 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/ui-test/run` | 执行单个 UI 测试 | `UiTestCaseRequestDTO(url, browser, viewport, headless, timeout, steps)` | `Result<UiTestResultVO>` |
| POST | `/api/ui-batch-test` | UI 批量执行 | `BatchExecuteDTO(useCaseIds, executionCount, maxConcurrency)` | `UiBatchExecuteResultVO` |

### 4.6 测试报告

| 方法 | 路径 | 用途 | 主要入参 | 返回 |
| --- | --- | --- | --- | --- |
| POST | `/api/report/cases` | 查询用例执行记录 | `ReportQueryDTO(moduleName, caseName, status, startTime, endTime)` | `Result<List<TestCaseReportVO>>` |
| POST | `/api/report/export` | 导出 Allure 报告压缩包 | `ReportQueryDTO` | zip 文件 |

## 5. Agent 架构初步设想

> 技术选型待用户提供推荐后补充。

### 5.1 分层建议

```text
前端（Vue 对话界面）
  -> Agent API（会话、消息、文件、流式响应）
  -> Agent 运行时（意图识别、ReAct/Function Calling 循环、上下文管理）
  -> Tool 层（平台工具：查询、执行、生成、存储）
  -> 平台业务服务（复用现有 Controller/Service）
```

### 5.2 核心模块

- 模型层：LangChain4j + DeepSeek 官方 OpenAI 兼容 API，负责模型调用、流式、工具参数绑定与请求级重试。
- 会话管理：会话 CRUD、上下文持久化、消息历史存储。
- 消息协议：用户消息、AI 消息、工具调用、工具结果、流式增量。
- 意图与工具编排：模型根据工具清单自主决策，或使用显式路由规则。
- 工具注册表：统一声明工具名、描述、入参 Schema、调用实现、权限级别，支持启用/禁用、动态工具池与契约文件。
- 技能系统：SKILL.md 注册与加载、依赖工具激活、技能正文按需注入上下文。
- 文件处理：上传、存储、解析、问答上下文注入。
- 上下文管理：每轮上下文组装、预算控制、分层压缩、检索注入。
- 用例生成：解析需求 -> 生成用例 -> 校验 -> 用户确认 -> 存储。
- 模板管理：模板保存/加载，作为生成上下文。
- 可观测性：记录会话、token、耗时、工具调用链、失败原因。

### 5.3 工具候选清单

| 工具名 | 作用 | 对应后端能力 |
| --- | --- | --- |
| `list_projects` | 查询项目列表 | GET `/api/projects` |
| `list_use_cases` | 查询项目下用例 | GET `/api/use-cases` |
| `get_use_case` | 查询用例详情 | GET `/api/use-cases/{id}` |
| `create_use_case` | 新增 API 用例 | POST `/api/use-cases` |
| `update_use_case` | 更新 API 用例 | PUT `/api/use-cases/{id}` |
| `delete_use_case` | 删除 API 用例 | DELETE `/api/use-cases/{id}` |
| `run_api_test` | 单接口执行 | POST `/api/test` |
| `run_batch_api_test` | API 批量执行 | POST `/api/execute` |
| `list_ui_use_cases` | 查询 UI 用例 | GET `/api/ui-use-cases` |
| `create_ui_use_case` | 新增 UI 用例 | POST `/api/ui-use-cases` |
| `run_ui_test` | UI 执行 | POST `/api/ui-test/run` |
| `run_batch_ui_test` | UI 批量执行 | POST `/api/ui-batch-test` |
| `query_reports` | 查询执行报告 | POST `/api/report/cases` |
| `parse_document` | 解析上传文档 | 文档解析服务（待设计） |
| `generate_cases_from_document` | 根据文档生成用例 | 用例生成服务（待设计） |
| `save_case_template` / `load_case_template` | 模板管理 | 模板服务（待设计） |

## 6. 待确认与待补充事项

### 6.1 需要用户补充

- 技术栈已确认：Java/Spring Boot + Vue/Element Plus + LangChain4j；DeepSeek 官方 API，API Key 运行时环境变量填写。
- 多会话窗口：按用户隔离，保留 7 天，线性历史。
- 权限模型：读自动放行，写/执行需确认。
- 文件：md/PDF/Word/txt，10MB，本地存储。
- 用例模板：沿用 blueprint 结构，MVP 在对话内管理。
- 模型：DeepSeek flash，具体 model id 待定。
- 流式输出：需要，使用 SSE。
- 登录：新增账号密码 + JWT + BCrypt + Redis；Agent 接口全部要求认证。

### 6.2 需要项目内部确认

- 当前接口无统一鉴权，Agent 工具层是否需要加权限控制
- 用例生成时是否需要复用旧 AI 生成的 schema 约束，还是重新定义
- UI 用例生成是否本期实现，还是仅预留扩展点

## 7. 参考资料

- 现有后端控制器：`backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/controller/`
- 现有 DTO/VO：`backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/Pojo/`
- 前端现有页面：`AutoTest_fronted/src/views/`
- 用户后续提供的 Agent 设计实现示例
