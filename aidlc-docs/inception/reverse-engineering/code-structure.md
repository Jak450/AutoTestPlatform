# 代码结构文档 - 自动化测试平台

## 仓库根目录

```text
AutoTestPlatform/
├── AutoTest_fronted/                  # Vue 3 + Vite 前端
├── backed/
│   ├── AI_Study_Notes/                # Spring Boot 后端 Maven 项目
│   ├── init.sql                       # 当前核心数据库 DDL
│   └── logs/                          # 本地运行日志（不要提交）
├── skills/                            # 本项目 AI skill 目录
├── scripts/                           # secrets filter / pre-commit scan 脚本
├── aidlc-docs/                        # AI-DLC 项目知识和逆向文档
├── sample-requirement.md              # AI 需求分析测试样例
├── .env.example                       # 环境变量样例，不含真实密钥
├── .secrets.template                  # secrets filter 模板
├── .secrets.local                     # 本地真实密钥（gitignored，不要提交）
├── .gitattributes                     # secrets filter 路径配置
└── README.md
```

## 前端结构 (`AutoTest_fronted`)

```text
AutoTest_fronted/
├── package.json                       # npm scripts 和依赖
├── package-lock.json
├── vite.config.js                     # Vite 配置，/api -> localhost:8080
├── index.html
└── src/
    ├── main.js                        # Vue app、Element Plus、router、axios baseURL=/api
    ├── App.vue                        # 管理后台布局、侧边栏、标题
    ├── router/
    │   └── index.js                   # 9 条路由，设置 document.title
    └── views/
        ├── AiRequirement.vue          # AI 需求分析：管道流式/Agent/Q&A/生成/导入
        ├── Projects.vue               # API 项目管理
        ├── UseCases.vue               # API 用例管理
        ├── ApiTest.vue                # 单 API 执行
        ├── BatchExecute.vue           # API 批量执行、报告、Allure 导出、AI 分析
        ├── UiTest.vue                 # UI 测试工作台
        ├── UiProjects.vue             # UI 项目管理
        ├── UiUseCases.vue             # UI 用例管理
        └── UiBatchExecute.vue         # UI 批量执行
```

## 后端结构 (`backed/AI_Study_Notes`)

```text
backed/AI_Study_Notes/
├── pom.xml                            # Java 17 + Spring Boot 3.2.5 Maven 配置
├── lib/selenium-server-4.18.1.jar     # 本地 Selenium 相关 jar
├── elk/                               # ELK/Filebeat 试验配置
└── src/
    ├── main/
    │   ├── java/org/example/ai_study_notes/
    │   │   ├── AiStudyNotesApplication.java
    │   │   ├── Aop/
    │   │   │   ├── IdempotentAspect.java
    │   │   │   ├── TestReportAspect.java
    │   │   │   └── anno/
    │   │   │       ├── ApiTest.java
    │   │   │       └── Idempotent.java
    │   │   ├── POM/
    │   │   │   ├── driver/WebDriverFactory.java
    │   │   │   └── page/BasePage.java
    │   │   ├── Pojo/
    │   │   │   ├── Result.java
    │   │   │   ├── dto/
    │   │   │   ├── entity/
    │   │   │   └── vo/
    │   │   ├── Task/CleanTestReportCaseTask.java
    │   │   ├── aiservice/
    │   │   ├── config/
    │   │   ├── controller/
    │   │   │   ├── ApiTest/
    │   │   │   └── UITest/
    │   │   ├── handler/GlobalExceptionHandler.java
    │   │   ├── mapper/
    │   │   ├── service/
    │   │   │   └── Impl/
    │   │   └── utils/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── UI_conf/config.properties
    └── test/
```

## AI 后端结构 (`aiservice`)

```text
aiservice/
├── AIController.java
├── SessionManager.java
├── agent/
│   ├── QuestionGenerator.java
│   ├── RequirementAnalyzer.java
│   ├── ResultAnalyzer.java
│   ├── TestCaseAgent.java
│   └── tool/
│       ├── AnalyzeGapTool.java
│       ├── AskUserTool.java
│       ├── GenerateCaseTool.java
│       ├── ParseDocumentTool.java
│       ├── Tool.java
│       └── ValidateCaseTool.java
├── client/
│   ├── AIClient.java
│   └── AIModelConfig.java
├── context/DocContext.java
├── generator/
│   ├── ApiTestCaseGenerator.java
│   ├── TestCaseGenerator.java
│   └── UiTestCaseGenerator.java
├── orchestrator/PipelineOrchestrator.java
└── skill/SkillLoader.java
```

## Skill 结构 (`skills`)

```text
skills/
├── doc-parser-markdown/SKILL.md
├── doc-parser-pdf/SKILL.md
├── doc-parser-word/SKILL.md
├── test-case-generator-api/SKILL.md
├── test-case-generator-ui/SKILL.md
└── test-result-analyzer/SKILL.md
```

当前不存在 `skills/register.json`。`SkillLoader` 会扫描候选 `skills` 根目录，并读取每个 `SKILL.md` 的 YAML frontmatter。

## 数据库结构

DDL 入口：`backed/init.sql`

| 表名 | 说明 | 关键字段 |
|---|---|---|
| `project` | API 项目 | `id`, `name` |
| `use_case` | API 用例 | `pid`, `name`, `url`, `method`, `header`, `param`, `assert_str`, `description` |
| `test_case_report` | API 执行报告 | `case_id`, `case_name`, `module_name`, request/response/assertion/allure/time fields |
| `uiproject` | UI 项目 | `name`, `description`, timestamps |
| `ui_use_cases` | UI 用例 | `project_id`, `url`, `browser`, `viewport`, `headless`, `timeout`, `steps` JSON |

## 配置文件

| 文件 | 作用 |
|---|---|
| `application.yml` | 公共 Spring 配置，激活 `dev` profile，拼接 datasource/redis 配置 |
| `application-dev.yml` | 本地 MySQL/Redis/AI/日志/任务配置；包含敏感值时不要写入文档或提交 |
| `application-prod.yml` | 生产 profile 配置 |
| `UI_conf/config.properties` | ChromeDriver/GeckoDriver/Firefox/Edge 路径 |
| `.env.example` | 环境变量示例 |
| `.secrets.template` / `.secrets.local` | secrets filter 使用的模板/本地真实值 |
| `scripts/README.md` | secrets 管理说明 |

## 构建与运行入口

```powershell
# Backend, from backed/AI_Study_Notes
mvn test
mvn spring-boot:run

# Frontend, from AutoTest_fronted
npm run build
npm run dev
```
