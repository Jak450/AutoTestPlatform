# 自研 Agent 可借鉴设计蓝图（源自 pi / pi-agent 架构）

> 本文档从 `earendil-works/pi-mono` 的 `pi-agent-core` 与 `pi-coding-agent` 中提炼出**可复用的设计模式**，
> 每个模式都给出：设计意图、精确的类型定义、严格的行为契约（不变量）、以及典型时序。
> 目标：让另一名 AI（或团队成员）仅凭本文档即可在你的项目中忠实还原这些设计。
>
> 所有 TypeScript 代码片段均为可直接落地的类型定义；接口方法的行为契约务必一字不差地遵守，
> 因为它们决定了多模块协作时的正确性。

---

## 0. 文档索引

| 章节 | 模式 | 借鉴价值 |
|---|---|---|
| §1 | 分层架构与模块边界 | ★★★★★ |
| §2 | 双消息模型（AgentMessage / LLM Message） | ★★★★★ |
| §3 | 流式事件协议（EventStream + partial） | ★★★★★ |
| §4 | Agent 主循环（turn 内层 + follow-up 外层） | ★★★★★ |
| §5 | 有状态 Agent（状态机 + 事件总线 + 队列） | ★★★★★ |
| §6 | 高层 Harness（turn 快照 + hook + 持久化） | ★★★★ |
| §7 | Session 树存储（追加式分支树） | ★★★★★ |
| §8 | 上下文压缩 Compaction | ★★★★ |
| §9 | 工具系统（定义/校验/执行/可插拔操作） | ★★★★★ |
| §10 | 扩展系统（工厂 + 事件 + ctx 失效） | ★★★★ |
| §11 | 认证抽象（apiKey / OAuth 双检锁刷新） | ★★★ |
| §12 | 错误处理约定与全局不变量 | ★★★★★ |

---

## 1. 分层架构与模块边界

### 1.1 分层总览

把整个系统切成 4 层，**依赖方向自底向上**，禁止反向依赖：

```
┌──────────────────────────────────────────────────────┐
│ L4  应用层（你的产品形态）                              │
│     交互 UI / CLI / 单次执行 / RPC / SDK               │
│     只做：I/O 输入输出、命令解析、渲染                  │
├──────────────────────────────────────────────────────┤
│ L3  领域层（你的业务 Agent）                            │
│     内置工具集、系统提示词、会话管理、扩展加载          │
├──────────────────────────────────────────────────────┤
│ L2  框架层（通用 Agent 运行时，可跨项目复用）           │
│     AgentLoop（底层循环）                              │
│     Agent（有状态封装 + 事件总线）                     │
│     Harness（turn 快照 + hook + 会话/压缩编排）        │
│     Session 树存储 / ExecutionEnv（文件+进程抽象）     │
├──────────────────────────────────────────────────────┤
│ L1  模型层（统一 LLM API）                             │
│     Message/Model/Provider/Models 抽象                │
│     流式协议 / 认证 / 模型目录 / 工具参数校验          │
└──────────────────────────────────────────────────────┘
```

### 1.2 关键规则

1. **L4 永远不直接调用 L1**。UI 通过 L2/L3 的公开 API（`prompt/steer/followUp/subscribe`）驱动，不接触 Provider 细节。
2. **L1 对 L2 的承诺**：`stream()` 类方法**永不 throw**，错误编码进流。L2 据此可以放心编排，不必到处 try/catch。
3. **每一层只输出自己的事件类型**，上一层消费并（可选）翻译后向更上层转发。事件是层间唯一的运行时通信方式。
4. **配置/资源注入采用"工厂闭包"**：运行时工厂捕获进程级固定配置，按工作目录（cwd）重建可变服务。

### 1.3 分层职责表

| 层 | 负责 | 不负责 |
|---|---|---|
| L1 模型层 | 多协议适配、认证、重试、流解析、token 估算 | agent 循环、状态、持久化 |
| L2 框架层 | 循环控制、事件、队列、工具编排、会话存储、压缩 | 具体工具语义、提示词内容 |
| L3 领域层 | 内置工具、系统提示词、会话文件格式、设置 | 流协议细节 |
| L4 应用层 | 终端渲染、命令解析、进程协议 | agent 逻辑 |

---

## 2. 双消息模型（AgentMessage / LLM Message）

### 2.1 设计意图

agent 内部需要表达**三类消息之外的东西**（状态通知、bash 执行记录、压缩摘要、UI 专用消息），
但 LLM 只认识 `user / assistant / toolResult` 三种角色。硬塞会让模型困惑，删掉又会丢信息。
于是 pi 用 **宽松的内部类型 + 调用边界的严格翻译** 解决。

### 2.2 类型定义

