# 技术栈文档 - 自动化测试平台

## 前端技术栈

来源：`AutoTest_fronted/package.json`

| 技术 | 版本 | 用途 |
|---|---:|---|
| Vue | `^3.3.4` | 前端框架 |
| Vite | `^4.4.9` | 开发服务器与构建工具 |
| Element Plus | `^2.3.12` | UI 组件库 |
| Vue Router | `^4.2.4` | SPA 路由 |
| Axios | `^1.5.0` | HTTP 客户端 |
| xlsx | `^0.18.5` | Excel 数据处理 |
| @vitejs/plugin-vue | `^4.3.4` | Vite Vue 插件 |

前端脚本：

| Script | 命令 | 说明 |
|---|---|---|
| `dev` | `vite` | 启动开发服务器 |
| `build` | `vite build` | 构建生产包 |
| `preview` | `vite preview` | 本地预览构建产物 |

## 后端技术栈

来源：`backed/AI_Study_Notes/pom.xml`

| 技术 | 版本 | 用途 |
|---|---:|---|
| Java | 17 | 后端语言与运行时 |
| Spring Boot | 3.2.5 | Web 应用框架 |
| Spring Web | managed by Spring Boot | REST API |
| Spring AOP | managed by Spring Boot | 报告和幂等切面 |
| Spring Data Redis | managed by Spring Boot | Redis session / idempotency |
| MyBatis-Plus | 3.5.14 | ORM / CRUD |
| mybatis-spring | 3.0.4 | Spring Boot 3 兼容的 MyBatis Spring 集成 |
| MySQL Connector/J | 8.0.33 | MySQL 驱动 |
| Druid | 1.2.23 | 数据库连接池 |
| Lombok | 1.18.30 | 样板代码简化 |
| Apache HttpClient5 | 5.3 | API 测试 HTTP 客户端 |
| Selenium Java | 4.18.1 | UI 自动化 |
| Allure Java Commons | 2.30.0 | Allure 报告能力 |
| Allure Generator | 2.30.0 | 生成 HTML 报告 |
| Allure Model | 2.30.0 | Allure 数据模型 |
| Allure Maven Plugin | 2.11.2 | Maven 报告插件 |
| LangChain4j OpenAI | 1.3.0 | OpenAI-compatible AI client |

## AI 技术栈

| 技术/配置 | 当前用途 |
|---|---|
| Volcano Engine ARK coding endpoint | OpenAI-compatible chat/stream endpoint |
| `AIModelConfig` | 从 `ai.ark.*` 读取 base-url、api-key、模型名 |
| `AIClient` | 创建并缓存 `OpenAiChatModel` 和 `OpenAiStreamingChatModel` |
| `deepseek-v4-flash` | 默认文档解析、问题生成、Agent ReAct 模型 |
| `doubao-seed-2.0-pro` | 默认需求分析模型 |
| `doubao-seed-code` | 默认测试用例生成模型 |
| `deepseek-v4-pro` | 默认测试结果分析模型 |
| `skills/*/SKILL.md` | 文档解析、用例生成、结果分析的提示词/规则来源 |

## 数据与基础设施

| 组件 | 当前配置/用途 |
|---|---|
| MySQL | 开发库名 `app_test`，DDL 在 `backed/init.sql` |
| Redis | 开发地址 `127.0.0.1:6379`，DB 0，用于 AI session 和幂等 |
| Selenium WebDriver | `resources/UI_conf/config.properties` 配置驱动路径 |
| Allure | 后端生成 HTML ZIP，前端下载 |
| 日志 | `logs/app.log`，rolling policy 配置在 `application-dev.yml` |
| Vite proxy | `/api` -> `http://localhost:8080` |

## 构建工具

| 工具 | 用途 |
|---|---|
| Maven | 后端构建、测试、Spring Boot 启动 |
| npm | 前端依赖管理和脚本执行 |
| Vite | 前端开发服务和生产构建 |
| Allure Maven Plugin | 报告生成辅助 |

## 安全与密钥管理

| 文件/机制 | 说明 |
|---|---|
| `.env.example` | 只保存示例值 |
| `.secrets.template` | secrets 映射模板 |
| `.secrets.local` | 本地真实 secrets，已 gitignore，不要提交 |
| `scripts/secrets-*.ps1` | git clean/smudge filter 和同步脚本 |
| `scripts/pre-commit-scan.ps1` | staged secret 扫描 |
| `.gitattributes` | 指定需要 secrets filter 的路径 |

AI-DLC 文档只记录配置项名称，不记录真实 API key、数据库密码或本地 token。

## 与旧文档差异

| 项 | 旧文档 | 当前代码 |
|---|---|---|
| 项目根目录 | `D:/桌面/测试平台` | `D:/桌面/AutoTestPlatform` |
| Spring Boot | 2.6.13 | 3.2.5 |
| Servlet API | `javax.servlet` | `jakarta.servlet` |
| Druid | 1.2.15 | 1.2.23 |
| AI client | 手写 HTTP client 描述 | LangChain4j OpenAI-compatible client |
| Skill registry | `skills/register.json` | 无 register；读取 `SKILL.md` frontmatter |
| AI 前端 | 简单三步页 | 管道流式 + Agent 模式 + Q&A + 导入用例 |
