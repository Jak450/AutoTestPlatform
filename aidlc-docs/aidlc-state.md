# AI-DLC Workflow State

## Project Information

- **Project Name**: AutoTestPlatform / 自动化测试平台
- **Project Type**: brownfield
- **Workspace Root**: `D:/桌面/AutoTestPlatform`
- **Backend Module**: `backed/AI_Study_Notes`
- **Frontend Module**: `AutoTest_fronted`
- **Started**: 2026-05-25T21:13:00+08:00
- **Last Updated**: 2026-08-10T18:00:00+08:00
- **Last Sync Scope**: Agent 模块全量开发 + 前端重设计 + 评测体系建立后的全面刷新

## Current Status

- **Current Phase**: Agent 模块开发完成 + 前端重设计完成 + 评测体系建立
- **Current Stage**: 稳定可用；等待用户继续提需求/调整
- **Status**: 开发主体完成，未推送提交 5 个，本地可运行

## 2026-08-10 大版本更新（新窗口必读）

本日基于 `docs/agent-design/` 五份设计文档完成了大量开发，涵盖：

1. **Agent 模块（`backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/`）**
   - P0：JWT 认证、多会话、SSE 流式对话、AgentLoop、工具系统、确认流程、前端登录 + Agent 页
   - P1：文件上传解析、用例生成/校验/保存、模板、记忆、压缩、中间件、技能
   - P2：工具/技能管理 API、审计、契约校验、部署文件
   - A/B 修复轮：批量执行报告落库、附件清理、生产部署参数、流式重试/取消、大文档预算、
     契约同步、自动压缩接线、记忆相关性检索 + 自动提炼、中间件补齐、技能 Redis 持久化、
     SSE 事件 Redis 化、审计与 token 计量、管理端页面
2. **用例草稿试跑**：`trial_run_cases`（真实执行草稿，支持随机抽样 + 多轮重跑 + flaky 标记）
3. **Agent 评测脚手架**：`agent-eval/`（任务集 + 多轮 runner + report.md，当前基线 94%）
4. **前端全站重设计（AutoTest·Blueprint 浅色蓝图）**：设计 Token、顶部导航、登录页、
   Agent 三栏页、8 个数据页换肤、系统管理页（详见 `aidlc-docs/agent-module.md` 与
   `docs/superpowers/specs/2026-08-10-frontend-redesign-design.md`）

### 重要已知信息

- 当前分支 `codex/agent`，**已 push 一次**（远端存在 `origin/codex/agent`），
  本地另有 **5 个未推送提交**（`c60ea86`、`5d2e0f9`、`110a669`、`95a82e3`、`695b8e0`）
- 本地开发环境：后端 8080（dev profile + `DEEPSEEK_API_KEY` 环境变量）、前端 5173、MySQL 3306、Redis 6379
- 默认账号 `admin / 12345678`（启动时自动创建，可用 `ADMIN_INIT_PASSWORD` 覆盖）
- Agent 模型层为自研 OpenAI 兼容客户端（支持 DeepSeek thinking 的 reasoning_content 回传）

## Stage Progress

### Inception Phase
- [x] Workspace Detection
- [x] Reverse Engineering
- [x] Requirements Analysis (historical AI-DLC conversation)
- [x] Workflow Planning
- [x] Application Design
- [x] Units Generation
- [x] Reverse Engineering Refresh (2026-06-29)

### Construction Phase
- [x] Unit 1: Idempotency Infrastructure
- [x] Unit 2: Skill System & Document Parsing
- [x] Unit 3: Pipeline Orchestrator & Agents
- [x] Unit 4: AI Controller & Q&A Interaction
- [x] Unit 5: AI Result Analysis
- [x] Unit 6: UI Test Case Generation Extension Point
- [x] Unit 7: Streaming Pipeline Mode
- [x] Unit 8: ReAct Test Case Agent Mode
- [ ] Build and Test (manual/project testing ongoing)

### Operations Phase
- [ ] Operations (placeholder)

## Fast Onboarding Summary

This project is a front-end/back-end separated automated testing platform with three domains:

1. **API testing**: API projects, API use cases, single execution, batch concurrent execution, assertions, report persistence, Allure export.
2. **UI testing**: UI projects, UI use cases, Selenium execution, batch UI execution, step/assertion model.
3. **AI-assisted test generation**: requirement upload, skill-based parsing, requirement gap analysis, clarification Q&A, API test case generation, AI result analysis, and optional ReAct Agent mode.