```ts
// ---- L1：LLM 唯一认识的三种消息 ----
type TextContent = { type: "text"; text: string };
type ThinkingContent = { type: "thinking"; text: string; thinkingSignature?: string; redacted?: boolean };
type ToolCall = { type: "toolCall"; id: string; name: string; arguments: Record<string, any>; thoughtSignature?: string };
type ImageContent = { type: "image"; data: string /* base64 */; mimeType: string };

interface UserMessage { role: "user"; content: string | Array<TextContent | ImageContent>; timestamp: number; }
interface AssistantMessage {
  role: "assistant";
  content: Array<TextContent | ThinkingContent | ToolCall>;
  api: string; provider: string; model: string;
  usage: Usage; stopReason: StopReason;   // "pending"|"stop"|"length"|"toolUse"|"error"|"aborted"
  errorMessage?: string; timestamp: number;
}
interface ToolResultMessage<TDetails = any> {
  role: "toolResult";
  toolCallId: string; toolName: string;
  content: Array<TextContent | ImageContent>;
  details?: TDetails; isError: boolean; timestamp: number;
}
type Message = UserMessage | AssistantMessage | ToolResultMessage;

// ---- L2：agent 内部类型（可扩展） ----
// 关键：自定义角色通过"声明合并"注册，框架层不需要知道具体类型
interface CustomAgentMessages {} // 空壳，应用用 declare module 扩展

type AgentMessage = Message | CustomAgentMessages[keyof CustomAgentMessages];
```

应用扩展示例：

```ts
declare module "your-agent-core" {
  interface CustomAgentMessages {
    bashExecution: BashExecutionMessage; // { role: "bashExecution"; command; output; exitCode; ... }
    compactionSummary: { role: "compactionSummary"; summary: string; tokensBefore: number; timestamp: number };
  }
}
```

### 2.3 转换契约（关键不变量）

```ts
interface AgentLoopConfig {
  // 必填。每次 LLM 调用前把 AgentMessage[] 翻译成 Message[]。
  // 契约：不得 throw / reject；不可翻译的（UI 专用）消息必须过滤掉而非保留。
  convertToLlm: (messages: AgentMessage[]) => Message[] | Promise<Message[]>;

  // 可选。在 convertToLlm 之前、AgentMessage 层面处理：
  //   - 上下文裁剪（pruning）
  //   - 从外部注入上下文
  // 契约：不得 throw / reject；出错时返回原数组。
  transformContext?: (messages: AgentMessage[], signal?: AbortSignal) => Promise<AgentMessage[]>;
}
```

**消息流（必须保持这个顺序）**：

```
AgentMessage[] ──transformContext──▶ AgentMessage[] ──convertToLlm──▶ Message[] ──▶ LLM
                （可选：裁剪/注入）                     （必填：过滤/翻译）
```

**约定**：自定义角色翻译成 `user` 并包上 XML 标记，例如压缩摘要包成
`<summary>...</summary>`，分支摘要包成 `<branch-summary>...</branch-summary>`，
bash 记录渲染成代码块文本。这样模型能区分来源，又不破坏三种角色结构。

---

## 3. 流式事件协议（EventStream + partial）

### 3.1 设计意图

- 消费方（UI）要能**实时渲染增量**，又要能拿**最终结果**，还要能**取消**。
- 用一个统一事件协议覆盖所有 Provider，协议差异全部在 L1 内部消化。

### 3.2 事件协议

```ts
// 每个事件都带 partial：正在增量构建的 AssistantMessage（可变引用）
type AssistantMessageEvent =
  | { type: "start"; partial: AssistantMessage }
  | { type: "text_start"; contentIndex: number; partial: AssistantMessage }
  | { type: "text_delta"; contentIndex: number; delta: string; partial: AssistantMessage }
  | { type: "text_end"; contentIndex: number; content: string; partial: AssistantMessage }
  | { type: "thinking_start"; contentIndex: number; partial: AssistantMessage }
  | { type: "thinking_delta"; contentIndex: number; delta: string; partial: AssistantMessage }
  | { type: "thinking_end"; contentIndex: number; content: string; partial: AssistantMessage }
  | { type: "toolcall_start"; contentIndex: number; partial: AssistantMessage }
  | { type: "toolcall_delta"; contentIndex: number; delta: string; partial: AssistantMessage }
  | { type: "toolcall_end"; contentIndex: number; toolCall: ToolCall; partial: AssistantMessage }
  | { type: "done"; reason: "stop" | "length" | "toolUse"; message: AssistantMessage }
  | { type: "error"; reason: "aborted" | "error"; error: AssistantMessage };

type StreamFn = (model, context, options?) => AssistantMessageEventStream;
```

### 3.3 EventStream 实现要点

```ts
class EventStream<T, R> implements AsyncIterable<T> {
  // 语义：
  //  - push(event)：投递给正在等待的消费者；无消费者则内部排队。
  //  - end(result)：终止，之后迭代器结束；同时让 result() 解析。
  //  - result(): Promise<R>  —— 在收到终端事件（done/error）时解析出最终消息。
  //  - isComplete 谓词：判定哪个事件是"终端事件"，终端事件的 payload 就是 result。
}

// 流契约（对上层最重要的承诺）：
// 1. stream() 本身绝不 throw、绝不返回 rejected promise。
// 2. 请求/模型/运行时失败都编码为流内的 { type: "error" } 事件 + 最终的 error 消息。
// 3. 消费者可以 for await 逐事件，也可以 .result() 拿最终消息，还可以提前 break（取消）。
```

### 3.4 请求发起侧（lazyStream 模式）

```ts
// 关键技巧：先立刻返回外层流，再在后台做慢的异步准备（认证解析等），
// 准备失败也转成 error 事件，绝不从 stream() 抛出去。
function lazyStream(model, factory: () => Promise<Stream>): Stream {
  return new EventStream(
    async (push, end) => {
      try {
        const inner = await factory();
        for await (const ev of inner) push(ev);
        end(await inner.result());
      } catch (err) {
        // 构造 stopReason="error" 的 AssistantMessage 作为 error 事件
      }
    }
  );
}
```

