# 架构文档 - 自动化测试平台

## 当前整体架构

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                         Frontend: AutoTest_fronted                         │
│                                                                            │
│  Vue 3 + Vite + Element Plus                                               │
│                                                                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ AI需求分析    │ │ 接口测试管理   │ │ UI测试管理    │ │ 报告/AI分析       │  │
│  │ AiRequirement│ │ Projects/... │ │ UiProjects/..│ │ BatchExecute     │  │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └────────┬─────────┘  │
│         └────────────────┴────────────────┴──────────────────┘            │
│                         axios baseURL=/api + fetch SSE                     │
└───────────────────────────────────┬────────────────────────────────────────┘
                                    │ HTTP / SSE
┌───────────────────────────────────┴────────────────────────────────────────┐
│                       Backend: backed/AI_Study_Notes                       │
│                                                                            │
│  ┌──────────────────────────── Controller 层 ────────────────────────────┐ │
│  │ ApiTest: Project / UseCase / API_Test / BatchTest / Report            │ │
│  │ UITest : UIProject / UIUseCase / UiTest / UIBatchTest                 │ │
│  │ AI     : AIController (/api/ai/*, streaming, agent endpoints)         │ │
│  └─────────────────────────────────┬─────────────────────────────────────┘ │
│                                    │                                       │
│  ┌──────────────────────────── Service 层 ───────────────────────────────┐ │
│  │ API CRUD + HTTP execution + assertions + batch concurrency             │ │
│  │ UI CRUD + Selenium WebDriver execution + batch concurrency             │ │
│  │ TestReportService: persisted reports + Allure ZIP export               │ │
│  └─────────────────────────────────┬─────────────────────────────────────┘ │
│                                    │                                       │
│  ┌──────────────────────────── AI Service 层 ────────────────────────────┐ │
│  │ PipelineOrchestrator: parse -> analyze -> questions -> cases/result    │ │
│  │ TestCaseAgent: ReAct loop + tools                                      │ │
│  │ AIClient: LangChain4j OpenAI-compatible ARK chat/streaming client      │ │
│  │ SkillLoader: load skills/*/SKILL.md frontmatter and body               │ │
│  └─────────────────────────────────┬─────────────────────────────────────┘ │
│                                    │                                       │
│  ┌──────────────────────────── Data / Infra ─────────────────────────────┐ │
│  │ MyBatis-Plus Mapper -> MySQL app_test                                  │ │
│  │ Redis: AI sessions + idempotency keys                                  │ │
│  │ AOP: @ApiTest report capture, @Idempotent duplicate-submit guard       │ │
│  │ Selenium WebDriver, Apache HttpClient5, Allure                         │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────┘
```

## 架构模式

| 模式 | 当前实现 |
|---|---|
| 前后端分离 | 前端 `AutoTest_fronted` 通过 `/api` 代理访问 Spring Boot 8080 |
| 三层架构 | Controller -> Service/Impl -> Mapper -> MySQL |
| AOP 横切能力 | `@ApiTest` 记录测试报告；`@Idempotent` 使用 Redis 防重复提交 |
| 异步并发执行 | `AsyncConfig` 线程池 + `CompletableFuture` + `Semaphore` 控制并发 |
| POM UI 自动化 | `WebDriverFactory` + `BasePage` 执行 UI 步骤 |
| AI Pipeline | `PipelineOrchestrator` 串联文档解析、需求分析、问题生成、用例生成、结果分析 |
| ReAct Agent | `TestCaseAgent` 循环调用 `Tool`：parse/analyze/ask/generate/validate |
| Skill 机制 | `SkillLoader` 从 `skills/*/SKILL.md` 读取 frontmatter；当前没有 `register.json` |
| 统一响应 | 大多数接口使用 `Result<T>`，部分列表查询直接返回 List，批量接口直接返回 VO |

## 核心业务流程

### API 测试流程

```text
用户创建 API 项目
  -> 创建 API 用例(use_case)
  -> 单接口执行(/api/test) 或 批量执行(/api/execute)
  -> ApiTestServiceImpl 使用 HttpClient5 发起请求
  -> JSON/字符串/正则断言
  -> @ApiTest + TestReportAspect 捕获批量执行结果
  -> TestReportService 持久化 test_case_report
  -> 前端查询报告 / 导出 Allure HTML ZIP / 调 AI 分析单条报告
```

### UI 测试流程

```text
用户创建 UI 项目
  -> 创建 UI 用例(ui_use_cases，steps 为 JSON)
  -> 单用例执行(/api/ui-test/run) 或 批量执行(/api/ui-batch-test)
  -> UiTestServiceImpl 创建 WebDriver
  -> BasePage 执行 click/input/getText/getAttribute/hover/wait/scroll/screenshot/switchWindow/customCode
  -> 返回步骤级结果、断言结果、耗时和错误信息
```

### AI Pipeline 模式

```text
AiRequirement.vue 上传 .md/.pdf/.doc/.docx 文档
  -> /api/ai/analyze-requirement-stream
  -> SkillLoader 选择 doc-parser-* skill
  -> AIClient.chatStream 解析文档并推送 token
  -> RequirementAnalyzer 分析信息缺口并推送 token
  -> QuestionGenerator 生成澄清问题并推送 token
  -> SessionManager 保存 DocContext 到 Redis
  -> 用户回答 /api/ai/submit-answers
  -> /api/ai/generate-cases 生成 API 用例 JSON
  -> 前端调用 /api/use-cases 保存到用例管理
```

### AI Agent 模式

```text
AiRequirement.vue 选择 Agent 模式
  -> /api/ai/agent-analyze
  -> TestCaseAgent 使用 ReAct JSON 协议循环推理
  -> 调用 parse_document / analyze_gap / ask_user / generate_case / validate_case
  -> 返回 questions 或 cases
  -> 如需回答，则 /api/ai/agent-generate 继续生成
```

### AI 结果分析流程

```text
BatchExecute.vue 查询 test_case_report
  -> 用户点击 AI 分析
  -> /api/ai/analyze-result/{reportId}
  -> ResultAnalyzer 读取报告中的请求、响应、断言、状态
  -> AI 输出结论、错误类型、根因、修复建议
```

## 系统边界与外部依赖

| 边界 | 说明 |
|---|---|
| 前端 | Vue SPA；开发时由 Vite 代理 `/api` 到 `http://localhost:8080` |
| 后端 | Spring Boot 3.2.5，端口 8080 |
| 数据库 | MySQL，开发库名 `app_test`，DDL 在 `backed/init.sql` |
| 缓存 | Redis，用于 AI session 和幂等 key |
| AI 服务 | Volcano Engine ARK OpenAI-compatible endpoint，通过 LangChain4j 调用 |
| API 测试目标 | 任意外部 HTTP API |
| UI 测试目标 | Selenium 可访问的 Web 页面 |
| 报告 | Allure Java 生成 HTML ZIP |

## 当前注意事项

- 旧文档中 `D:/桌面/测试平台` 路径已过期，当前根目录是 `D:/桌面/AutoTestPlatform`。
- 旧文档中 Spring Boot 2.6.13 已过期，当前 `pom.xml` 为 Spring Boot 3.2.5。
- `application-dev.yml` 含本地开发配置，AI-DLC 文档不得记录真实密钥。
- `@Idempotent` 当前会在方法返回/异常后删除 Redis key，主要防并发重复提交，不是传统“成功后一段时间内禁止重复”。
- `App.vue` 已有 AI 菜单，但 `titleMap` 未包含 `/ai-requirement`，AI 页面 Header 会显示默认标题。
