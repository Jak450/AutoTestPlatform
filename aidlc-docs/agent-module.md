# Agent 模块（可交互测试 Agent）实现说明

> 状态：P0 + P1 + P2 主体已完成
> 依据：`docs/agent-design/` 下五份设计文档

## 1. 新增数据库表

见 `backed/init.sql`（追加了 `agent_*` 表）：

| 表 | 用途 |
|---|---|
| `agent_user` | 用户（BCrypt 密码、user/admin 角色） |
| `agent_conversation` | 会话（按 user_id 隔离，保留 7 天） |
| `agent_message` | 会话消息（追加式，seq 保证顺序） |
| `agent_attachment` | 附件（P1 使用） |
| `agent_confirmation` | 工具执行确认记录 |
| `agent_case_template` | 用例模板（P1 使用） |
| `agent_memory` | 长期记忆（P1 使用） |
| `agent_tool_registry` | 工具注册表（P2 管理使用） |
| `agent_audit_log` | 审计日志（P2 使用） |

## 2. 后端代码结构

`backed/AI_Study_Notes/src/main/java/org/example/ai_study_notes/agent/`

```text
api/             ConversationController（会话/消息/SSE/取消/压缩）
                 ConfirmationController（批准/拒绝 -> 执行 -> 续跑）
auth/            用户、JWT 工具、认证过滤器、登录接口
config/          AgentProperties、AgentConfig（过滤器注册/线程池）
contract/        契约常量与枚举（与 agent-contracts.json 对应）
core/            AgentAiClient(DeepSeek)、AgentLoop、RunRegistry、SystemPromptBuilder
context/         ContextAssembler（内部消息 -> LLM 消息）
event/           ConversationEventStream、EventStreamService（SSE 缓冲+广播）
session/         会话/消息实体、Mapper、Service、7 天清理任务
confirmation/    确认实体、Mapper、ConfirmationService（pending 状态）
tool/            ToolDefinition/Registry/SchemaValidator/ToolExecutionService
tool/tools/      16 个平台工具（查询/执行/写入）
```

## 3. API 清单

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 JWT + 用户信息（唯一免认证接口） |
| POST | `/api/auth/logout` | 登出（token 加入 Redis 黑名单） |
| GET | `/api/auth/me` | 当前用户 |
| GET/POST | `/api/auth/users` | 用户列表/新增（admin） |

除登录外，所有 `/api/*` 均需 `Authorization: Bearer <token>`；SSE 可用 `?token=` 查询参数。

### 会话与消息

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/agent/conversations` | 新建会话 |
| GET | `/api/agent/conversations` | 会话列表 |
| GET | `/api/agent/conversations/{id}` | 会话详情（含消息） |
| DELETE | `/api/agent/conversations/{id}` | 删除会话 |
| POST | `/api/agent/conversations/{id}/messages` | 发送消息，202 + 异步 run |
| GET | `/api/agent/conversations/{id}/stream?lastEventId=` | SSE 事件流（支持 Last-Event-ID） |
| POST | `/api/agent/conversations/{id}/cancel` | 取消当前 run |
| POST | `/api/agent/conversations/{id}/compact` | 手动压缩（P1 完善） |
| POST | `/api/agent/conversations/{id}/confirmations/{cid}` | 批准/拒绝工具确认 |

## 4. SSE 事件协议

事件名为契约中的 `agent_start / turn_start / message_start / message_update / message_end / tool_execution_start / tool_execution_end / turn_end / agent_end / heartbeat`，失败额外发 `error`。

约定：

- 每个事件 `data` 内含单调递增 `id`，前端按 id 去重。
- run 结束（`agent_end`）后服务端清空事件缓冲，避免重放重复。
- 心跳 15 秒一次；断线重连由浏览器 `Last-Event-ID` 自动续传。
- 写/执行工具返回 `awaiting_confirmation`，前端渲染确认卡片，批准后后端执行工具并续跑 Agent 汇总。

## 5. 工具清单（16 个）

查询（自动放行）：`list_projects`、`list_use_cases`、`get_use_case`、`list_ui_use_cases`、`query_reports`

执行（需确认）：`run_api_test`、`run_batch_api_test`、`run_ui_test`、`run_batch_ui_test`

写入（需确认）：`create_project`、`update_project`、`delete_project`、`create_use_case`、`update_use_case`、`delete_use_case`、`create_ui_use_case`

工具统一走 `ToolExecutionService` 管线：注册检查 -> JSON Schema 校验 -> 权限门禁 -> 执行 -> 结构化 `ToolResultMeta`。

## 6. 配置与环境变量

`application-dev.yml` / `application-prod.yml` 新增 `agent.*`：

```yaml
agent:
  deepseek:
    base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
    api-key: ${DEEPSEEK_API_KEY:}
    model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
  jwt:
    secret: ${JWT_SECRET:...}   # 生产环境必须设置，>= 32 字节
  data-dir: ${AGENT_DATA_DIR:./agent-data}
  loop:
    max-turns: 20
    timeout-seconds: 300
