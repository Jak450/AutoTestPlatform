# 组件清单 - 自动化测试平台

## 后端组件 (`backed/AI_Study_Notes`)

### Controller 层

| 组件 | 路径 | 职责 | 主要接口 |
|---|---|---|---|
| `ProjectController` | `controller/ApiTest/ProjectController.java` | API 项目 CRUD | `/api/projects` |
| `UseCaseController` | `controller/ApiTest/UseCaseController.java` | API 用例 CRUD | `/api/use-cases` |
| `API_TestController` | `controller/ApiTest/API_TestController.java` | 单接口即时执行 | `/api/test` |
| `BatchTestController` | `controller/ApiTest/BatchTestController.java` | API 批量并发执行 | `/api/execute` |
| `ReportController` | `controller/ApiTest/ReportController.java` | 报告查询和 Allure 导出 | `/api/report/cases`, `/api/report/export` |
| `UIProjectController` | `controller/UITest/UIProjectController.java` | UI 项目 CRUD | `/api/ui-projects` |
| `UIUseCaseController` | `controller/UITest/UIUseCaseController.java` | UI 用例 CRUD | `/api/ui-use-cases` |
| `UiTestController` | `controller/UITest/UiTestController.java` | UI 单用例执行 | `/api/ui-test/run` |
| `UIBatchTestController` | `controller/UITest/UIBatchTestController.java` | UI 批量并发执行 | `/api/ui-batch-test` |
| `AIController` | `aiservice/AIController.java` | AI 需求分析、SSE、Agent、结果分析 | `/api/ai/*` |

### Service 层

| 接口 | 实现 | 职责 |
|---|---|---|
| `ProjectService` | `ProjectServiceImpl` | API 项目管理 |
| `UseCaseService` | `UseCaseServiceImpl` | API 用例管理 |
| `ApiTestService` | `ApiTestServiceImpl` | HTTP 请求执行、断言、API 批量执行 |
| `TestReportService` | `TestReportServiceImpl` | 报告查询、记录、Allure HTML ZIP 导出 |
| `UIProjectService` | `UIProjectServiceImpl` | UI 项目管理 |
| `UIUseCaseService` | `UIUseCaseServiceImpl` | UI 用例管理 |
| `UiTestService` | `UiTestServiceImpl` | Selenium UI 执行、步骤断言、UI 批量执行 |

### AI Service 组件

| 组件 | 路径 | 职责 |
|---|---|---|
| `AIController` | `aiservice/AIController.java` | 暴露 AI REST/SSE/Agent 接口 |
| `SessionManager` | `aiservice/SessionManager.java` | Redis 保存 `DocContext` 会话 |
| `DocContext` | `aiservice/context/DocContext.java` | AI 流程上下文：原文、解析结果、分析结果、Q&A、生成用例 |
| `AIModelConfig` | `aiservice/client/AIModelConfig.java` | 读取 `ai.ark.*` 配置 |
| `AIClient` | `aiservice/client/AIClient.java` | LangChain4j 同步/流式模型调用和模型缓存 |
| `PipelineOrchestrator` | `aiservice/orchestrator/PipelineOrchestrator.java` | 管道式 AI 流程编排 |
| `RequirementAnalyzer` | `aiservice/agent/RequirementAnalyzer.java` | 需求缺口分析 |
| `QuestionGenerator` | `aiservice/agent/QuestionGenerator.java` | 澄清问题生成 |
| `ResultAnalyzer` | `aiservice/agent/ResultAnalyzer.java` | 测试报告 AI 诊断 |
| `TestCaseAgent` | `aiservice/agent/TestCaseAgent.java` | ReAct Agent 决策循环 |
| `SkillLoader` | `aiservice/skill/SkillLoader.java` | 从 `skills/*/SKILL.md` 加载 skill；自动按扩展名选择解析 skill |
| `ApiTestCaseGenerator` | `aiservice/generator/ApiTestCaseGenerator.java` | API 用例生成策略 |
| `UiTestCaseGenerator` | `aiservice/generator/UiTestCaseGenerator.java` | UI 用例生成扩展点 |

### Agent Tool 组件

| Tool | 类 | 作用 |
|---|---|---|
| `parse_document` | `ParseDocumentTool` | 根据文件扩展名加载 doc-parser skill 并解析需求文档 |
| `analyze_gap` | `AnalyzeGapTool` | 识别需求缺口和澄清点 |
| `ask_user` | `AskUserTool` | 生成最多 5 个澄清问题 |
| `generate_case` | `GenerateCaseTool` | 调用 `test-case-generator-api` 生成 API 用例 |
| `validate_case` | `ValidateCaseTool` | 校验生成用例数组、必填字段、HTTP method、URL 协议 |

### Mapper 层

