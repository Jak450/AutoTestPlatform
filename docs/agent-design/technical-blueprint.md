# AutoTestPlatform Agent 技术实现蓝图

> 状态：综合三份参考设计后的技术实现蓝图（v0.1 草稿）
>
> 依据：
> - `doc/agent-design-reference.md`（重点参考，20 章教学设计）
> - `doc/pi-agent-design-blueprint.md`（pi / pi-agent 架构提炼）
> - `doc/TEST_AGENT_REFERENCE_DESIGN.md`（DeerFlow 测试 Agent 规范）

本文档的目标是让另一个开发 Agent 在没有交互的情况下，仅凭本文档完成可交互测试 Agent 的实现。

可直接实现的规格见：[implementation-spec.md](implementation-spec.md)、[sql/agent_schema.sql](sql/agent_schema.sql)、[contracts/agent-contracts.json](contracts/agent-contracts.json)

## 1. 参考文档与借鉴结论

| 参考来源 | 核心价值 | 本项目中采用 |
| --- | --- | --- |
| agent-design-reference.md | ReAct 主循环、工具双表、Hooks、权限、技能、上下文压缩、错误恢复、后台任务、MCP | 主循环模型、Hooks 拦截语义、工具 Schema、压缩管线、技能注册、权限分层 |
| pi-agent-design-blueprint.md | 四层架构、双消息模型、流式事件、turn/follow-up 循环、状态 Agent、Session 树 | 分层边界、内部消息与 LLM 消息分离、事件流、会话持久化、工具执行管线 |
| TEST_AGENT_REFERENCE_DESIGN.md | harness/app 分离、工具结果元数据、中间件链、RunManager/SSE、配置热重载、安全与契约 | 中间件顺序、结构化工具元数据、运行生命周期、错误分类、契约文件、密钥治理 |

### 借鉴优先级（时间有限时按此顺序）

1. P0：双消息模型与 `convertToLlm` 转换边界
2. P0：流式事件协议（增量事件 + 错误进流，不抛异常）
3. P0：双层主循环（turn 内层 + follow-up 外层）
4. P0：工具系统（Schema 校验、执行管线、并行/串行、截断保护）
5. P1：Hooks 管道（首个非 None 即拦截）
6. P1：上下文压缩（主动分层压缩 + 应急压缩）
7. P1：会话持久化（追加式存储 + 压缩边界）
8. P2：中间件链、工具结果元数据、RunManager/SSE、安全治理

## 2. 目标架构

### 2.1 四层模型

```text
L4 应用层（Web UI / REST / SSE）
  └─ 只负责 I/O、渲染、用户确认交互
L3 领域层（测试平台 Agent）
  ├─ 平台工具：项目/用例/执行/报告/文件/模板
  ├─ 系统提示词与技能资源
  └─ 业务编排：用例生成、结果汇总
L2 框架层（通用 Agent 运行时，可复用）
  ├─ AgentLoop（turn 内层 + follow-up 外层）
  ├─ Agent（有状态门面 + 事件总线）
  ├─ Harness（turn 快照 + hooks + 持久化编排）
  ├─ SessionStore（会话存储）
  └─ ToolRegistry / Middleware / Compaction
L1 模型层（统一 LLM 适配）
  └─ Provider、流解析、认证、重试、token 估算
```

### 2.2 边界规则

1. L4 永不直接调用 L1，只能调用 L2/L3 公开 API。
2. L1 的 `stream()` 永不 throw，所有失败编码为流内 `error` 事件。
3. L2 不知道具体工具语义，只负责循环、事件、队列、校验、持久化。
4. L3 不接触 Provider 细节，只注册工具与提示词。
5. 跨层协议（消息类型、工具元数据、事件类型）必须集中定义并版本化。

### 2.3 框架选型（LangChain4j）

已确认技术栈：后端继续 Java/Spring Boot，前端保持 Vue + Element Plus。

推荐使用 LangChain4j 承担 L1 模型层：

- 支持 DeepSeek 官方 OpenAI 兼容 API（`OpenAiChatModel` / `OpenAiStreamingChatModel`）。
- 提供流式、工具调用、消息转换与请求级重试基础能力。
- 只把 LangChain4j 用于模型接入，不把本文档定义的 AgentLoop、Hooks、会话、中间件塞进框架。

使用边界：

1. LangChain4j 只负责模型调用、流式解析、工具参数绑定、请求级重试。
2. AgentLoop、Hooks、SessionStore、ToolRegistry、Middleware 按本文档自研，保证行为可测试、可替换。
3. 不在 L3/L4 暴露 LangChain4j 消息类型，保持双消息模型边界。
4. 构建时需联网拉取 `langchain4j-open-ai`（本机 Maven 缓存当前无该依赖）。

替代方案：Spring AI 也可选，但当前版本的工具调用与 Agent 编排能力较弱，暂不作为首选。

## 3. 核心运行时设计

### 3.1 双消息模型

内部消息（AgentMessage）允许业务自定义类型（文件解析记录、执行结果、模板引用等）；LLM 只认识 `user / assistant / toolResult` 三种角色。

规则：

1. 每次 LLM 调用前执行 `convertToLlm(messages)`，把内部消息翻译成 LLM 消息。
2. `convertToLlm` 不得 throw；无法翻译的 UI 专用消息必须过滤。
3. 可选 `transformContext(messages)` 在转换前做裁剪/注入，出错时返回原数组。
4. 自定义消息翻译为 `user` 消息时用 XML/受保护标签包裹，例如 `<summary>`、`<file-analysis>`。
5. 消息对象更新采用原位替换，避免内部引用、持久化、事件三处不同步。