### 3.5 请求级重试（属于 L1，可整体借鉴）

- 将 SDK 自带重试置 0，自定义重试策略。
- 触发条件：HTTP 408/409/429/5xx，或 `x-should-retry` 头。
- 退避：`retry-after`/`retry-after-ms` 头优先，封顶 `maxRetryDelayMs`；否则指数退避 + 抖动。
- **sleep 必须 abort 感知**（abort 信号到达即中断等待）。

### 3.6 消息级重试（与请求级区分）

完成一条 AssistantMessage 后，按 `errorMessage` 文本分类：
- 可重试：限流、服务过载、网络、流被截断、"you can retry your request"。
- 不可重试：配额/计费类错误。
这是独立于 HTTP 层的一次更高级判定（因为很多错误发生在"流已经成功返回但内容是错误"时）。

---

## 4. Agent 主循环（turn 内层 + follow-up 外层）

### 4.1 设计意图

一个"run"可能包含多轮 LLM 调用：LLM 回复 → 执行工具 → 把结果喂回去 → LLM 再回复 → ……，
直到没有工具调用为止。除此之外还要支持：
- **steering（打断）**：用户中途打字，下一条消息在下个 turn 之前注入。
- **follow-up（续尾）**：agent 本该停下时，还有排队消息要处理。

pi 用**双层 while** 实现：

```ts
// 外层：continue 处理 follow-up（agent 快停时）
// 内层：continue 处理工具调用和 steering
async function runLoop(initialContext, config, emit, signal, streamFn) {
  let currentContext = initialContext;
  let firstTurn = true;
  // 一开始就轮询一次 steering（用户可能在等待期间打了字）
  let pendingMessages = (await config.getSteeringMessages?.()) || [];

  while (true) {                       // 外层循环
    let hasMoreToolCalls = true;

    while (hasMoreToolCalls || pendingMessages.length > 0) {   // 内层循环
      if (!firstTurn) await emit({ type: "turn_start" });
      firstTurn = false;

      // ① 注入 pending 消息（steering 或 follow-up 带来的）
      if (pendingMessages.length > 0) {
        for (const m of pendingMessages) {
          await emit({ type: "message_start", message: m });
          await emit({ type: "message_end", message: m });
          currentContext.messages.push(m);
        }
        pendingMessages = [];
      }

      // ② 流式取 assistant 响应
      const message = await streamAssistantResponse(currentContext, config, signal, emit);

      // ③ 出错/中止 → 结束整个 run
      if (message.stopReason === "error" || message.stopReason === "aborted") {
        await emit({ type: "turn_end", message, toolResults: [] });
        await emit({ type: "agent_end", messages });
        return;
      }

      // ④ 取工具调用
      const toolCalls = message.content.filter(c => c.type === "toolCall");
      const toolResults = [];
      hasMoreToolCalls = false;
      if (toolCalls.length > 0) {
        const batch = message.stopReason === "length"
          ? await failAllToolCalls(toolCalls)          // 截断的消息：全部失败
          : await executeToolCalls(currentContext, message, config, signal, emit);
        toolResults.push(...batch.messages);
        hasMoreToolCalls = !batch.terminate;           // terminate 提示提前停
        for (const r of toolResults) currentContext.messages.push(r);
      }

      await emit({ type: "turn_end", message, toolResults });

      // ⑤ 下一 turn 可替换 context/model/thinkingLevel（如换模型、换上下文）
      const next = await config.prepareNextTurn?.({ message, toolResults, context, messages });
      if (next) { currentContext = next.context ?? currentContext; config.model = next.model ?? config.model; }

      // ⑥ 优雅停止（如上下文快满）
      if (await config.shouldStopAfterTurn?.(...)) {
        await emit({ type: "agent_end", messages });
        return;
      }

      // ⑦ 轮询 steering 队列
      pendingMessages = (await config.getSteeringMessages?.()) || [];
    }

    // ⑧ 内层退出的时机 = agent 本该停止。看 follow-up 队列。
    const followUps = (await config.getFollowUpMessages?.()) || [];
    if (followUps.length > 0) { pendingMessages = followUps; continue; }

    break;
  }

  await emit({ type: "agent_end", messages });
}
```

### 4.2 队列语义（必须精确）

| 队列 | 注入时机 | 用途 |
|---|---|---|
| steering | 当前 turn 的**所有工具调用执行完后**、下一次 LLM 调用之前（步骤⑦） | 打断/纠正进行中的工作 |
| follow-up | **没有任何工具调用且没有 steering 消息**、agent 即将结束时（步骤⑧） | 排队到任务完成后 |

队列模式（`QueueMode`）：
- `"one-at-a-time"`（默认）：每次只取最旧一条。
- `"all"`：一次全取。

### 4.3 流式 assistant 响应内部

