# 依赖关系文档 - 自动化测试平台

## 外部 Maven 依赖

来源：`backed/AI_Study_Notes/pom.xml`

| 依赖 | 版本 | 说明 |
|---|---:|---|
| `spring-boot-starter-web` | Spring Boot 3.2.5 管理 | REST API / embedded Tomcat |
| `spring-boot-starter-aop` | Spring Boot 3.2.5 管理 | AOP 切面：报告、幂等 |
| `spring-boot-starter-data-redis` | Spring Boot 3.2.5 管理 | Redis session/idempotency |
| `spring-boot-starter-test` | Spring Boot 3.2.5 管理 | 后端测试 |
| `mybatis-plus-boot-starter` | 3.5.14 | ORM/Mapper |
| `mybatis-spring` | 3.0.4 | Spring Boot 3 兼容集成 |
| `mysql-connector-j` | 8.0.33 | MySQL JDBC 驱动 |
| `druid-spring-boot-3-starter` | 1.2.23 | Druid 数据源 |
| `httpclient5` | 5.3 | API 测试请求执行 |
| `selenium-java` | 4.18.1 | UI 自动化测试 |
| `allure-java-commons` | 2.30.0 | Allure 报告核心 |
| `allure-generator` | 2.30.0 | HTML 报告生成 |
| `allure-model` | 2.30.0 | Allure 数据模型 |
| `langchain4j-open-ai` | 1.3.0 | OpenAI-compatible AI model client |
| `lombok` | optional / annotationProcessor 1.18.30 | Lombok 注解处理 |

## 外部 NPM 依赖

来源：`AutoTest_fronted/package.json`

| 依赖 | 版本 | 说明 |
|---|---:|---|
| `vue` | `^3.3.4` | 前端框架 |
| `element-plus` | `^2.3.12` | UI 组件库 |
| `vue-router` | `^4.2.4` | 路由 |
| `axios` | `^1.5.0` | HTTP 客户端 |
| `xlsx` | `^0.18.5` | Excel 处理 |
| `vite` | `^4.4.9` | 构建工具 |
| `@vitejs/plugin-vue` | `^4.3.4` | Vite Vue 插件 |

## 内部依赖链

### API 项目/用例管理

```text
Projects.vue / UseCases.vue
  -> ProjectController / UseCaseController
  -> ProjectServiceImpl / UseCaseServiceImpl
  -> ProjectMapper / UseCaseMapper
  -> MySQL: project / use_case
```

### API 单次执行

```text
ApiTest.vue
  -> POST /api/test
  -> API_TestController
  -> ApiTestServiceImpl.run()
  -> HttpClient5
  -> 目标 API
  -> 断言处理(JSON/字符串/正则)
  -> ApiResponseVO
```

### API 批量执行与报告

```text
BatchExecute.vue
  -> POST /api/execute
  -> BatchTestController.batchExecute()
  -> @Idempotent: Redis setIfAbsent(uri + args hash)
  -> @ApiTest: TestReportAspect around result
  -> ApiTestServiceImpl.batchExecute()
  -> CompletableFuture + Semaphore + testExecutor
  -> ApiTestServiceImpl.run()
  -> BatchExecuteResultVO
  -> TestReportAspect extracts details
  -> TestReportService.recordCaseReport()
  -> TestCaseReportMapper
  -> MySQL: test_case_report
```

### 报告查询/导出

```text
BatchExecute.vue
  -> POST /api/report/cases
  -> ReportController.listCases()
  -> TestReportService.queryCaseReports()
  -> TestCaseReportMapper
  -> TestCaseReportVO

BatchExecute.vue
  -> POST /api/report/export
  -> ReportController.exportAllure()
  -> TestReportService.exportAllureHtmlZip()
  -> query reports -> write Allure JSON -> generate HTML -> zip
  -> ResponseEntity<byte[]>
```

### UI 用例管理

```text
UiProjects.vue / UiUseCases.vue
  -> UIProjectController / UIUseCaseController
  -> UIProjectServiceImpl / UIUseCaseServiceImpl
  -> UIProjectMapper / UIUseCaseMapper
  -> MySQL: uiproject / ui_use_cases
```

### UI 单次执行

```text
UiTest.vue
  -> POST /api/ui-test/run
  -> UiTestController.run()
  -> UiTestServiceImpl.run()
  -> WebDriverFactory
  -> Selenium WebDriver
  -> BasePage executes steps
  -> UiTestResultVO
```