### 3.2 Agent 主循环

采用双层循环：

- 内层：执行工具调用直至没有工具调用（turn 循环）。
- 外层：agent 本应停止时检查 follow-up 队列，有则继续。
- steering 队列：当前 turn 所有工具执行完后注入，用于打断/纠正。

终止语义：

| 条件 | 行为 |
| --- | --- |
| 无工具调用且无 follow-up | 正常结束 |
| `stopReason=length`（截断） | 不执行任何工具，全部标记失败并提示重试 |
| `stopReason=error/aborted` | 完整发出终止事件序列后结束 |
| 达到最大轮数/预算 | 记录 `stopReason`，正常收尾 |

### 3.3 流式事件协议

统一事件流，前端通过 SSE 消费：

```text
agent_start
  turn_start
    message_start
    message_update*（text/thinking/toolcall 增量）
    message_end
    tool_execution_start / update* / end*
    toolResult message_start / message_end
  turn_end
  ...
agent_end
```

事件必须包含：

- `partial` 正在构建的 assistant 消息（浅拷贝给订阅者）
- `done` / `error` 终端事件
- 单调递增事件 id，SSE 使用 `Last-Event-ID` 支持断线重连

错误处理不变量：

1. `stream()` 不抛异常。
2. 失败编码为 `error` 事件 + 带 `errorMessage` 的最终消息。
3. UI 永远收到完整的终止序列（`message_end → turn_end → agent_end`）。
4. 订阅者被 await，是持久化/渲染的屏障。

### 3.4 Hooks 管道

事件点与拦截语义：

| 事件 | 时机 | 返回值语义 |
| --- | --- | --- |
| `UserPromptSubmit` | 用户输入后、循环前 | 非 None = 替换用户输入 |
| `PreToolUse` | 工具执行前 | 非 None = 拒绝执行，返回值作为 tool_result |
| `PostToolUse` | 工具执行后 | 忽略（可做日志/告警） |
| `Stop` | 循环结束 | 忽略 |
| `context` | LLM 调用前 | 替换消息列表 |
| `tool_call` | 工具预检 | `{ block, reason }` 拦截 |
| `tool_result` | 工具结果生成后 | 局部改写结果 |
| `session_before_compact` | 压缩前 | `{ cancel, compaction }` |

规则：

1. `trigger_hooks` 按注册顺序执行，首个非 None 返回值即拦截，后续 hook 不再执行。
2. 注册顺序 = 优先级，权限 hook 必须最先注册。
3. hook 不得 throw，出错时返回安全默认值。
4. 工具执行与权限/日志/监控解耦，agent loop 不感知 hook 细节。

### 3.5 会话状态

每个会话独立保存：

- 对话历史（消息树或追加式日志）
- 上传文件与解析结果
- 问答记录
- 生成的用例草稿
- 当前激活的用例模板
- 模型/工具配置快照

存储建议：

- 优先追加式 JSONL：一条会话一个文件，条目含 `id / parentId / type / timestamp`。
- 支持压缩边界：读取路径从 leaf 向上走到最近 compaction 条目为止。
- 分支/回退能力（Session 树）作为增强项，不阻塞 MVP。
- 并发追加必须串行化（按会话 key 加锁或 promise 链）。

### 3.6 上下文压缩

采用主动分层压缩，在 LLM 调用前执行：

1. 大输出持久化：超过阈值的 tool_result 写入文件，消息中保留预览和引用。
2. 中间消息截断：保留头部与尾部，截断点必须在完整的 tool_use/tool_result 对之间。
3. 旧工具结果压缩：仅保留最近 N 条完整内容，更早的替换为占位符。
4. LLM 摘要兜底：压缩前存档 transcript，生成结构化摘要：
   `目标 / 约束 / 进度 / 关键决策 / 下一步 / 关键上下文`。
5. 应急压缩：收到 context overflow 错误后，保留最近 5 条并摘要其余历史。

规则：

- 压缩点不允许落在 toolResult 上。
- 摘要作为受保护数据块注入（hidden user 消息），不作为 system 指令。
- 摘要后消息尾部不能以孤立 assistant tool-call 开头。
- 手动 `/compact` 复用同一压缩管线。

### 3.7 错误恢复与重试

错误分三层处理：

| 层级 | 触发 | 行为 |
| --- | --- | --- |
| 请求级 | HTTP 408/409/429/5xx、网络错误 | 指数退避 + 抖动重试，遵守 `retry-after`，连续 529 自动切换备用模型 |
| 消息级 | 流成功后内容为错误（限流/过载/截断） | 按 `errorMessage` 分类，可重试则重新发起 |
| 工具级 | 工具执行抛异常 | 转为 `ToolResultMeta(status=error)`，不让 agent 崩溃 |

异常来源总览：

| 异常来源 | 措施 | 用户可见结果 |
| --- | --- | --- |
| LLM API/网络错误 | 指数退避重试、切换备用模型、封顶次数 | 重试或错误消息 |
| 流式响应失败 | 转流内 `error` 事件 + `errorMessage` | 前端显示错误并收到完整终止序列 |
| 上下文溢出 | 应急压缩一次后重试 | 恢复运行 |
| `max_tokens` 截断 | 升级 token 预算/continuation | 继续生成 |
| 工具参数非法 | Schema 校验拦截，返回结构化 error | 工具失败卡片 |
| 工具执行异常 | 捕获并转 `ToolResultMeta(status=error)` | 工具失败卡片 + 建议 |
| 权限拒绝 | Guardrail 返回 deny 结果 | 提示无权限或需要确认 |
| 循环/停滞 | LoopDetection/ToolProgress 硬停 | 正常收尾，`stopReason` 标记 |
| token 预算触顶 | TokenBudget 硬停，不抛异常 | 汇总已有结果后收尾 |
| 用户取消 | cancel -> abort -> 终止事件序列 | 前端收到终止事件 |
| 持久化失败 | 记录审计，run 标记 error | 错误提示 |
| 前端断线 | 心跳 + `Last-Event-ID` 重连 | 断点续传 |