```ts
async function streamAssistantResponse(context, config, signal, emit, streamFn) {
  let messages = context.messages;
  if (config.transformContext) messages = await config.transformContext(messages, signal);
  const llmMessages = await config.convertToLlm(messages);
  const llmContext = { systemPrompt: context.systemPrompt, messages: llmMessages, tools: context.tools };

  // 动态解析 apiKey（关键：应对短命 OAuth token）——每次调用前都解析
  const apiKey = (await config.getApiKey?.(config.model.provider)) || config.apiKey;

  const response = await streamFn(config.model, llmContext, { ...config, apiKey, signal });

  let partial = null; let addedPartial = false;
  for await (const event of response) {
    switch (event.type) {
      case "start":
        partial = event.partial; context.messages.push(partial); addedPartial = true;
        await emit({ type: "message_start", message: { ...partial } });   // 注意浅拷贝
        break;
      case "text_delta": case "thinking_delta": case "toolcall_delta": case "text_start": /* ...等增量事件 */
        partial = event.partial;
        context.messages[context.messages.length - 1] = partial;          // 原地替换数组尾部
        await emit({ type: "message_update", assistantMessageEvent: event, message: { ...partial } });
        break;
      case "done": case "error":
        const final = await response.result();
        // 更新 context 尾部为 final（已存在则替换，否则 push）
        await emit({ type: "message_end", message: final });
        return final;
    }
  }
}
```

**要点**：`context.messages` 在流式期间被"部分消息 + 原地替换"维护，所以工具预检看到的 context 已经包含触发它的 assistant 消息。

---

## 5. 有状态 Agent（状态机 + 事件总线 + 队列）

### 5.1 设计意图

底层循环是无状态的纯函数。UI/业务需要"一个会记得对话、可订阅事件、可打断的实体"。
`Agent` 类就是：**循环的有状态门面**。

### 5.2 状态定义

```ts
interface AgentState {
  systemPrompt: string;
  model: Model;                        // 下一次调用使用的模型
  thinkingLevel: "off"|"minimal"|"low"|"medium"|"high"|"xhigh"|"max";
  tools: AgentTool[];                  // 赋值时拷贝顶层数组（访问器属性）
  messages: AgentMessage[];            // 完整对话记录
  readonly isStreaming: boolean;       // 直到 agent_end 的 await 监听器全部 settle 才为 false
  readonly streamingMessage?: AgentMessage;  // 当前流式中的部分消息
  readonly pendingToolCalls: ReadonlySet<string>; // 执行中的 toolCallId
  readonly errorMessage?: string;
}
```

### 5.3 生命周期事件（Agent 级）

```
agent_start → turn_start → message_start → message_update* → message_end
            → [工具调用时] tool_execution_start → tool_execution_update* → tool_execution_end
            → message_start/end (toolResult) → turn_end
            → turn_start ... → agent_end
```

| 事件 | 含义 |
|---|---|
| `agent_start` / `agent_end` | run 开始 / 结束。`agent_end` 是最后一个事件，但 run 的 settle 要等它的 await 监听器完成 |
| `turn_start` / `turn_end` | 一次 assistant 响应 + 其工具执行。`turn_end` 携带 `{ message, toolResults }` |
| `message_start` / `message_update` / `message_end` | 任意消息。`message_update` 仅 assistant 流式期间，带底层 `assistantMessageEvent` |
| `tool_execution_start` / `tool_execution_update` / `tool_execution_end` | 工具执行进度 |

### 5.4 订阅语义（关键不变量）

1. 监听器按**注册顺序**被 await。
2. `message_end` 是工具预检前的**屏障**：订阅者处理完 `message_end` 之后，`beforeToolCall` 才运行。所以监听器可以安全地原地改写消息。
3. `agent_end` 只表示"不再有循环事件"；`waitForIdle()` / 下一次 `prompt()` 要等 `agent_end` 的监听器全部 settle。

```ts
class Agent {
  subscribe(listener: (event: AgentEvent, signal: AbortSignal) => void | Promise<void>): () => void;
  async prompt(input: string | AgentMessage | AgentMessage[]): Promise<void>;  // run 进行中调用会抛错
  async continue(): Promise<void>;   // 从现有上下文继续（重试用，末尾必须是 user/toolResult）
  steer(message); followUp(message); clearSteeringQueue(); clearFollowUpQueue();
  async waitForIdle(): Promise<void>;
  abort(): void;                     // 触发当前 run 的 AbortController
  reset(): void;
}
```

### 5.5 状态归约

`Agent` 内部把事件转成状态变更（`processEvents`），UI 不必自己累加：

```ts
message_start/update → state.streamingMessage = event.message
message_end           → streamingMessage = undefined; state.messages.push(event.message)
tool_execution_start  → state.pendingToolCalls.add(id)
tool_execution_end    → state.pendingToolCalls.delete(id)
turn_end              → 若 assistant 有 errorMessage 则 state.errorMessage = ...
agent_end             → streamingMessage = undefined
```

### 5.6 run 失败的处理

循环被外力打断/出错时，Agent 构造一条假的 `AssistantMessage`（`stopReason: "aborted"|"error"`，`errorMessage`），
并照常走完 `message_start → message_end → turn_end → agent_end` 事件序列——**保证 UI 永远收到完整的终止序列**。

---

## 6. 高层 Harness（turn 快照 + hook + 持久化）

### 6.1 设计意图

`Agent` 之上再加一层 `Harness`，解决 agent 与**具体应用**的耦合：
- 每轮 turn 都从 Session 重建"turn 快照"（允许中途换模型/工具/思考级别）。
- 把持久化、压缩、分支摘要、Provider 钩子、资源（skills/templates）编排进一个类。
- 通过"钩子事件 + 返回值"让应用在特定节点插入逻辑。

### 6.2 Turn 快照（核心概念）

