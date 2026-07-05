# AI-DLC Workflow State

## Project Information

- **Project Name**: AutoTestPlatform / 自动化测试平台
- **Project Type**: brownfield
- **Workspace Root**: `D:/桌面/AutoTestPlatform`
- **Backend Module**: `backed/AI_Study_Notes`
- **Frontend Module**: `AutoTest_fronted`
- **Started**: 2026-05-25T21:13:00+08:00
- **Last Updated**: 2026-06-29T09:10:10+08:00
- **Last Sync Scope**: AI-DLC documentation refreshed from current codebase

## Current Status

- **Current Phase**: Maintenance / Reverse Engineering Refresh
- **Current Stage**: Project Knowledge Sync
- **Status**: complete; ready for next development task

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

1. Read this file first for the current overview.
2. Read `aidlc-docs/inception/reverse-engineering/architecture.md` and `api-documentation.md` for details.
3. For AI changes, inspect `backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/aiservice/`.
4. For UI/UX changes, inspect `AutoTest_fronted/src/views/AiRequirement.vue`, `BatchExecute.vue`, and `App.vue`.
5. Preserve security conventions: never commit `.secrets.local`, `.env`, raw ARK keys, DB passwords, or local logs.