非异常路径：

- `stopReason=max_tokens`：先升级 token 预算重试一次，仍截断则保存输出并注入 continuation。
- context overflow：应急压缩一次后重试，不允许无限压缩循环。
- loop/token 预算触顶：设置加性字段 `stopReason`（如 `token_capped`、`loop_capped`），正常收尾。

不变量：

1. LLM 流永不 throw，失败编码为流内 error 事件。
2. 每次 run 都发出完整终止序列（`message_end -> turn_end -> agent_end`）。
3. 重试有次数与预算上限，不无限重试。
4. 恢复状态（重试次数、是否已压缩、当前模型）随 run 生命周期保存。

对应参考文档的 Error Recovery：`RecoveryState`（重试次数、是否已升级 token、连续 529 计数、是否已应急压缩、当前模型）在本设计中作为 run 级恢复状态；`with_retry` 对应请求级重试层，`reactive_compact` 对应 context overflow 应急压缩。

## 4. 工具系统设计

### 4.1 工具定义

每个工具提供：

```text
name           工具名（平台内唯一）
label          UI 显示名
description    给 LLM 看的用途说明
inputSchema    参数 JSON Schema（唯一事实源）
executionMode  sequential | parallel
permissions     read | confirm_write | confirm_execute | deny
category        查询/执行/写入/文件/生成/模板/记忆/系统
activeByDefault 是否默认进入工具池
version         工具契约版本
execute        实际执行函数
terminate      是否建议提前结束（仅当全部工具返回 true 才生效）
```

工具定义与 handler 分离：

- 注册表 A：给 LLM 的工具 Schema。
- 注册表 B：工具名到执行函数的映射。
- 统一入口分发，未注册工具返回 `Unknown tool` 错误结果。

### 4.2 执行管线

固定顺序：

1. 工具调用出现
2. 参数归一化与 Schema 校验
3. `PreToolUse` / `tool_call` hook（权限、拦截）
4. 执行（支持并行，预检串行、执行并发、结果按源顺序落盘）
5. `PostToolUse` / `tool_result` hook
6. 构造结构化 ToolResult

保护规则：

- `stopReason=length` 的 toolCall 参数可能不完整，一律不执行。
- 工具抛异常转为 `status=error` 的结构化结果，不让 agent 崩溃。
- 并行工具结果事件按完成序发出，落盘顺序必须与模型看到的顺序一致。
- 任一工具声明 sequential 时，整批退化为串行。

### 4.3 工具结果元数据

每个工具结果附结构化元数据，禁止上层解析文本来判断状态：

```text
status: success | error | partial_success
error_type: auth | rate_limited | transient | config | permission
            | no_results | not_found | internal | unknown
recoverable_by_model: boolean
recommended_next_action: continue | rewrite_query
                        | try_alternative | summarize | stop
source: exception | tool_return | content_analysis
```

测试执行专属分类：

| 场景 | error_type | recoverable | next_action |
| --- | --- | --- | --- |
| 全部通过 | success | - | continue |
| 断言失败 | `test_failed` | true | rewrite_query |
| 用例报错 | `test_error` | true | try_alternative |
| 超时 | `test_timeout` | false | try_alternative |
| 重跑通过（flaky） | `test_flaky` | true | summarize |
| 报告/文件不存在 | `not_found` | true | rewrite_query |

异常路径的元数据优先级高于工具自返回标记。

### 4.4 平台工具契约

查询类（默认只读，可自动执行）：

| 工具 | 对应接口 |
| --- | --- |
| `list_projects` | GET `/api/projects` |
| `list_use_cases` | GET `/api/use-cases?pid=` |
| `get_use_case` | GET `/api/use-cases/{id}` |
| `list_ui_use_cases` | GET `/api/ui-use-cases?pid=` |
| `query_reports` | POST `/api/report/cases` |

执行类（高风险，需要用户确认）：

| 工具 | 对应接口 |
| --- | --- |
| `run_api_test` | POST `/api/test` |
| `run_batch_api_test` | POST `/api/execute` |
| `run_ui_test` | POST `/api/ui-test/run` |
| `run_batch_ui_test` | POST `/api/ui-batch-test` |

写入类（生成/修改，需要预览确认）：

| 工具 | 对应接口 |
| --- | --- |
| `create_project` | POST `/api/projects` |
| `update_project` | PUT `/api/projects/{id}` |
| `create_use_case` | POST `/api/use-cases` |
| `update_use_case` | PUT `/api/use-cases/{id}` |
| `delete_use_case` | DELETE `/api/use-cases/{id}` |
| `create_ui_use_case` | POST `/api/ui-use-cases` |

文件与生成类（本平台新增，不直接复用旧 AI 代码）：

| 工具 | 说明 |
| --- | --- |
| `upload_file` / `list_files` / `read_file_content` | 会话附件管理 |
| `parse_document` | 解析需求文档，输出结构化需求 |
| `generate_cases` | 根据需求 + 模板生成用例草稿 |
| `validate_cases` | 校验草稿 schema 与业务规则 |
| `save_cases` | 用户确认后批量写入用例 |
| `list_templates` / `save_template` / `load_template` | 用例模板管理 |