```ts
interface TurnState {
  messages: AgentMessage[];
  resources: { skills?; promptTemplates? };
  toolContext: TContext;          // 应用自定义，绑定进每个工具闭包
  streamOptions: StreamOptions;   // 每轮浅拷贝，避免跨轮污染
  sessionId: string;
  systemPrompt: string;
  model: Model;
  thinkingLevel: ThinkingLevel;
  tools: TTool[];                 // 全部注册工具
  activeTools: TTool[];           // 本轮激活的工具
}

// 每轮开始：从 session 重建快照
async function createTurnState() {
  const context = await session.buildContext();     // 读 Session 树投影出消息
  const toolContext = await resolveToolContext();   // 静态值或 () => value 提供器
  return { messages: context.messages, /* ... */ };
}

// 工具上下文绑定：把每轮快照闭包进 execute
function bindToolContext(tool, context) {
  return { ...tool, execute: (id, params, signal, onUpdate) => tool.execute(id, params, signal, onUpdate, context) };
}
```

### 6.3 钩子事件表（可返回值的用结果类型限定）

| 钩子 | 入参 | 返回值能力 |
|---|---|---|
| `before_agent_start` | prompt, images, systemPrompt, resources | 追加消息、替换 systemPrompt |
| `context` | messages | 替换消息列表（LLM 调用前最后一道闸） |
| `before_provider_request` | model, streamOptions | patch streamOptions |
| `before_provider_payload` | model, payload | 替换 payload |
| `after_provider_response` | status, headers | 无 |
| `tool_call` | toolCallId, toolName, input | `{ block, reason }` 拦截执行 |
| `tool_result` | toolCallId, toolName, input, content, details, isError | 局部改写 result |
| `session_before_compact` | preparation, branchEntries | `{ cancel, compaction }` 可自定义压缩 |
| `session_before_tree` | preparation | `{ cancel, summary }` 可自定义分支摘要 |

**事件分发语义**：订阅 `"*"` 是纯观察；订阅具名事件可以有返回值，多个 handler 依次调用、后一个非 undefined 返回值覆盖前一个。

### 6.4 持久化：延迟写 + 保存点

```ts
// 运行期间应用对 session 的写入（改模型/工具/追加消息）不直接落盘，
// 而是进 pendingSessionWrites 队列，在安全点 flush：
//   - turn_end 之后（同时发 save_point 事件）
//   - agent_end 之前
// 好处：a) 避免高频写；b) 保证每个 turn 是一个原子持久化单元。
```

### 6.5 队列语义（Harness 版）

除 steering/follow-up 外还有 `nextTurn` 队列：随**下一条 user 消息**一并注入（作为"附带上下文"，如 asides）。`prompt()` 时若 nextTurn 队列非空，先 splice 出来拼到消息数组头部。

---

## 7. Session 树存储（追加式分支树）

### 7.1 设计意图

对话历史**不是列表而是树**：支持原地分支（改某个历史点后走新路）、回退、时间旅行，而不用复制文件。
实现为**纯追加日志 + 移动的 leaf 指针**，任何条目永不删除。

### 7.2 数据结构

```ts
interface SessionTreeEntryBase {
  type: string;             // 见下
  id: string;               // 8 位 uuidv7 后缀，碰撞重试
  parentId: string | null;  // 树的父指针
  timestamp: string;        // ISO 字符串
}

// 条目类型：message | thinking_level_change | model_change | active_tools_change
//          | compaction | branch_summary | custom | custom_message | label | session_info | leaf
```

**规则**：
- 当前编辑位置叫 `leafId`。每次 append 创建 `leafId` 的子节点，append 后 `leafId` 指向新条目。
- **分支/跳转**：写入一条 `leaf` 条目（`targetId` 指向要跳到的条目），`leafId` 变为 `targetId`。
  若跳走时想总结被放弃的路径，再追加一条 `branch_summary` 条目。
- **永远不删除/修改已追加条目**。时间旅行靠"跳 leaf 指针"。

### 7.3 读路径

```ts
// 从 leaf 沿 parentId 向上走，遇到 compaction 条目停止。
// 这保证每次读取的上下文都被压缩边界截断，不会无限膨胀。
readPathToRootOrCompaction(leafId): SessionTreeEntry[];

// 投影为模型上下文：message 与 custom_message 条目 → AgentMessage；
// compaction 条目 → summary 消息 + retainedTail 原文；其余条目（元数据）不进上下文，
// 但会按时间线推导"当前 thinkingLevel / model / activeTools"。
buildSessionContext(entries): { messages, thinkingLevel, model, activeToolNames };
```

### 7.4 存储实现

- 默认 JSONL：一个会话一个文件，首行 header `{ type:"session", version, id, timestamp, cwd, parentSession? }`，之后每行一个 JSON 化的条目。
- **并发控制**：`KeyedOperationQueue`——按 key（如文件路径）串行，不同 key 可并行；`list()` 用 barrier 等所有在途操作。
- 读多写少：内存索引（`byId` Map + 派生投影 stats/labels）配合 append-only 文件。

### 7.5 分叉（fork）

把当前 active path（leaf → root）复制到**新会话文件**，新文件 header 的 `parentSession` 指向旧文件。
复制时重新链 labels，生成新 session id。

---

## 8. 上下文压缩 Compaction

### 8.1 决策链

```
estimateTokens(messages)              启发式：每角色按字符估算（chars/4）
estimateContextTokens(messages)       优化：若最近 assistant 有有效 usage，信任 Provider 数字，只估算其后尾部
shouldCompact(contextTokens, window, settings)
    = contextTokens > window - settings.reserveTokens   // 默认 reserveTokens=16384
findCutPoint(entries, start, end, keepRecentTokens)     // 默认 keepRecentTokens=20000
```

