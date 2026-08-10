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
- 事件缓冲在 agent_end 后清空；run 极快完成时迟到的 SSE 连接靠前端 syncRunState 拉取详情兜底
- 中间件 ReadBeforeWrite 为简化版（写前需本会话最近有查询标记）
- Agent 模型层为自研 OpenAI 兼容客户端（Apache HttpClient 直接调 /chat/completions），
  支持 DeepSeek thinking 模式 reasoning_content 回传；LangChain4j 仅保留给旧 AI 模块与单轮生成
- 会话内已加载的技能状态（SkillService）为内存态，重启后需重新 load_skill

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

## 9. 验证

```powershell
# 后端
mvn test          # 14 个测试通过（契约/Schema/JWT/上下文加载）
# 前端
npm install
npm run build
```

运行前提：MySQL（导入 `backed/init.sql`）、Redis、`DEEPSEEK_API_KEY` 环境变量；测试默认账号 admin/12345678。