### 4.5 用户确认流程

写操作与执行操作采用“预览后确认”：

```text
Agent 生成动作预览
  -> 前端展示确认卡片（字段/影响范围/风险）
  -> 用户确认或修改
  -> Agent 调用写入/执行工具
```

确认状态保存在会话上下文中，作为工具执行的“授权令牌”，避免无交互模式下误执行。

### 4.6 注册与生命周期

使用统一 `ToolRegistry` 管理工具生命周期：

```text
注册 -> 启用 -> 可用（进入工具池）
         -> 禁用（从工具池移除，注册表保留）
         -> 注销（从注册表删除）
```

规则：

1. 内置工具启动时注册，动态工具（插件/MCP）运行时可注册。
2. 工具名全局唯一；重名注册必须 fail-fast，不允许静默覆盖。
3. 注册/启用/禁用状态持久化，重启后恢复。
4. 配置/契约文件变更采用内容签名检测，支持热重建工具池。
5. 注册失败的工具进入 `failed` 状态并记录原因，不影响其他工具。

### 4.7 动态工具池与命名空间

每轮 LLM 调用前从注册表组装“激活工具列表”，而不是全量暴露：

| 激活来源 | 说明 |
| --- | --- |
| 默认激活 | `activeByDefault=true` 的核心工具 |
| 会话激活 | 当前会话明确选择的工具 |
| 技能依赖 | 加载技能时自动激活其依赖工具 |
| 用户显式激活 | 用户通过对话或界面启用的工具 |

命名空间规范：

```text
builtin__{name}          平台内置工具
skill__{skill}__{tool}   技能提供的工具
mcp__{server}__{tool}    MCP 外部工具
```

规则：

1. 工具名中的非法字符归一化为下划线。
2. 激活工具列表保存在会话状态，模型换上下文后仍保持一致。
3. 未激活工具不进 LLM 工具列表，减少 token 与误调用。
4. 技能加载时绑定其依赖工具集，技能卸载后工具回到禁用状态。

### 4.8 工具契约、管理与可观测

工具契约文件（JSON）是唯一事实源：

```text
tools_contract.json
  - name / description / inputSchema / version
  - permissions / category / activeByDefault
  - 参数校验规则
```

管理能力（增强期实现）：

- 工具列表与状态查看
- 启用/禁用工具
- 更新工具契约
- 调用审计与健康指标（成功率、平均耗时、错误率、调用次数）

可观测要求：

- 每次调用记录 `traceId / sessionId / toolName / 参数摘要 / 结果摘要 / 耗时 / status / error_type`。
- 写与执行类工具必须关联用户确认记录。
- 后端实现与契约文件双向校验，测试钉住。

## 5. 中间件链

测试 Agent 最小中间件集合（顺序为强约束）：

| 顺序 | 中间件 | 职责 |
| --- | --- | --- |
| 1 | InputSanitization | 清洗用户输入中的注入标签，保留原文溯源 |
| 2 | ToolOutputBudget | 工具结果设置字节上限并注明截断 |
| 3 | ToolResultSanitization | 只对不可信来源结果中和框架标签 |
| 4 | ReadBeforeWrite | 写操作前必须存在同路径 read 标记且 hash 一致 |
| 5 | ToolProgress | 连续无新信息时 WARNED/BLOCKED |
| 6 | ToolErrorHandling | 异常转结构化 error 结果并打元数据 |
| 7 | LoopDetection | 检测重复调用模式并硬停 |
| 8 | TokenBudget | 每 run token 预算，硬停不抛异常 |
| 9 | TerminalResponse | 空终态回复重试一次，再失败则记 error |
| 10 | Guardrail | 工具执行前鉴权，deny 返回错误结果 |

实现纪律：

- 中间件通过集中构造函数组装，不允许散落注册。
- 用断言/测试钉住关键顺序。
- after 阶段按注册逆序分发。
- 中间件互不读取对方内部状态。

## 6. 文件、技能、用例模板与执行上下文

### 6.1 文件

- 文件绑定会话，支持上传、列表、删除。
- 支持格式：Markdown、PDF、Word、txt；单文件上限 10MB。
- 文件存储：本地磁盘 `{data_dir}/agent/files/{user_id}/{conversation_id}/`，路径可配置。
- 单实例部署时本地目录即可；多实例部署时改为共享存储或对象存储。
- 文本类文件提取内容进入会话上下文；非文本文件记录元数据并提示不可直接阅读。

### 6.2 技能加载与注册

技能是“提示词资源 + 依赖工具集”的组合，技能本身不直接执行代码，可执行能力由工具提供。

文件约定：

```text
skills/{skill-name}/SKILL.md
  YAML frontmatter:
    name / description / version / enabled
    tools[]              依赖工具（加载时自动激活）
    requires_confirmation 是否涉及写/执行操作
  body: 技能正文
  references/: 可选参考文件
```

注册与加载：

1. 启动时扫描技能目录，解析 frontmatter 构建技能注册表。
2. 技能状态：`registered / enabled / disabled / failed`。
3. frontmatter 解析失败时降级为普通正文，不阻断其他技能。
4. 热重载使用内容签名（路径 + mtime + size + sha256）检测变化。
5. `list_skills()` 只返回名称与描述，不注入全量正文。
6. `load_skill(name)` 注入技能正文并激活声明工具。
7. 技能卸载后恢复默认工具池。

与工具/上下文的关系：