| Mapper | 对应表 | 职责 |
|---|---|---|
| `ProjectMapper` | `project` | API 项目数据访问 |
| `UseCaseMapper` | `use_case` | API 用例数据访问 |
| `TestCaseReportMapper` | `test_case_report` | 测试报告数据访问 |
| `UIProjectMapper` | `uiproject` | UI 项目数据访问 |
| `UIUseCaseMapper` | `ui_use_cases` | UI 用例数据访问 |

### Entity 层

| Entity | 表名 | 关键字段 |
|---|---|---|
| `Project` | `project` | `id`, `name` |
| `UseCase` | `use_case` | `id`, `pid`, `name`, `url`, `method`, `header`, `param`, `assertStr`, `description` |
| `TestCaseReport` | `test_case_report` | 请求、响应、断言、Allure JSON、状态、耗时、时间字段 |
| `UIProject` | `uiproject` | `id`, `name`, `description`, `createTime`, `updateTime` |
| `UIUseCase` | `ui_use_cases` | `projectId`, `url`, `browser`, `viewport`, `headless`, `timeout`, `steps` |

### 基础设施组件

| 组件 | 路径 | 职责 |
|---|---|---|
| `AsyncConfig` | `config/AsyncConfig.java` | `testExecutor` 线程池配置 |
| `RedisUtils` | `config/RedisUtils.java` | Redis 序列化配置 |
| `WebConfig` | `config/WebConfig.java` | 静态资源/Allure 报告映射 |
| `BloomFilterConfig` | `config/BloomFilterConfig.java` | 布隆过滤器配置 |
| `GlobalExceptionHandler` | `handler/GlobalExceptionHandler.java` | 全局异常处理 |
| `TestReportAspect` | `Aop/TestReportAspect.java` | 捕获 `@ApiTest` 批量执行结果并写报告 |
| `IdempotentAspect` | `Aop/IdempotentAspect.java` | Redis `setIfAbsent` 防重复提交 |
| `ApiTest` | `Aop/anno/ApiTest.java` | 报告采集注解 |
| `Idempotent` | `Aop/anno/Idempotent.java` | 幂等注解 |
| `CleanTestReportCaseTask` | `Task/CleanTestReportCaseTask.java` | 定时清理报告数据 |
| `WebDriverFactory` | `POM/driver/WebDriverFactory.java` | 创建 Chrome/Firefox/Edge WebDriver |
| `BasePage` | `POM/page/BasePage.java` | UI 操作封装 |
| `JsonUtils` | `utils/JsonUtils.java` | JSON 工具 |
| `ContentTypeUtils` | `utils/ContentTypeUtils.java` | 请求 Content-Type 处理 |
| `BuildReportFileNameUtils` | `utils/BuildReportFileNameUtils.java` | 报告文件名生成 |

## 前端组件 (`AutoTest_fronted`)

### 页面组件

| 页面 | 路由 | 职责 |
|---|---|---|
| `AiRequirement.vue` | `/ai-requirement` | 上传需求、选择项目、管道/Agent 模式、流式输出、Q&A、生成并导入 API 用例 |
| `Projects.vue` | `/projects` | API 项目 CRUD |
| `UseCases.vue` | `/use-cases` | API 用例 CRUD，按项目筛选 |
| `ApiTest.vue` | `/api-test` | 单接口调试执行，展示响应与断言 |
| `BatchExecute.vue` | `/batch-execute` | API 批量执行、报告查询、Allure 导出、AI 分析报告 |
| `UiTest.vue` | `/ui-test` | UI 测试工作台，配置步骤并执行 |
| `UiProjects.vue` | `/ui-projects` | UI 项目 CRUD |
| `UiUseCases.vue` | `/ui-use-cases` | UI 用例 CRUD，编辑步骤 JSON 模型 |
| `UiBatchExecute.vue` | `/ui-batch-execute` | UI 用例批量执行 |

### 前端基础组件

| 组件 | 路径 | 职责 |
|---|---|---|
| `App.vue` | `src/App.vue` | 管理后台布局、侧边栏菜单、页面标题 |
| `router/index.js` | `src/router/index.js` | 路由表和浏览器标题设置 |
| `main.js` | `src/main.js` | Vue/Element Plus/router 初始化，axios 全局配置 |
| `vite.config.js` | `vite.config.js` | Vite 构建配置，`/api` 代理到后端 8080 |

## Skill 目录

| Skill | 当前用途 |
|---|---|
| `doc-parser-markdown` | Markdown 需求解析 |
| `doc-parser-pdf` | PDF 需求解析 |
| `doc-parser-word` | Word 需求解析 |
| `test-case-generator-api` | API 测试用例生成，当前核心启用 skill |
| `test-case-generator-ui` | UI 用例生成扩展点 |
| `test-result-analyzer` | 测试结果分析说明/扩展 |

`skills/register.json` 当前不存在；以 `SkillLoader` 的 frontmatter 解析逻辑为准。