## Current Architecture Snapshot

### Backend (`backed/AI_Study_Notes`)

```text
org.example.ai_study_notes
├── controller/ApiTest              # API project/use-case/test/batch/report REST APIs
├── controller/UITest               # UI project/use-case/test/batch REST APIs
├── service + service/Impl          # API/UI execution and CRUD business logic
├── mapper                          # MyBatis-Plus data access
├── Pojo                            # DTO/entity/VO/Result models
├── Aop                             # @ApiTest report aspect + @Idempotent duplicate-submit aspect
├── POM                             # Selenium WebDriver factory + BasePage operations
├── aiservice                       # AI pipeline, agent, tools, skill loader, ARK client
├── config                          # async executor, Redis, web static mapping, Bloom filter config
├── handler                         # global exception handler
├── Task                            # scheduled report cleanup
└── utils                           # JSON/content type/report filename/property helpers
```

### AI Module (`aiservice/`)

```text
aiservice/
├── AIController.java               # /api/ai endpoints, SSE stream, Agent endpoints
├── SessionManager.java             # Redis-backed DocContext session storage
├── client/
│   ├── AIModelConfig.java          # ai.ark.* model/base-url/api-key config
│   └── AIClient.java               # LangChain4j OpenAI-compatible client + streaming model cache
├── orchestrator/
│   └── PipelineOrchestrator.java   # pipeline mode: parse -> analyze -> questions -> generate/analyze result
├── agent/
│   ├── RequirementAnalyzer.java
│   ├── QuestionGenerator.java
│   ├── ResultAnalyzer.java
│   ├── TestCaseAgent.java          # ReAct loop with Tool implementations
│   └── tool/                       # parse_document/analyze_gap/ask_user/generate_case/validate_case
├── generator/
│   ├── ApiTestCaseGenerator.java
│   └── UiTestCaseGenerator.java    # extension point / placeholder
└── skill/
    └── SkillLoader.java            # loads skills/*/SKILL.md frontmatter directly; no register.json required
```

### Frontend (`AutoTest_fronted`)

```text
src/
├── App.vue                         # Element Plus admin layout + sidebar groups
├── main.js                         # axios baseURL=/api
├── router/index.js                 # 9 routes
└── views/
    ├── AiRequirement.vue           # upload requirements, pipeline stream mode, Agent mode, Q&A, save cases
    ├── Projects.vue                # API project CRUD
    ├── UseCases.vue                # API use-case CRUD
    ├── ApiTest.vue                 # single API request execution
    ├── BatchExecute.vue            # API batch execution, report query/export, AI result analysis
    ├── UiTest.vue                  # UI testing workbench
    ├── UiProjects.vue              # UI project CRUD
    ├── UiUseCases.vue              # UI use-case CRUD
    └── UiBatchExecute.vue          # UI batch execution
```

## Key API Endpoints

| Area | Method | Path | Purpose |
|---|---:|---|---|
| API projects | GET/POST/PUT/DELETE | `/api/projects` | API project CRUD |
| API cases | GET/POST/PUT/DELETE | `/api/use-cases` | API use-case CRUD |
| API run | POST | `/api/test` | Single API execution |
| API batch | POST | `/api/execute` | Batch API execution with `@Idempotent` and `@ApiTest` |
| Reports | POST | `/api/report/cases` | Query persisted test reports |
| Reports | POST | `/api/report/export` | Export Allure HTML ZIP |
| UI projects | GET/POST/PUT/DELETE | `/api/ui-projects` | UI project CRUD |
| UI cases | GET/POST/PUT/DELETE | `/api/ui-use-cases` | UI use-case CRUD |
| UI run | POST | `/api/ui-test/run` | Single UI execution |
| UI batch | POST | `/api/ui-batch-test` | Batch UI execution with `@Idempotent` |
| AI pipeline | POST | `/api/ai/analyze-requirement` | Non-streaming requirement analysis |
| AI pipeline | POST | `/api/ai/analyze-requirement-stream` | SSE-style streaming requirement analysis |
| AI pipeline | POST | `/api/ai/submit-answers` | Submit clarification answers |
| AI pipeline | POST | `/api/ai/generate-cases` | Generate API test cases |
| AI result | POST | `/api/ai/analyze-result/{reportId}` | Analyze persisted report |
| AI agent | POST | `/api/ai/agent-analyze` | ReAct Agent initial analysis |
| AI agent | POST | `/api/ai/agent-generate` | ReAct Agent answer submission/generation |