- 技能目录写入 system prompt，提示“相关时调用 load_skill”。
- 技能正文按需注入，默认预算 2KB，可配置。
- 技能声明的 `tools[]` 加入 active tools，权限仍由 Guardrail 校验。
- 技能正文视为不可信外部资源，注入前做转义/中和。

流程：

```text
用户/模型选择技能
  -> list_skills 查看技能目录
  -> load_skill(name)
  -> 激活依赖工具 + 注入技能正文
  -> 任务完成/用户关闭
  -> 卸载技能，恢复默认工具池
```

MVP 范围：只做技能目录、加载/卸载与依赖工具激活，不做技能市场与版本管理；增强期增加技能配置界面、版本与多用户授权。

### 6.3 用例模板

模板用于约束后续用例生成，包含：

```text
name            模板名称
description     适用场景
caseShape       用例字段规则（必填/可选/类型/格式）
coverageRules   正常/异常/边界覆盖策略
assertRules     断言规范
examples        示例用例
```

流程：加载模板 → 注入生成上下文 → 生成草稿 → 校验 → 预览 → 保存。

### 6.4 执行上下文

一次生成/执行任务的上下文包含：

- 用户意图与原始请求
- 会话内文件解析结果
- 当前用例模板
- 已选项目/用例
- 最近的执行结果与失败分类
- 用户确认记录

## 7. 记忆系统设计

### 7.1 记忆分层

| 层级 | 范围 | 内容 | 存储 | 生命周期 |
| --- | --- | --- | --- | --- |
| 会话工作记忆 | 当前会话 | 消息、文件解析结果、QA 记录、激活模板、确认令牌、当前项目/用例选择 | 会话存储（Redis/DB） | 随会话存在，随压缩精简 |
| 会话摘要 | 单会话 | 压缩摘要：目标、约束、进度、关键决策、下一步、关键上下文 | 会话记录中的 summary 字段/JSONL | 跨压缩保留，随会话销毁 |
| 长期记忆 | 用户级 | 偏好、命名/断言约定、常用默认值、已确认的生成约定 | `agent_memory` 表或用户记忆文件 | 跨会话持久 |

### 7.2 长期记忆数据模型

```text
id
user_id
scope              user | global
namespace          preference | convention | decision | reference
key                唯一键，例如 "default_project_id"、"assertion.style"
content_md         记忆正文（Markdown/结构化文本）
tags[]             检索标签
confidence         high | medium | low
confirmed          boolean（是否经用户确认）
source_session_id  来源会话（审计用）
version            覆盖版本
created_at / updated_at
```

约束：

1. 同一 `key` 的唯一版本是“用户最近确认的值”。
2. 单条记忆正文建议限制 4KB，单次注入预算默认 2KB。
3. 不存密钥、不存数据库快照；项目/用例/报告数据一律实时查询。
4. 引用平台数据时只存 ID/名称引用，不缓存完整内容。

### 7.3 读写工具

| 工具 | 行为 |
| --- | --- |
| `list_memory(query)` | 列出/搜索当前用户记忆 |
| `save_memory(key, content, tags, overwrite)` | 写入记忆；覆盖已有 key 需要用户确认 |
| `forget_memory(key)` | 删除记忆，需要用户确认 |

写入策略：

- MVP 以 Agent 显式调用工具写入为主。
- 覆盖/删除必须经用户确认，并记录来源会话与时间。
- 后续增强：turn 结束后由系统提炼候选记忆，经用户确认后落库。

### 7.4 注入策略

每轮 LLM 调用前动态组装：

```text
固定 System Prompt（身份、规则、工具说明）
  + 当前会话状态（项目/模板/文件摘要）
  + 匹配到的长期记忆 Top-N（默认预算 2KB）
  + 已加载模板内容（按需）
```

规则：

1. 记忆是数据不是指令，注入时使用受保护的数据块（`<memory>` 或 hidden user 消息），不进 system 指令区。
2. 外部文本进入记忆前做转义/中和，防止伪造标签。
3. 压缩后的会话摘要以 protected data block 注入，保证模型不“失忆”。
4. 未匹配到记忆时注入空段，不强制填充。

### 7.5 更新与失效

- 同 key 覆盖时记录版本与来源，最新确认值胜出。
- 用户显式覆盖/删除立即生效。
- 平台数据变化不依赖记忆，引用 ID 失效时重新查询。
- 会话摘要不会自动升级为长期记忆，除非用户或 Agent 显式保存。
- 记忆删除/覆盖进入审计日志。

### 7.6 参考映射

- `agent-design-reference.md` 第 9 章：采用其“显式工具写入 + 注入预算”的思想，升级为多用户、结构化、可审计。
- `pi-agent-design-blueprint.md`：会话历史与摘要按 Session 树/追加式日志存储，压缩边界决定读路径。
- `TEST_AGENT_REFERENCE_DESIGN.md`：摘要与记忆作为隐藏受保护数据块注入，不混入 system 指令。

## 8. 上下文管理

### 8.1 上下文分层

每轮进入模型的上下文按来源分层，避免混为一谈：

| 层 | 内容 | 注入方式 |
| --- | --- | --- |
| 固定系统提示 | 身份、平台规则、安全边界、回复格式 | SystemMessage，静态 |
| 动态系统提示 | 当前时间、会话信息、可用工具清单、技能目录 | SystemMessage，每轮重建 |
| 会话状态 | 当前项目、激活模板、文件摘要、确认令牌、用户选择 | 数据块 |
| 对话历史 | 用户/助手/工具消息 | LLM 消息 |
| 工具结果 | 查询/执行/生成结果，含结构化元数据 | toolResult，受预算约束 |
| 长期记忆 | 匹配到的 Top-N 用户记忆 | 受保护数据块 |
| 任务上下文 | 需求解析结果、用例草稿、执行结果台账 | 受保护数据块 |
| 平台数据 | 项目/用例/报告实时查询结果 | 不进常驻上下文，按轮注入 |