### 8.2 切点算法（findCutPoint）

1. 计算合法切点：只能切在"可见消息"（`user/assistant/bashExecution/custom/branchSummary/compactionSummary`）或 `branch_summary/custom_message` 条目上。`toolResult` 和一切元数据条目**不可**切。
2. 从后往前累加每个可见消息的 `estimateTokens`，直到攒够 `keepRecentTokens` 预算，取达到预算时的那个条目为切点。
3. 向前回溯跳过元数据条目，但若撞到更早的 compaction 或 message 就停。
4. 若切点落在非 user 条目上，用 `findTurnStartIndex` 找本 turn 起点；`isSplitTurn = 切点非 user 且找到了 turn 起点`。

### 8.3 摘要生成（LLM 做）

- 摘要 prompt 固定结构化格式：`## Goal / ## Constraints & Preferences / ## Progress(Done|In Progress|Blocked) / ## Key Decisions / ## Next Steps / ## Critical Context`。
- **迭代压缩**：如果已存在上一次的 compaction 摘要，把它作为 `<previous-summary>` 喂回去，新摘要是在其基础上"增量更新"而非重写。
- **split-turn 处理**：先对历史做常规摘要，再对 turn 前缀做 `TURN_PREFIX` 摘要，两条摘要拼接。
- 摘要消息作为 `compactionSummary` 角色进上下文（外包 `<summary>` 标签），**最近 `retainedTail` 条消息保留原文**。
- `details` 里记录文件操作集合（`read/written/edited`），并让后续压缩**复用**上一次的 fileOps 再累加，保证跨压缩的文件感知不丢。

### 8.4 压缩条目写回

```ts
session.appendCompaction(summary, firstKeptEntryId, tokensBefore, details, fromHook, usage, retainedTail);
```

### 8.5 触发点

- 手动：`/compact` 或带自定义指令。
- 自动：turn 结束后检查（`shouldCompact`），或**溢出恢复**（检测到 context 溢出错误 → 压缩 → 重试）。

---

## 9. 工具系统

### 9.1 工具定义

```ts
interface AgentTool<TParams extends TSchema = TSchema, TDetails = any> {
  name: string;
  label: string;                    // UI 显示名
  description: string;
  parameters: TParams;              // TypeBox schema —— 参数定义唯一事实来源
  constrainedSampling?: false | ConstrainedSamplingConfig;  // JSON-schema strict / grammar
  prepareArguments?: (args: unknown) => Static<TParams>;    // 兼容旧格式的归一化 shim
  executionMode?: "sequential" | "parallel";                // 单工具覆盖全局模式
  execute: (
    toolCallId: string,
    params: Static<TParams>,
    signal?: AbortSignal,
    onUpdate?: (partialResult: AgentToolResult<TDetails>) => void,  // 流式进度
  ) => Promise<AgentToolResult<TDetails>>;
}

interface AgentToolResult<TDetails> {
  content: Array<TextContent | ImageContent>;  // 喂回给模型的文本/图片
  details: TDetails;                            // UI/日志用的结构化数据
  usage?: Usage;
  addedToolNames?: string[];                    // 本结果引入的新工具（延迟加载）
  terminate?: boolean;                          // 本批全部 terminate=true 才提前停
}
```

### 9.2 执行管线（顺序固定）

```
tool_call 出现在 assistant 消息
  → emit tool_execution_start
  → prepareToolCall():
      工具不存在          → 立即错误结果 "Tool X not found"（isError:true）
      prepareArguments 归一化参数
      validateToolArguments（TypeBox 校验，失败 → 立即错误结果）
      beforeToolCall 钩子 → 返回 { block, reason } 则阻止，错误结果显示 reason
  → executePreparedToolCall():
      tool.execute(id, params, signal, onUpdate)
      onUpdate 进度事件（promise 化收集，执行结束后 await 全部，保证顺序）
      抛错 → 构造错误结果（isError:true），错误消息作为 content
  → finalizeExecutedToolCall():
      afterToolCall 钩子可局部改写 content/details/isError/usage/terminate
  → emit tool_execution_end
  → 构造 ToolResultMessage → emit message_start/end
```

### 9.3 并行/串行执行

- 全局 `toolExecution: "parallel"（默认）| "sequential"`。
- **任一工具声明 `executionMode: "sequential"` → 整个批次退化为串行**。
- parallel 实现：
  1. 所有工具串行做"预检"（`tool_execution_start` + `prepareToolCall`）。
  2. 通过预检的并发执行。
  3. `tool_execution_end` 按**完成顺序**发出。
  4. `toolResult` 消息按 **assistant 源顺序**发出（保证落盘顺序与模型看到的顺序一致）。

### 9.4 terminate 提前停语义（容易错，务必照抄）

- 工具返回 `terminate: true` 只是"提示"。
- **仅当一批内所有工具的最终结果都是 `terminate: true` 才提前停**；混批继续正常循环。
- 只影响运行时循环控制，不改变落盘的 toolResult 消息形态。

### 9.5 截断保护

assistant 消息 `stopReason === "length"`（输出被 token 上限截断）时，其 toolCall 参数**可能不完整**——
全部不执行，每个返回错误结果，要求模型重新发起。这是防御性设计，务必保留。