## Technology Stack Snapshot

| Layer | Current Stack |
|---|---|
| Frontend | Vue 3.3.4, Vite 4.4.9, Element Plus 2.3.12, Vue Router 4.2.4, Axios 1.5.0, xlsx 0.18.5 |
| Backend | Java 17, Spring Boot 3.2.5, MyBatis-Plus 3.5.14, MyBatis Spring 3.0.4, Druid 1.2.23 |
| Test engines | Apache HttpClient5 5.3 for API tests, Selenium 4.18.1 for UI tests |
| Reports | Allure Java Commons/Generator/Model 2.30.0, Allure Maven Plugin 2.11.2 |
| AI | LangChain4j OpenAI-compatible client 1.3.0, Volcano Engine ARK coding API |
| Storage | MySQL database `app_test`, Redis DB 0 |

## Important Changes Since Older AI-DLC Docs

- Workspace root is now `D:/桌面/AutoTestPlatform`; older `D:/桌面/测试平台` references are stale.
- Backend is Spring Boot **3.2.5** with `jakarta.servlet`; older Spring Boot 2.6.13 notes are stale.
- `AIClient` now uses **LangChain4j** `OpenAiChatModel` / `OpenAiStreamingChatModel` with model caches.
- AI requirement flow now has two modes: pipeline streaming mode and ReAct Agent mode.
- New AI endpoints exist: `/analyze-requirement-stream`, `/agent-analyze`, `/agent-generate`.
- `SkillLoader` no longer depends on `skills/register.json`; it reads `skills/*/SKILL.md` frontmatter directly. No `register.json` is present now.
- Idempotency no longer uses a token endpoint. It hashes request URI + method args in Redis and currently deletes the key after return/exception, so it mostly blocks concurrent duplicate submissions rather than post-success repeat clicks.
- Frontend `AiRequirement.vue` is no longer only a simple three-step page; it includes stream output, pipeline/agent mode selection, Q&A, generated case preview, and import into use-case management.
- `App.vue` sidebar includes the AI menu, but `titleMap` still lacks `/ai-requirement`, so the header title falls back to `自动化测试平台` on that route.
- `application-dev.yml` contains local development values and an ARK key placeholder/protected secret mechanism is documented under `scripts/README.md`; do not expose secrets in AI-DLC docs.

## Data Model Snapshot

Primary schema is in `backed/init.sql`:

| Table | Purpose |
|---|---|
| `project` | API test project |
| `use_case` | API test case |
| `test_case_report` | Persisted API execution report + Allure JSON + request/response/assertion detail |
| `uiproject` | UI test project |
| `ui_use_cases` | UI test case with JSON step list |

## Run / Verify Commands

Run these from the module directories:

```powershell
# Backend
mvn test
mvn spring-boot:run

# Frontend
npm run build
npm run dev
```

There is no separate lint/typecheck script in `AutoTest_fronted/package.json`.

## Next Steps for New Session

1. 先读本文件，再读 `aidlc-docs/agent-module.md`（Agent 模块实现说明，含最新 A/B 修复、试跑、评测、前端重设计）。
2. Agent 业务代码在 `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/`；
   旧 AI 模块（`aiservice/`）仍在，`AiRequirement.vue` 用的就是它（已切到 DeepSeek 配置）。
3. 前端重设计：`AutoTest_fronted/src/styles/*`（Token/全局/Element 换肤）、
   `components/layout/TopNav.vue`、`components/ui/*`、`components/agent/*`、`views/Agent.vue`。
4. 评测：`node agent-eval/run-eval.mjs --rounds N`（前置：后端已启动且配置 DEEPSEEK_API_KEY）。
5. 已知边界/待办（C 组准备项）：
   - GitHub Actions Secrets 需补 `DEEPSEEK_API_KEY`、`JWT_SECRET`（≥32 字符）；`deploy.yml` 已接参数
   - 生产库需执行 `backed/init.sql` 的 Agent 段（9 张 `agent_*` 表）
   - 多实例部署：附件为本地磁盘，需对象存储/共享卷；SSE 事件已 Redis 化
   - 模型在"生成→试跑"长流程上仍偶发不稳（多轮评测可量化），可继续调 prompt 或工具链
6. 安全约定：不提交 `.secrets.local`、`.env`、真实密钥、本地日志；DEEPSEEK/JWT 密钥只走环境变量。