### 8.2 每轮组装管线

```text
用户输入 / 外部事件
  -> transformContext（裁剪、注入、清洗）
  -> convertToLlm（内部消息翻译为 LLM 消息）
  -> prepare_context（预算检查与分层压缩）
  -> 组装 system prompt + 数据块
  -> LLM 调用
  -> 工具执行
  -> 工具结果回写
```

规则：

1. 管线顺序固定，不允许绕过 `convertToLlm` 直接拼接消息。
2. 上下文每轮重建，但 system prompt 与可缓存数据块用内容签名缓存，未变化不重复构建。
3. 不可信数据与系统规则分离注入，前者不能进入 system 指令区。
4. 工具执行上下文闭包绑定本轮快照，防止跨轮污染。

### 8.3 预算管理

| 类别 | 默认预算建议 | 说明 |
| --- | --- | --- |
| 系统提示 | 2-4K tokens | 固定规则精简，工具描述只放激活工具 |
| 历史消息 | 视模型窗口 | 超出走压缩管线 |
| 工具结果 | 单条全文 30KB、回显 2KB 预览 | 超限持久化并给引用 |
| 执行结果台账 | 最近 50 条 | 旧结果压缩为摘要 |
| 长期记忆 | 单轮 2KB | 只注入匹配 Top-N |
| 输出预留 | 16K tokens | 窗口内必须保留给模型输出 |

估算策略：

- 优先信任 Provider 返回的 usage 数据。
- 无 usage 时用字符数/4 估算 token。
- 每轮记录 token 统计，作为压缩与审计依据。

### 8.4 压缩与持久化

压缩管线见 3.6，本节补充整体衔接：

1. 压缩产物写入会话摘要（`summary_text`），跨 turn 以受保护数据块注入。
2. 压缩前存档 transcript，压缩事件记录前后 token 数、保留条数、切点。
3. 手动 `/compact` 与自动压缩共用同一管线。
4. 压缩后消息尾部不得以孤立 assistant tool-call 开头。
5. ReadBeforeWrite 的 hash 标记随消息压缩失效属预期行为，下次写前必须重新读。
6. 压缩摘要不自动升级为长期记忆，显式保存才落库。

### 8.5 检索与注入

- 长期记忆按查询相关性注入 Top-N，不注入全部。
- 模板只在生成类任务激活时注入。
- 文档解析结果：任务相关时注入完整结构，否则只注入摘要。
- 平台数据实时查询，查询结果在当轮预算内使用，不缓存到会话。
- 注入数据统一做标签中和/转义，防止伪造指令。

### 8.6 上下文安全

- 用户输入先清洗，远程内容中和框架标签。
- 密钥、敏感凭据绝不进入上下文。
- 外部字符串进入数据块前做 HTML/标签转义。
- 每轮记录上下文组成审计：消息数、token 估算、记忆注入数、压缩次数。

### 8.7 参考映射

- `agent-design-reference.md` 第 8/10 章：分层压缩管线与动态 prompt 组装。
- `pi-agent-design-blueprint.md` §2/§8：双消息模型、turn 快照、compaction 切点。
- `TEST_AGENT_REFERENCE_DESIGN.md` §7/§8：DurableContext 隐藏数据块与 RunJournal 计量。

## 9. 会话、API 与存储设计

### 9.1 核心实体

```text
User             用户：id、username、password_hash、display_name、
                 role(user/admin)、status、created_at、updated_at
Conversation     会话：id、user_id、title、status、active_tool_names、
                 context_summary、created_at、updated_at
Message          消息：id、conversation_id、role、type、content、
                 tool_meta、attachments、created_at
Attachment       附件：id、conversation_id、fileName、size、mimeType、
                 storagePath、parseStatus、parseResultRef
Confirmation     确认：id、conversation_id、toolName、payloadHash、
                 status(pending/approved/rejected)、expiresAt
CaseTemplate     模板：id、user_id、name、caseShape、coverageRules、
                 assertRules、examples
MemoryEntry      记忆：id、user_id、scope、namespace、key、content、version
```

会话隔离与保留：

- 会话按 `user_id` 隔离，所有会话/消息/文件/记忆 API 必须校验归属。
- 会话保留 7 天，过期清理。
- 历史为线性追加，不实现分支/时间旅行。

### 9.2 REST API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET/POST | `/api/agent/conversations` | 会话列表/新建 |
| GET/DELETE | `/api/agent/conversations/{id}` | 会话详情/删除 |
| POST | `/api/agent/conversations/{id}/messages` | 发送用户消息并启动 run |
| GET | `/api/agent/conversations/{id}/stream` | SSE 事件流（支持 `Last-Event-ID`） |
| POST | `/api/agent/conversations/{id}/cancel` | 取消当前 run |
| POST | `/api/agent/conversations/{id}/compact` | 手动压缩 |
| POST/GET/DELETE | `/api/agent/conversations/{id}/files` | 附件上传/列表/删除 |
| POST | `/api/agent/conversations/{id}/confirmations/{cid}` | 批准/拒绝工具确认 |
| GET/POST | `/api/agent/templates` | 模板列表/保存 |
| GET/POST/DELETE | `/api/agent/memory` | 长期记忆读写 |