### 9.6 可插拔操作层（强烈推荐借鉴）

内置工具不要直接调用 `fs`/`child_process`，而是定义**操作接口 + 工厂**：

```ts
// 每个工具声明自己的操作集
interface BashOperations {
  exec: (command, cwd, options: { onData; signal?; timeout?; env? }) => Promise<{ exitCode: number|null }>;
}
interface ReadOperations { readFile; access; detectImageMimeType; }
interface WriteOperations { writeFile; mkdir; }
interface EditOperations { readFile; writeFile; access; }
interface GrepOperations { isDirectory; readFile; }
// 默认实现 = 本地环境；SSH/沙箱 = 换一组实现，工具本体零改动
function createBashToolDefinition(cwd, options: { operations?: BashOperations }) { ... }
```

**收益**：沙箱化、远程执行、权限门控都变成"换实现"而非"改工具"。

### 9.7 输出安全（bash 工具）

- 尾部截断（保留最后 N 行 / 最大字节数），截断时把完整输出写到临时文件，工具 result 里带 `fullOutputPath`。
- 流式输出做二进制/ANSI 清洗。
- 非零退出码 → 抛错（作为错误结果回给模型）；超时/中止区分处理。

---

## 10. 扩展系统（工厂 + 事件 + ctx 失效）

### 10.1 形态

扩展 = 一个工厂函数，接收一个 `api` 对象：

```ts
type ExtensionFactory = (pi: ExtensionAPI) => void | Promise<void>;
// 例：export default (pi) => { pi.registerTool({...}); pi.on("tool_call", async (e, ctx) => {...}); }
```

### 10.2 API 四类能力

| 类别 | 方法 |
|---|---|
| 事件订阅 | `on(event, handler)` —— 覆盖 §6.3 全部钩子 + `message_end/tool_call/tool_result/input/user_bash/session_*` 等 |
| 注册 | `registerTool` / `registerCommand` / `registerShortcut` / `registerFlag` |
| 渲染 | `registerMessageRenderer` / `registerMarkdownTransformer` / `registerEntryRenderer` |
| 动作 | `sendMessage` / `sendUserMessage` / `setModel` / `setThinkingLevel` / `registerProvider` / `getActiveTools` / `setActiveTools` / `events`(扩展间事件总线) |

### 10.3 上下文 ctx（惰性 + 失效检测）

```ts
interface ExtensionContext {
  ui: UIContext;            // select/confirm/input/notify/editor/setWidget/... —— 每种运行模式有不同实现
  mode: "tui"|"rpc"|"json"|"print";
  cwd: string;
  model: Model | undefined;
  isIdle(): boolean;
  signal?: AbortSignal;
  abort(): void;
  compact(options?): void;
  // ...
}
```

**关键机制**：
1. ctx 的所有 getter **惰性求值**（调用时才解析），不要在创建时快照。
2. session 被替换/重载后，运行时 `invalidate()`，旧 ctx 的 getter 抛"stale"错误——防止扩展在旧会话上乱操作。
3. 每个事件 handler 都收到当时的 ctx；会话切换后新事件带新 ctx。

### 10.4 变异型事件的"合成"分发

普通事件是扇出（fan-out）；能改结果的用专门 emitter：

| emitter | 语义 |
|---|---|
| `emitMessageEnd` | 链式：每个 handler 可返回替换消息，后者替换前者；**角色必须一致** |
| `emitToolCall` | 支持 `{ block }` 拦截 |
| `emitInput` | `continue | transform | handled` 三态短路 |
| `emitBeforeAgentStart` | 链式替换 systemPrompt + 收集 custom 消息 |

### 10.5 加载与作用域

- 加载顺序：项目目录 → 全局目录 → 显式配置路径。
- 用 jiti 动态加载 TS（Bun 二进制用内嵌虚拟模块）。
- 命令重名 → 自动加后缀 `name:1`；工具重名 → 先注册者胜。
- **provider 注册在加载期被排队**，`bindCore`（运行时初始化完成）后统一 flush 生效。

---

## 11. 认证抽象

```ts
// 认证只产出这三样东西，别无其他
interface ModelAuth { apiKey?: string; headers?: Record<string,string>; baseUrl?: string; }

// 解析顺序（优先级从高到低）
// 1. 显式传入的 overrides.apiKey
// 2. 已存储的凭据（优先 OAuth，其次 API key）
// 3. 环境变量 / 环境发现（如 ADC 文件、Bedrock 的多来源链）

// OAuth 刷新：双检锁（double-checked locking）
//   - 只有"剩余有效期 < minOAuthValidityMs(默认5分钟)"才刷新
//   - 刷新在 CredentialStore 的 modify()（read-modify-write 串行化）下进行，
//     防止并发请求双重刷新
//   - 短命 token 场景：agent 每次 LLM 调用前都重新解析一次 apiKey
```

---

## 12. 错误处理约定与全局不变量

> 这些不是风格建议，是**正确性契约**。实现时必须逐条遵守。