### UI 批量执行

```text
UiBatchExecute.vue
  -> POST /api/ui-batch-test
  -> UIBatchTestController.BatchTest()
  -> @Idempotent
  -> UiTestServiceImpl.batchTest()
  -> load UIUseCase by id
  -> CompletableFuture + Semaphore + testExecutor
  -> UiTestServiceImpl.run()
  -> UiBatchExecuteResultVO
```

### AI Pipeline 模式

```text
AiRequirement.vue (pipeline mode)
  -> fetch /api/ai/analyze-requirement-stream
  -> AIController.analyzeRequirementStream()
  -> PipelineOrchestrator.executeFullPipelineStream()
  -> SkillLoader.resolveDocParserSkill(fileName)
  -> SkillLoader.load(doc-parser-*)
  -> AIClient.chatStream(docParser model)
  -> RequirementAnalyzer.analyzeStream()
  -> QuestionGenerator.generateStream()
  -> SseEmitter sends stage/token/done/session/error events
  -> SessionManager.create(DocContext)
  -> Redis stores context
```

### AI Q&A 与用例生成

```text
AiRequirement.vue
  -> POST /api/ai/submit-answers?sessionId=...
  -> AIController.submitAnswers()
  -> SessionManager.get(sessionId)
  -> PipelineOrchestrator.submitAnswers()
  -> QuestionGenerator.generate()
  -> SessionManager.update(sessionId)

AiRequirement.vue
  -> POST /api/ai/generate-cases?sessionId=...
  -> AIController.generateCases()
  -> PipelineOrchestrator.generateTestCases()
  -> ApiTestCaseGenerator.generate()
  -> SkillLoader.load(test-case-generator-api)
  -> AIClient.chat(testCaseGeneration model)
  -> Result<String> cases JSON
  -> Frontend POST /api/use-cases for each generated case
```

### AI Agent 模式

```text
AiRequirement.vue (Agent mode)
  -> POST /api/ai/agent-analyze
  -> AIController.agentAnalyze()
  -> TestCaseAgent.run(userInput, null)
  -> AIClient.chatWithHistory(docParser model)
  -> ReAct action JSON
  -> Tool lookup by name
  -> Tool.execute(args)
  -> observations appended to message history
  -> finish_questions or finish_cases

AiRequirement.vue
  -> POST /api/ai/agent-generate?sessionId=...
  -> TestCaseAgent.run(userInput, qaHistory)
  -> same ReAct loop
```

### AI 测试结果分析

```text
BatchExecute.vue
  -> POST /api/ai/analyze-result/{reportId}
  -> AIController.analyzeResult()
  -> PipelineOrchestrator.analyzeResult()
  -> ResultAnalyzer.analyze(reportId)
  -> TestCaseReportMapper reads request/response/assertion detail
  -> AIClient.chat(resultAnalysis model)
  -> Result<String> structured analysis
```

### Skill 加载链

```text
SkillLoader constructor
  -> resolve candidate skills roots relative to user.dir
  -> find directory containing */SKILL.md
  -> load(skillName)
  -> read SKILL.md
  -> parse YAML frontmatter: name, description, enabled
  -> cache by lastModifiedTime
```

## 配置依赖

| 配置 | 使用方 | 说明 |
|---|---|---|
| `example.ai_study_notes.datasource.*` | Spring datasource | MySQL 连接 |
| `example.ai_study_notes.redis.*` | Spring Data Redis / SessionManager / IdempotentAspect | Redis 连接 |
| `ai.ark.base-url` | `AIModelConfig` / `AIClient` | ARK endpoint |
| `ai.ark.api-key` | `AIModelConfig` / `AIClient` | AI API key；不要写入文档或提交 |
| `ai.ark.doc-parser` | 文档解析、Agent 默认模型 | 默认 `deepseek-v4-flash` |
| `ai.ark.requirement-analysis` | `RequirementAnalyzer`, `AnalyzeGapTool` | 默认 `doubao-seed-2.0-pro` |
| `ai.ark.question-generation` | `QuestionGenerator`, `AskUserTool` | 默认 `deepseek-v4-flash` |
| `ai.ark.test-case-generation` | `ApiTestCaseGenerator`, `GenerateCaseTool` | 默认 `doubao-seed-code` |
| `ai.ark.result-analysis` | `ResultAnalyzer` | 默认 `deepseek-v4-pro` |
| `UI_conf/config.properties` | `WebDriverFactory` | 浏览器驱动路径 |