### 9.3 消息协议

消息类型：

```text
text          普通文本
tool_call     模型发起的工具调用
tool_result   工具结果（含结构化元数据）
file          附件消息
confirmation  用户确认卡片
case_preview  生成的用例草稿预览
system        系统状态/错误提示
```

规则：

- 工具结果携带 `ToolResultMeta`，前端按元数据渲染，不解析文本。
- 确认消息携带 `payloadHash`，批准后作为工具执行授权。
- 所有消息可回放，持久化顺序与模型看到顺序一致。

### 9.4 存储设计

- 元数据建议 MySQL：会话、消息、确认、模板、记忆、审计表。
- 活跃 run 状态建议 Redis：stream 缓冲、取消信号、token 计量。
- 附件与大工具输出建议文件存储：`{data_dir}/users/{user_id}/conversations/{id}/`。
- 当前线性追加 JSONL 保留完整历史，不实现分支。

并发与幂等：

1. 每个会话同时只允许一个 run。
2. 用户消息带幂等键，重复提交不产生重复 run。
3. 会话写入按会话串行化，不同会话可并行。
4. 取消返回枚举（已取消/已接管/已终态/未知），不静默失败。

## 10. 前端交互规范

### 10.1 页面结构

```text
左侧：会话列表（新建/切换/删除）
中间：消息流 + 输入框 + 附件上传
右侧/抽屉：当前会话状态（激活工具、模板、文件、记忆）
```

### 10.2 消息渲染

- 普通文本：Markdown 渲染。
- 工具调用：卡片展示工具名、状态（运行中/成功/失败）、参数摘要、耗时。
- 执行结果：结构化展示状态码、断言结果、失败分类。
- 用例预览：表格展示草稿，提供“确认保存/修改后保存/取消”。
- 确认卡片：展示影响范围与风险，用户批准/拒绝后才继续。

### 10.3 SSE 连接

- 使用 `Last-Event-ID` 断线重连，从断点续传。
- 心跳事件默认 15 秒一次。
- 消息增量按事件 id 幂等去重。
- 提供取消按钮，调用 cancel API 后事件流收到终止事件。

### 10.4 文件上传

- 支持拖拽/选择，显示上传进度。
- 上传完成后展示解析状态（待解析/解析中/完成/失败）。
- 文件进入消息流，可被当前会话引用。

## 11. 安全设计

### 11.1 权限分层

| 级别 | 行为 |
| --- | --- |
| 硬拒绝 Deny | 无条件拒绝，不弹确认 |
| 软确认 Confirm | 弹窗请求用户确认（执行/写入类） |
| 自动放行 Auto | 只读查询与低风险操作 |

非交互模式（后台/定时触发）默认禁用需要确认的工具。

多用户规则：

- 用户只能访问自己的会话、文件、模板、记忆。
- 读操作（查询项目/用例/报告）自动放行。
- 写与执行操作（新增/修改/删除用例、执行测试）必须用户确认。
- 会话归属校验在 API 与工具两层同时执行。

### 11.2 输入与输出安全

- 用户输入先清洗再进入模型。
- 远程/外部内容（文档、日志）中和框架标签。
- 文件内容作为数据注入，不作为指令注入 system prompt。
- 密钥（LLM API Key、被测系统凭据）不进消息、不进日志、不进 trace。
- 工具输出进入上下文前做预算截断。

### 11.3 审计

- 记录工具调用链（工具名、参数摘要、结果摘要、耗时）。
- 写操作与执行操作必须可追溯到会话、消息、用户确认记录。
- 不记录完整密钥与敏感响应体。

### 11.4 登录与用户体系

已确认新增登录功能，MVP 采用账号密码 + JWT：

- 用户表：`id / username / password_hash / display_name / role / status / created_at / updated_at`。
- 密码使用 BCrypt 存储，最小长度 8 位。
- 登录成功返回 JWT（短期 access token），服务端可在 Redis 保存会话状态与登出黑名单。
- 角色：`user`（普通用户）、`admin`（管理工具/技能/模板启用禁用、用户管理）。
- Agent 相关接口全部要求登录认证，认证通过后 `user_id` 注入请求上下文。
- 会话、文件、记忆、模板均按 `user_id` 隔离，服务端强制过滤。

接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/login` | 账号密码登录，返回 token |
| POST | `/api/auth/logout` | 登出，token 加入黑名单 |
| GET | `/api/auth/me` | 当前用户信息 |
| GET/POST | `/api/auth/users` | 用户列表/新增（admin） |

前端：

- 登录页独立路由。
- token 存本地存储，axios 拦截器统一附加 `Authorization: Bearer <token>`。
- 收到 401 时清除 token 并跳转登录页。

认证范围（已确认）：

- 现有项目/用例/执行/报告/UI 业务接口全部要求登录认证。
- 除登录接口与静态资源外，其余请求均需携带有效 token。
- 现有业务数据表无 `user_id` 归属字段，登录只做访问控制，不按用户隔离业务数据。
- 如后续需要项目/用例数据级隔离，再为 `project`、`use_case` 增加 owner 字段并做迁移。

## 12. 配置系统

### 12.1 配置分层

```text
config.yaml        模型、预算、工具、中间件、会话、存储
extensions.json    外部服务/插件/MCP 配置
环境变量/DB        密钥、运行时覆盖
```

### 12.2 热重载

- 配置缓存使用内容签名（路径 + mtime + size + sha256）失效，不使用 mtime 比较。
- 每次请求读取配置，per-run 字段（模型、token 预算、工具列表、提示词）下一条消息即生效。
- 基础设施字段（DB、沙箱、事件存储）登记为 startup-only，重启生效。
- 配置文件带版本号，过期时告警并提供合并命令。

### 12.3 密钥与环境

- 配置值以 `$KEY` 形式引用环境变量。
- LLM：DeepSeek 官方 OpenAI 兼容 API，`baseUrl=https://api.deepseek.com`；模型 id 默认 `deepseek-v4-flash`（以实际开通为准），可用 `DEEPSEEK_MODEL` 覆盖。
- API Key：运行时环境变量 `DEEPSEEK_API_KEY`，不写入代码与文档。
- 密钥走带外上下文（`context.secrets`），不进消息、日志、trace。
- 沙箱环境默认擦除敏感变量，注入值总是胜出。