```

默认管理员 `admin` 在启动时自动创建，初始密码 `agent.auth.admin-password`（默认 `12345678`，可用 `ADMIN_INIT_PASSWORD` 覆盖）。

## 7. 前端

- `Login.vue`：登录页（/login）
- `Agent.vue`：AI Agent 对话页（/agent）——左侧会话列表，右侧消息流 + 工具卡片 + 确认卡片 + 停止按钮
- `main.js`：axios 请求附加 token，401 统一跳登录
- `router/index.js`：登录守卫
- `AiRequirement.vue`：流式 fetch 补充 Authorization 头

## 8. 已实现 / 未实现

已实现：

- P0：AUTH-1、SESSION-1、STREAM-1、LOOP-1、TOOL-1、QUERY-1、EXEC-1（含确认）、UI-1、UI-2/4/5 主体
- P1：
  - FILE-1：附件上传/列表/删除/解析（md/txt/pdf/doc/docx，10MB 限制，本地磁盘）
  - GEN-1：parse_document / generate_cases / validate_cases / save_cases，草稿 case_preview 展示后确认入库
  - TEMPLATE-1：模板 CRUD + 会话激活模板注入生成上下文
  - MEMORY-1：记忆 CRUD + 覆盖确认 + Top-N 注入系统提示词
  - COMPACT-1：LLM 分层压缩 + 摘要注入 + 手动 /compact
  - MIDDLE-1：中间件链（输入清洗/输出预算/读写前置/循环检测/Guardrail），顺序测试钉住
  - SKILL-1：SKILL.md 注册、list/load/unload、依赖工具激活、正文注入
- P2：
  - OBS-1：agent_audit_log 记录 run 生命周期与确认动作
  - ADMIN-1：/api/agent/admin/tools 启停工具（admin），持久化到 agent_tool_registry
  - CONTRACT-1：契约 JSON 落入 resources + ContractResourceTest 双向校验
  - DEPLOY-1：deploy/Dockerfile、nginx SSE 配置、start-agent.sh

已知限制（后续按需调整）：

- 流式输出为逐 token（SSE stream=true + 增量 message_update），思考内容（reasoning_content）仅用于回传，未在前端展示
- 确认后同一批多个工具调用仅保留首个待确认调用
- 事件缓冲已 Redis 化（`agent:events:{conversationId}`，TTL 7 天），run 结束后清空；
  迟到的连接靠前端 syncRunState 拉取详情兜底
- 中间件 ReadBeforeWrite 为简化版（写前需本会话最近有查询标记）
- Agent 模型层为自研 OpenAI 兼容客户端（Apache HttpClient 直接调 /chat/completions），
  支持 DeepSeek thinking 模式 reasoning_content 回传；LangChain4j 仅保留给旧 AI 模块与单轮生成
- 技能加载状态已持久化到 Redis（`agent:conv:{conversationId}:skills`），重启不丢

### 记忆存储位置

| 内容 | 存储 |
|---|---|
| 对话消息 | MySQL `agent_message` |
| 会话摘要 | MySQL `agent_conversation.context_summary` |
| 长期记忆 | MySQL `agent_memory` |
| 附件文件/解析文本 | 磁盘 `{AGENT_DATA_DIR}/agent/files/{userId}/{conversationId}/` |
| 附件元数据 | MySQL `agent_attachment` |
| 确认记录 | MySQL `agent_confirmation` |
| 激活的用例模板 | Redis `agent:conv:{conversationId}:activeTemplate`（TTL 7 天） |
| 待确认的工具调用 | Redis `agent:confirm:{confirmationId}:pending`（TTL 10 分钟） |

## 9. A/B 修复与增强（2026-08-10）

- A1 批量执行报告落库：`AgentReportRecorder` 显式补录 `test_case_report`
- A2 会话删除/过期清理附件（记录 + 磁盘文件）
- A3 生产部署参数：`deploy.yml` 传 `DEEPSEEK_API_KEY/JWT_SECRET/ADMIN_INIT_PASSWORD`，创建 `agent-data`
- A4 流式重试只在发送阶段；流中途失败不重发
- A5 工具输出预算覆盖 Map 类型（大文档截断）
- A6 流式请求可取消（sendAsync + 轮询，取消立即中断）
- A7 前端确认卡 handled 状态在刷新后保留
- A8 旧 AI 模块（`ai.ark.*`）切到 DeepSeek 配置
- A9 契约 JSON 与代码枚举严格双向校验（补 `error`/`awaiting_confirmation`）
- B1 自动压缩接线（增量 ≥40 条才重复压缩）
- B2 记忆相关性 Top-N 注入 + 自动提炼候选（未确认，资源面板一键确认）
- B3 中间件补齐：ToolResultSanitization / TerminalResponse（空回复重试）/ TokenBudget（可配置）
- B4 技能热重载 + 激活状态 Redis 持久化
- B5 可观测性：工具审计、token 计量（agent_end 事件带 tokens）、SSE 响应头 `X-Trace-Id`、事件 Redis 化
- B6 管理端：工具/技能启停 + 全量模板管理 API，前端 `views/Admin.vue`（admin）
- B8 流式"思考中…"指示

## 10. 用例草稿试跑与评测

- `trial_run_cases` 工具：对草稿真实执行（不保存），支持 `repeat`（每条 N 次）与 `sampleSize`（随机抽样），
  输出 `usableCases / flakyCases / executableRuns / assertPassedRuns` 分类报告；执行需用户确认
- 评测脚手架 `agent-eval/`：
  - `tasks.jsonl`：9 个真实任务（查询/报告/记忆/模板/写入确认/文档生成/执行确认/试跑链路）
  - `run-eval.mjs --rounds N`：多轮跑真实链路，输出按任务通过率分布与失败明细（支持 `EVAL_ONLY` 过滤）
  - `report.md`：评测报告；当前基线 94%（9 任务 × 2 轮 = 18 轮 17/18）
- 常见回归兜底：改完 Agent 后跑 `node agent-eval/run-eval.mjs --rounds 2`

## 11. 前端重设计（AutoTest·Blueprint 浅色蓝图）

- 设计文档：`docs/superpowers/specs/2026-08-10-frontend-redesign-design.md`
- 实现计划：`docs/superpowers/plans/2026-08-10-frontend-redesign.md`
- 设计 Token/全局样式/Element 换肤：`AutoTest_fronted/src/styles/`
- 自建组件：`components/ui/*`（Logo/StatusStamp/MethodBadge/StatCard/PageHeader/EmptyState）、
  `components/layout/TopNav.vue`（含移动端抽屉）、`components/agent/*`（会话列表/消息/资源面板）
- 页面：登录页、Agent 三栏页（`views/Agent.vue`）、项目卡片式、用例/批量/报告/AI 需求分析换肤、`views/Admin.vue`
- 注意：Agent.vue 的 SSE/上传/确认等逻辑全部保留并组件化；工具卡片不展示原始参数 JSON

## 12. Git 与运行状态

- 分支 `codex/agent`：已 push 一次（`origin/codex/agent` 存在）；本地另有 5 个未推送提交
- 本地运行：后端 8080（dev + `DEEPSEEK_API_KEY`）、前端 5173、MySQL 3306、Redis 6379
- 后端测试当前 18 个全部通过；前端 `npm run build` 通过
- 评测任务偶发不稳定项：`trial_run_generated` 曾在单轮出现生成后不试跑（模型随机性），多轮评测可见

## 13. 验证

```powershell
# 后端
mvn test          # 18 个测试通过（契约/Schema/JWT/上下文加载/中间件顺序/草稿试跑）
# 前端
npm install
npm run build
# Agent 评测（可选）
node agent-eval/run-eval.mjs --rounds 2
```

运行前提：MySQL（导入 `backed/init.sql`）、Redis、`DEEPSEEK_API_KEY` 环境变量；测试默认账号 admin/12345678。

## 14. 私有测试知识库（MD 文件存储，2026-08-10 新增）

> 定位：把会话中沉淀的测试经验/知识结论提炼为"私有测试知识"，按用户隔离，以 MD 文件存储，
> 提问测试知识时自动检索注入。存储与维护对人友好（可直接编辑、git 管理），不依赖数据库。

### 目录结构

```text
{data-dir}/knowledge/{userId}/
├── _candidates/                # 自动提炼、待用户确认（confirmed: false）
└── {category}/{slug}.md        # 已确认知识（confirmed: true）
```

每个 MD 文件带 YAML frontmatter（与 SKILL.md 同风格）：

```markdown
---
title: 登录接口超时经验
category: 经验教训
tags: [登录, 超时, 重试]
confirmed: true
updated: 2026-08-10T21:30:08
---

正文（Markdown）…
```

### 工具（4 个，已注册进 Agent）

| 工具 | 权限 | 说明 |
|---|---|---|
| `save_knowledge` | 需确认 | 按标题/分类保存或覆盖知识文档（覆盖需 overwrite=true） |
| `search_knowledge` | 自动放行 | 按关键词检索标题/标签/内容，返回 Top-N 片段 |
| `list_knowledge` | 自动放行 | 列出全部或指定分类的知识文档 |
| `delete_knowledge` | 需确认 | 删除知识文档 |

### 检索注入

每个 turn 用最近用户消息做关键词检索（复用记忆的词频打分思路），取 Top-5、总预算 2KB，
以"私有测试知识（已确认）"小节注入系统提示词；系统规则第 9 条要求回答知识/经验问题时优先引用。

### 自动提炼与入库

- `MemoryExtractor` 两类提炼均**自动直接入库、无需用户确认**：
  - `preference`（偏好/约定）→ `agent_memory`（`confirmed: 1`，同名跳过）
  - `knowledge`（知识/经验）→ MD 知识库正式分类目录
- 偏好**去重与合并**：提炼提示词会携带已有偏好清单（最多 10 条），由模型对每条偏好输出
  `action`：`create`（新主题）/ `merge`（同主题，`targetKey` 指定合并进已有 key，内容追加 + 版本+1）/
  `skip`（重复，不入库）——避免同主题偏好反复创建新 key 造成冗余
- 提炼阈值：历史 ≥60 字符即触发（偏好往往一句话说清，阈值过高会漏提炼）
- 自动入库有质量门控与**去重/合并决策**：提炼提示词只允许"用户明确陈述、具体可复用、不臆造"的经验，
  宁可少提炼不要错提炼；提炼时把已有知识库（标题+摘要，最多 10 条）一并给模型，
  由模型对每条新知识输出 `action`：
  - `create`：新主题 → 新建文档；
  - `append`：与已有文档同主题 → 追加为新小节（`targetTitle` 指定目标），避免知识越堆越多；
  - `skip`：与已有内容重复 → 不入库。
  词法相似度（字符二元组）作为兜底：≥0.65 视为重复跳过；`findByTitle` 用于定位 append 目标。
- `save_knowledge` 工具权限改为 `auto_write`（**自动执行、不再弹确认卡**），
  `delete_knowledge` 仍为需确认；系统规则第 10 条要求 Agent 日常交流不主动调用 save_knowledge
- 前端资源面板"知识"分组**按分类分组展示**（含每类计数），支持查看全文；
  `_candidates` 候选机制保留用于兼容/手动场景（`POST /api/agent/knowledge/{slug}/confirm` 仍可用）
- 管理 API：`GET /api/agent/knowledge`（列表/候选/详情）、`DELETE /api/agent/knowledge`（删除）

### 已知修复

- `agent_memory.tags` 列是 JSON 类型，代码原先写入普通字符串（如 `auto`）导致提炼入库报错；
  已改为写入合法 JSON 数组（`["auto"]`），并让提炼循环单条失败不中断其余条目

## 15. 工具调用幂等与重传（2026-08-10 新增）

> 背景：LLM 调用断连/前端重传时，写类工具可能被重复执行（如重复建项目、重复保存）。
> 通过 `agent_tool_execution` 表按 (conversation_id, tool_name, payload_hash) 唯一记录，保证同参数写操作只执行一次。

### 表结构（`backed/init.sql` + 线上库已建）

```text
agent_tool_execution(
  id, conversation_id, tool_call_id, tool_name, payload_hash,
  status(running/success/failed), result(JSON), created_at, updated_at)
UNIQUE KEY (conversation_id, tool_name, payload_hash)
```

### 幂等语义（ToolExecutionService）

- 只对**写类工具**（`CONFIRM_WRITE` / `AUTO_WRITE`）生效；查询、执行测试类工具不参与（有意重跑不受影响）
- 首次执行：插入 `running` 记录 → 执行 → 更新 `success/failed` + 结果 JSON
- 重传同参数：唯一键冲突 → 读已有记录：
  - `success` 且 10 分钟内 → **重放上次结果**（不重复执行，消息标注"幂等重放"）
  - `running` → 返回"正在执行中"，阻止并发重复
  - `failed` / 过期成功 → 重置 `running` 允许重试
- `save_cases` 批量入库加 `@Transactional(rollbackFor=Exception.class)`，单批原子

### 过度调用修复（系统提示词规则 8）

- 用户只要求"分析/解读/总结"文档 → 仅 `list_files + parse_document`，**不生成用例**
- 用户明确要求生成用例 → 才走 generate_cases →（要求试跑时）trial_run_cases → save_cases
- 未明确要求生成时绝不主动调用 generate_cases / trial_run_cases / save_cases

### 测试

`ToolExecutionIdempotencyTest`：同参数只执行一次并重放、失败可重试、执行中拦截、读工具不记录。

## 16. 任务计划（MD 清单，2026-08-10 新增）

> 复杂/多步任务执行前先建立任务清单（MD 存储 + 状态字段），执行过程中逐步勾选，
> 每轮注入当前清单，保证长任务不跑偏、可断点续做、对用户可见。

### 存储

```text
{data-dir}/tasks/{userId}/{taskId}.md
```

frontmatter：`task_id / title / status / source_conversation / updated`，
正文为可勾选清单（`- [ ]` / `- [x]`）。状态：`pending / in_progress / done / blocked / failed`。

### 工具（4 个，Agent 工具总数 41）

| 工具 | 权限 | 说明 |
|---|---|---|
| `create_task_plan` | 自动执行 | 建任务清单（title + items） |
| `update_task_plan` | 自动执行 | 更新状态 + 勾选已完成步骤（doneItems 按文本匹配） |
| `get_task_plan` | 自动放行 | 查看详情 |
| `list_task_plans` | 自动放行 | 列表（可按会话过滤） |

### 机制

- 系统规则 8/11：多步任务先 `create_task_plan`，每完成一步 `update_task_plan`；单步请求不建计划
- 每轮把当前会话活动计划（紧凑清单）注入系统提示词"当前任务计划"小节
- `create/update_task_plan` 成功后，AgentLoop 以 `task_plan` 消息推给前端
  （稳定 messageId=`tp-{taskId}`，同一任务原地更新，不重复堆卡）
- 前端消息流渲染任务卡（标题 + 状态徽章 + 勾选清单），历史消息同样可渲染

### 测试

`TaskPlanServiceTest`：建文件/frontmatter、状态与勾选更新、活动计划注入、会话过滤、删除、路径消毒。

## 17. 管理端资源创建（2026-08-11 新增）

- 技能：`POST /api/agent/admin/skills`（admin）——按名称在 `skills/{name}/SKILL.md` 写入
  frontmatter + 正文并**热重载**；管理页"新建技能"表单对应此接口
- 模板：复用 `POST /api/agent/templates`——管理页"新建模板"表单
  （name/description/caseShape/coverageRules/assertRules/examples）
- 工具：仍为代码内新增 `ToolExecutor` 类自动注册；管理端仅启停

### 已知修复

- `agent_case_template` 的 `case_shape/coverage_rules/assert_rules/examples` 原为 JSON 列，
  与自由文本语义不符导致创建模板报错；已改为 TEXT（init.sql + 线上库 ALTER 同步）

### 迁移

`KnowledgeMigration`（启动时执行一次，按用户以标记文件防重跑）会把已确认的 DB 记忆
（`agent_memory`，confirmed=1）导出为 MD 知识文档，分类 `记忆迁移`。

### 注意点

- 写入用"临时文件 + 原子 move"，避免写到一半损坏
- 目录/文件名分段做消毒（禁止 `..`、`/`、`\` 等），防止目录穿越
- `ReadBeforeWriteMiddleware` 对 `save/delete_knowledge` 豁免（知识内容自包含，无需先查平台数据）