1. **LLM 流绝不 throw**：请求/模型/运行时失败 → 流内 `error` 事件 + 最终 error 消息（带 `errorMessage` 与正确 `stopReason`）。
2. **文件/进程操作绝不 throw**：`ExecutionEnv` 全部返回 `Result<T, FileError|ExecutionError>`，`FileError` 带稳定错误码（`not_found`/`permission_denied`/`aborted`/...）。抛错只发生在适配器边界。
3. **hook 绝不 throw**：`beforeToolCall`/`afterToolCall`/`transformContext`/`convertToLlm` 出错应返回安全默认值（不执行 / 原消息 / 过滤后的消息）。
4. **事件订阅者会被 await**：它们必须是"屏障"（如 `message_end` 后工具才预检），所以 UI 的异步副作用（渲染、持久化）在订阅者里完成，保证时序。
5. **状态通过事件归约**：不要相信 UI 自己累加的状态；Agent 通过 `processEvents` 维护唯一状态，UI 读取。
6. **数组赋值拷贝**：`state.tools`/`state.messages` 赋值时拷贝顶层数组，防止外部意外共享。
7. **消息对象原位替换**（需要扩展改写消息时）：mutate 同一对象而不是替换数组元素，否则 Agent 内部引用/持久化/事件三处会不同步。

---

## 13. 端到端时序（一次完整 prompt）

```
UI 提交 "修复 bug"
  → AgentSession.prompt(text)            # 领域层
      ├─ 扩展命令短路（/cmd）→ 直接执行，返回
      ├─ input 事件（transform/handled）
      ├─ skill / prompt 模板展开
      ├─ isStreaming ? steer()/followUp() 入队 : 继续
      ├─ 模型/API key 校验
      ├─ 预压缩检查（上一条 assistant 若该压缩先压）
      └─ before_agent_start → 收集 custom 消息 → 拼 user 消息
  → Agent.prompt(messages)               # 框架层
      ├─ agent_start
      └─ runAgentLoop
          ├─ message_start/end(user)
          ├─ [transformContext → convertToLlm] → streamFn → LLM
          ├─ message_start(assistant partial) → message_update* → message_end
          ├─ toolCalls? → [预检→执行→finalize] → tool_execution_* → toolResult 消息
          ├─ turn_end
          ├─ steering 队列? → 注入 → 下一个 turn
          ├─ follow-up 队列? → 注入 → 外层继续
          └─ agent_end
      └─ [订阅者] agent_end 处理 → waitForIdle 才 resolve
  → AgentSession._handleAgentEvent       # 领域层
      ├─ 转发给扩展
      ├─ 持久化（message_end → session.appendMessage）
      └─ 自动重试 / 自动压缩检查 → 需要则 agent.continue() 再跑一轮
  → UI 订阅者增量渲染
```

---

## 14. 借鉴优先级清单（如果时间有限）

| 优先级 | 设计 | 理由 |
|---|---|---|
| P0 | §2 双消息模型 + convertToLlm | 不搞，后面自定义消息/压缩/UI 全都别扭 |
| P0 | §3 流式事件协议（partial + 绝不 throw） | UI 实时渲染与错误处理的地基 |
| P0 | §4 双层循环 + steering/follow-up 队列 | 多轮工具调用的正确骨架，也是"可打断"的根 |
| P0 | §5 有状态 Agent + 订阅者被 await | 事件/状态/持久化的时序正确性 |
| P0 | §9.2 工具执行管线 + §9.3 并行/串行 | 工具是 agent 的四肢，管线的失败语义要严格 |
| P1 | §7 Session 树 | 分支/时间旅行是强差异化能力，且实现不贵 |
| P1 | §9.6 可插拔操作层 | 一次抽象，换来沙箱/远程/权限全部可替换 |
| P1 | §8 Compaction | 长对话刚需；先做"信任 usage + 尾部保留 + 迭代摘要" |
| P1 | §6 turn 快照 + 钩子 | 让 agent 可换模型/换上下文，给上层弹性 |
| P2 | §10 扩展系统 | 产品化后做；机制（惰性 ctx + 失效）比语法糖重要 |
| P2 | §11 认证抽象 | 多 provider 才需要 |
| P2 | §12 错误约定 | 是贯穿性约束，写每层时顺手遵守即可 |

---

## 附：实现时的常见陷阱（对照 pi 的踩坑点）

1. **parallel 模式下 toolResult 顺序**：完成事件按完成序、消息落盘按源顺序——两者不一致，别写反。
2. **terminate 是"全体才生效"**，不是任何一个 true 就停。
3. **`stopReason === "length"` 时别执行任何工具**。
4. **消息浅拷贝进事件**（`message: { ...partial }`），否则 UI 直接 mutate 会破坏内部状态。
5. **OAuth 刷新必须串行化**（modify 锁），否则并发请求同时刷新导致 token 失效。
6. **压缩切点不能在 toolResult 上**，否则把"调用和结果"劈开，模型看到残缺的配对。
7. **事件订阅者是 await 的屏障**——别把它当 fire-and-forget，否则持久化会乱序。
8. **`convertToLlm` 只能过滤，不能 throw**；UI 专用消息漏过去会让某些 Provider 直接 400。
9. **hook（beforeToolCall 等）出错时返回安全默认**，让工具"被 block / 原样通过"，而不是把 agent 打死。
10. **session 树追加必须串行**（promise 链），并发 append 会弄乱 parentId 链。

---

*文档依据：`earendil-works/pi-mono`（pi-agent-core / pi-coding-agent / pi-ai）。原文关键文件：
`packages/agent/src/{agent,agent-loop,types}.ts`、`packages/agent/src/harness/*`、
`packages/coding-agent/src/core/{agent-session,agent-session-runtime,model-runtime}.ts`、
`packages/coding-agent/src/core/tools/*`、`packages/ai/src/{types,models}.ts`。*