### 12.4 部署与 SSE

部署形态（默认单实例）：

- Java 后端（Spring Boot）。
- Vue 前端构建产物由 nginx 托管或由后端静态资源托管。
- MySQL + Redis + 本地文件目录。
- 部署环境挂载持久化目录保存附件与工具输出。

SSE（Server-Sent Events）说明：

- 基于普通 HTTP 的单向服务器推送协议：浏览器建立连接后，服务器持续发送事件。
- 用于对话场景推送 token 增量、工具调用进度、终止事件。
- 与 WebSocket 的区别：单向、自动重连、实现简单，足够满足 AI 回复流式展示。

nginx 反向代理要求：

- `proxy_buffering off;`
- 响应头 `X-Accel-Buffering: no`
- read timeout 设置足够长（如 300s），避免代理提前断开
- `proxy_http_version 1.1;`
- 应用层心跳（默认 15s）防止空闲连接被中间设备回收
- 客户端断线通过 `Last-Event-ID` 重连续传

## 13. 可观测性与工程质量

### 13.1 可观测性

- 每次 run 有 `runId / traceId`，跨 HTTP、LLM、工具调用、持久化传播。
- RunJournal 记录 token 用量（按模型拆分）、生命周期事件。
- 事件存储支持分页回放，`Last-Event-ID` 支持断线续传。
- 日志统一带 traceId；SSE 在响应头写 traceId。

### 13.2 契约与测试

- 工具元数据枚举、状态枚举、事件类型使用契约文件（JSON）作为唯一事实源。
- 后端枚举与契约双向校验；前端类型从契约生成或比对。
- 新信号用加性字段（如 `stopReason`），不新增破坏性枚举。
- 每个中间件顺序、hook 拦截语义、压缩切点规则都要有测试钉住。
- 强制 TDD：新功能必须带单测。

## 14. 实施路线图

### 第一阶段：MVP（能对话、能查、能执行）

1. 会话 CRUD + 消息持久化
2. Agent 主循环 + 流式响应（SSE）
3. 工具注册表 + Schema 校验
4. 查询类工具（项目、用例、报告）
5. 执行类工具（单接口、批量 API、UI）
6. 用户确认卡片
7. 文件上传与文档问答（基础）
8. 基础错误处理与 token 预算
9. 前端对话工作台（会话列表 + 消息流 + SSE）

验收：用户可创建/切换会话，问“项目下有哪些用例”，确认后执行批量测试，并看到结构化结果；断线可重连。

### 第二阶段：增强（生成、模板、压缩）

1. `parse_document` + `generate_cases` + `save_cases`
2. 用例模板保存/加载/注入
3. 上下文压缩管线
4. 中间件链（ReadBeforeWrite、LoopDetection、ToolProgress）
5. 后台任务与异步执行通知
6. 工具结果元数据完整分类
7. 配置热重载

验收：上传需求文档生成用例并可确认入库；长会话不爆上下文；卡循环与停滞可被拦截。

### 第三阶段：生产级（安全、可运维）

1. Session 树/分支能力
2. RunManager + Redis StreamBridge + 断线重连
3. 完整密钥治理
4. Tracing + 事件存储
5. 契约文件 + 双向校验
6. 多用户权限与审计
7. 多 worker 取消/接管语义

## 15. 待确认决策清单

已确认决策：

- 后端：Java/Spring Boot。
- 前端：Vue + Element Plus（技术栈不变）。
- 登录：新增账号密码 + JWT + BCrypt + Redis，角色 user/admin，Agent 接口全部要求认证。
- 认证范围：现有项目/用例/执行/报告/UI 接口全部接入登录；业务数据暂不做按用户隔离。
- Agent 框架：LangChain4j 承担模型层，运行时按本文档自研。
- LLM：DeepSeek 官方 API；模型 id 默认 `deepseek-v4-flash`（待运行时确认）。
- API Key：环境变量 `DEEPSEEK_API_KEY`，运行时填写，不落仓库。
- 多用户：会话按用户隔离；读操作自动放行，写/执行需确认。
- 文件：md/PDF/Word/txt，上限 10MB，本地磁盘存储（路径可配置）。
- 用例生成：沿用 `use_case` schema，生成后预览确认再入库；UI 用例预留。
- 模板：按 `caseShape / coverageRules / assertRules / examples` 结构。
- 执行：复用现有 service/controller，长任务后台化。
- 会话：保留 7 天，线性追加，不分支。
- 预算：不设 token/预算限额；保留轮数（20）与超时（5 分钟）安全默认。

待确认：

- DeepSeek 具体 model id 与账号可用的模型名。
- 部署方式（Docker/systemd/云主机）与 nginx 配置。
- 多实例部署时附件共享存储方案。
- 管理后台（工具/技能/模板启用禁用）是否需要。
