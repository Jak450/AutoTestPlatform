# AI-DLC Workflow State

## Project Information

- **Project Name**: 自动化测试平台 (Automated Testing Platform)
- **Project Type**: brownfield
- **Workspace Root**: D:\桌面\测试平台
- **Started**: 2026-05-25T21:13:00+08:00
- **Last Updated**: 2026-05-25T23:20:00+08:00

## Current Status

- **Current Phase**: CONSTRUCTION
- **Current Stage**: Build and Test
- **Status**: in-progress

## Stage Progress

### 🔵 INCEPTION PHASE
- [x] Workspace Detection
- [x] Reverse Engineering
- [x] Requirements Analysis (performed via AI-DLC conversation)
- [x] Workflow Planning
- [x] Application Design
- [x] Units Generation

### 🟢 CONSTRUCTION PHASE
- [x] Unit 1: Idempotency Infrastructure
- [x] Unit 2: Skill System & Document Parsing
- [x] Unit 3: Pipeline Orchestrator & Agents
- [x] Unit 4: AI Controller & Q&A Interaction
- [x] Unit 5: AI Result Analysis
- [x] Unit 6: UI Test Case Generation (placeholder)
- [ ] Build and Test (user testing in progress)

### 🟡 OPERATIONS PHASE
- [ ] Operations (placeholder)

## Executed Units of Work

| Unit | Name | Status | Key Files |
|------|------|--------|-----------|
| 1 | Idempotency | completed | `@Idempotent`, `IdempotentAspect`, `IdempotentController`, SessionManager |
| 2 | Skill System | completed | `skills/` (6 SKILL.md), `SkillLoader.java` |
| 3 | Pipeline Agents | completed | `PipelineOrchestrator`, `AIClient`, `RequirementAnalyzer`, `QuestionGenerator` |
| 4 | AI Controller | completed | `AIController`, `AiRequirement.vue`, router, sidebar |
| 5 | Result Analysis | completed | `ResultAnalyzer`, BatchExecute.vue AI button |
| 6 | UI Generator | placeholder | `UiTestCaseGenerator` (throws UnsupportedOperationException) |

## Architecture Summary

### Backend (aiservice/)
```
aiservice/
├── AIController.java              # REST: /api/ai/analyze-requirement, /submit-answers, /generate-cases, /analyze-result
├── SessionManager.java            # Redis session storage (30min TTL)
├── client/
│   ├── AIModelConfig.java         # Volcano Engine ARK config + API key
│   └── AIClient.java              # HTTP client for ARK API (OpenAI-compatible)
├── context/
│   └── DocContext.java            # Pipeline context (parsed doc, QA history, generated cases)
├── orchestrator/
│   └── PipelineOrchestrator.java  # Full pipeline orchestration
├── agent/
│   ├── RequirementAnalyzer.java   # → doubao-seed-2.0-pro
│   ├── QuestionGenerator.java     # → deepseek-v4-flash
│   └── ResultAnalyzer.java        # → deepseek-v4-pro (reads test_case_report DB)
├── generator/
│   ├── TestCaseGenerator.java     # Strategy interface
│   ├── ApiTestCaseGenerator.java  # → doubao-seed-code (generates use_case JSON)
│   └── UiTestCaseGenerator.java   # Placeholder (throws exception)
└── skill/
    └── SkillLoader.java           # Reads skills/ directory, auto-resolves path
```

### Frontend (AutoTest_fronted/)
```
src/
├── views/AiRequirement.vue         # New: 3-step AI page (upload → Q&A → generate)
├── views/BatchExecute.vue          # Updated: idempotent token + AI analysis button
├── views/UiBatchExecute.vue        # Updated: idempotent token
├── router/index.js                 # Added /ai-requirement route
├── App.vue                         # Added "AI 智能" sidebar menu
└── vite.config.js                  # Fixed: removed invalid historyApiFallback
```

### Skills (/skills/)
```
skills/
├── register.json                   # 6 skills registered (2 enabled)
├── doc-parser-markdown/SKILL.md    # Markdown requirement parser
├── doc-parser-pdf/SKILL.md         # PDF requirement parser
├── doc-parser-word/SKILL.md        # Word requirement parser
├── test-case-generator-api/SKILL.md # API test case generator ★ (enabled)
├── test-case-generator-ui/SKILL.md  # UI test case generator (disabled)
└── test-result-analyzer/SKILL.md    # Result analyzer (disabled)
```

### AI Model Assignment

| Pipeline Step | Model | Context |
|--------------|-------|---------|
| Document Parsing | deepseek-v4-flash | 1024K |
| Requirement Analysis | doubao-seed-2.0-pro | 256K |
| Question Generation | deepseek-v4-flash | 1024K |
| Test Case Generation | doubao-seed-code | 256K |
| Result Analysis | deepseek-v4-pro | 1024K |

### Key API Endpoints Added

| Method | Path | Purpose |
|--------|------|---------|
| POST | /api/ai/analyze-requirement | Upload doc → AI analysis → return questions |
| POST | /api/ai/submit-answers | Submit Q&A answers → return next questions |
| POST | /api/ai/generate-cases | Generate use_case JSON → delete session |
| POST | /api/ai/analyze-result/{id} | Analyze test report by ID |
| GET | /api/idempotent/token | Get idempotency token |

## Next Steps for New Session

1. Read `aidlc-docs/aidlc-state.md` for project overview
2. Read `aidlc-docs/inception/reverse-engineering/` for architecture details
3. Read `skills/register.json` for skill inventory
4. Issues to address:
   - User reported slow AI response (~20s)
   - Need to optimize by merging doc parsing + requirement analysis into one call
   - Or switch all to deepseek-v4-flash for speed
5. Known Bugs:
   - None reported yet (in user testing)

## Notes

AI API uses Volcano Engine ARK (ark.cn-beijing.volces.com/api/coding/v3).
API key stored in `AIModelConfig.java`.
Session uses Redis with 30min TTL.
Skills path auto-resolved from workspace root.