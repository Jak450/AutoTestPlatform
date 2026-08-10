# Agent 设计参考文档

> 提取自 s01~s20 教学系列的所有亮点设计，按课程顺序组织为 20 章。
> 每章包含数据结构、执行流程、边界条件处理，可直接作为实现参考。

---

## 第1章：Agent 主循环 (Agent Loop)

### 基本结构

主循环是一个 `while True` 的无限循环，每轮执行：接收用户输入 → LLM 推理 → 工具调用 → 结果回传 → 下一轮。

```python
def agent_loop(messages, context):
    system = assemble_system_prompt(context)
    while True:
        response = client.messages.create(
            model=MODEL, system=system, messages=messages,
            tools=TOOLS, max_tokens=8000)

        messages.append({"role": "assistant", "content": response.content})

        if response.stop_reason != "tool_use":
            return  # LLM 认为任务完成，退出循环

        # 执行工具
        results = []
        for block in response.content:
            if block.type == "tool_use":
                handler = HANDLERS.get(block.name)
                output = handler(**block.input)
                results.append({"type": "tool_result",
                                "tool_use_id": block.id,
                                "content": output})

        messages.append({"role": "user", "content": results})
        # 循环继续，LLM 看到工具结果后决定下一步
```

### 消息格式

所有消息遵循 `{"role": "user"|"assistant", "content": ...}` 格式。assistant 消息的 content 是 Anthropic API 返回的 content block 列表（text 块 + tool_use 块），user 消息的 content 是 tool_result 块列表或纯文本字符串。

### 循环退出条件

1. `response.stop_reason != "tool_use"` — LLM 认为已完成，不再调用工具
2. 外部异常（权限拒绝、API 错误等）

### 与外部系统的协作

主循环通过 `agent_lock = threading.Lock()` 保护共享状态（消息列表、上下文），使得后台线程（cron 自动运行、teammate 结果注入）可以安全地与主循环并发操作。

---

## 第2章：工具调用与分发 (Tool Use)

### 双表结构

工具定义和实际执行函数分离在两个结构中：

```python
# 表1：给 LLM 看的 JSON Schema（符合 Function Calling 规范）
TOOLS = [
    {"name": "bash",
     "description": "Run a shell command.",
     "input_schema": {"type": "object",
                       "properties": {"command": {"type": "string"}},
                       "required": ["command"]}},
    {"name": "read_file",
     "description": "Read file contents.",
     "input_schema": {"type": "object",
                       "properties": {"path": {"type": "string"},
                                      "limit": {"type": "integer"},
                                      "offset": {"type": "integer"}},
                       "required": ["path"]}},
    # ...更多工具
]

# 表2：Python 执行函数（按工具名索引）
HANDLERS = {
    "bash": run_bash,
    "read_file": run_read,
    # ...更多 handler
}
```

### 统一调用入口

所有工具调用通过同一个函数分发，统一处理异常：

```python
def call_tool_handler(handler, args, name):
    if not handler:
        return f"Unknown: {name}"          # 工具未注册
    try:
        return handler(**(args or {}))      # 展开参数字典调用
    except TypeError as e:
        return f"Error: {e}"               # 参数不匹配
```

### 基本工具实现

**bash**：`subprocess.run(shell=True, timeout=120)`，捕获 stdout+stderr，截断到 50000 字符

**read_file**：读文件 → 按行分割 → 支持 offset/limit 分页 → 超出时显示 `... (N more lines)`

**write_file**：创建父目录 → `write_text(content)` → 返回写入字节数

**edit_file**：`read_text` → `str.replace(old_text, new_text, 1)`（只替换首次出现）

**glob**：`glob.glob(pattern, root_dir=workspace)`，过滤确保结果在 workspace 内

### 路径安全

所有文件工具共享一个 `safe_path` 函数，防止路径逃逸攻击：

```python
def safe_path(p, cwd=None):
    base = cwd or WORKSPACE
    path = (base / p).resolve()
    if not path.is_relative_to(base):
        raise ValueError(f"Path escapes workspace: {p}")
    return path
```

`bash` 工具不使用 `safe_path`（因为 shell 命令本身是灵活的），而是依赖权限系统控制。

---

## 第3章：权限系统 (Permission)

### 两层权限模型

| 层级 | 名称 | 触发条件 | 行为 |
|---|---|---|---|
| 硬拒绝 | Deny List | 命令包含特定关键词 | 直接返回错误，不弹交互 |
| 软确认 | Destructive List | 命令包含风险关键词 | 弹交互询问用户确认 |

```python
DENY_LIST = ["rm -rf /", "sudo", "shutdown", "reboot", "mkfs", "dd if="]
DESTRUCTIVE = ["rm ", "> /etc/", "chmod 777"]
```

### 权限检查的执行时机

权限检查通过 Hook 系统（见第4章）在 `PreToolUse` 事件中执行，即工具被调用**之前**拦截：

```python
def permission_hook(block):
    if block.name == "bash":
        command = block.input["command"]
        for pattern in DENY_LIST:
            if pattern in command:
                return f"Permission denied: '{pattern}' is on the deny list"
        if any(token in command for token in DESTRUCTIVE):
            choice = input("  Allow? [y/N] ").strip().lower()
            if choice not in ("y", "yes"):
                return "Permission denied by user"

    if block.name in ("write_file", "edit_file"):
        try:
            safe_path(block.input["path"])     # 检查路径是否在 workspace 内
        except Exception:
            return f"Permission denied: path escapes workspace"

    if block.name.startswith("mcp__") and "deploy" in block.name:
        choice = input("  Allow? [y/N] ").strip().lower()
        if choice not in ("y", "yes"):
            return "Permission denied by user"

    return None  # None = 通过，放行
```

**返回值规则**：返回字符串 = 权限拒绝（该字符串作为 tool_result 返回给 LLM），返回 `None` = 通过，正常执行工具。

### 为什么 bash 不用 safe_path

bash 命令的执行路径始终是 workspace 根目录或 worktree 目录（通过 `cwd` 参数），不涉及文件路径参数。权限控制由 deny/destructive 列表处理——bash 是"能力大，靠规则限制"的策略。

---

## 第4章：Hooks 事件管道 (Hooks)

### 设计理念

工具执行逻辑与权限/日志/监控逻辑完全解耦。每个 hook 是独立函数，注册到特定事件点。agent_loop 不需要知道有哪些 hook。

### 4 个事件点

| 事件 | 触发时机 | 参数 | 返回值含义 |
|---|---|---|---|
| `UserPromptSubmit` | 用户输入后、进入循环前 | `query: str` | 非 None = 替换用户输入 |
| `PreToolUse` | 工具执行前 | `block: ToolUseBlock` | 非 None = 跳过执行，返回值作为 tool_result |
| `PostToolUse` | 工具执行后 | `block, output` | 忽略 |
| `Stop` | agent 循环结束 | `messages: list` | 忽略 |

### 核心实现

```python
HOOKS = {
    "UserPromptSubmit": [],
    "PreToolUse": [],
    "PostToolUse": [],
    "Stop": []
}

def register_hook(event, callback):
    HOOKS[event].append(callback)

def trigger_hooks(event, *args):
    for callback in HOOKS[event]:
        result = callback(*args)
        if result is not None:       # 第一个非 None 返回值即视为"拦截"
            return result
    return None
```

### 拦截语义

`trigger_hooks` 按注册顺序依次执行 hook 函数。**第一个返回非 None 的函数会终止后续 hook 的执行**，其返回值被当作拦截结果。

这意味着 hook 的注册顺序决定优先级。例如，`permission_hook` 应最先注册，确保安全检查最先执行。

### 已注册的 hook 函数

1. **permission_hook** (PreToolUse) — 权限检查（见第3章）
2. **log_hook** (PreToolUse) — 打印工具调用名称：`[HOOK] bash`
3. **large_output_hook** (PostToolUse) — 输出超过 100K 字符时打警告
4. **user_prompt_hook** (UserPromptSubmit) — 打印工作目录
5. **stop_hook** (Stop) — 统计 loop 中执行的工具调用次数

### 在 agent_loop 中的使用位置

```python
# 用户输入时
trigger_hooks("UserPromptSubmit", query)

# 每个工具执行前
blocked = trigger_hooks("PreToolUse", block)
if blocked:
    results.append({"type": "tool_result",
                    "tool_use_id": block.id, "content": str(blocked)})
    continue

# 每个工具执行后
trigger_hooks("PostToolUse", block, output)

# 循环退出时
trigger_hooks("Stop", messages)
```

---

## 第5章：Todo 任务管理 (Todo Write)

### 数据结构

Todo 是轻量级的运行时任务列表，与 Task 系统（第12章）不同——Todo 仅存在于当前会话的内存中，不持久化到磁盘。

```python
CURRENT_TODOS: list[dict] = []

# 每个 todo 项的结构：
# {"content": "字符串描述", "status": "pending" | "in_progress" | "completed"}
```

### 输入归一化

LLM 可能传递 JSON 字符串、Python 字面量字符串、或 list 对象，需要统一处理：

```python
def _normalize_todos(todos):
    if isinstance(todos, str):
        try:
            todos = json.loads(todos)         # 先尝试 JSON 解析
        except:
            try:
                todos = ast.literal_eval(todos)  # 再尝试 Python 字面量
            except:
                return None, "Error: must be list or JSON array"

    if not isinstance(todos, list):
        return None, "Error: todos must be a list"

    for i, todo in enumerate(todos):
        if "content" not in todo or "status" not in todo:
            return None, f"Error: todos[{i}] missing 'content' or 'status'"
        if todo["status"] not in ("pending", "in_progress", "completed"):
            return None, f"Error: todos[{i}] invalid status"

    return todos, None
```

### 与 agent_loop 的集成

**主动提醒**：`rounds_since_todo` 计数器每执行一个非 `todo_write` 工具就 +1。达到 3 轮时自动注入提醒消息：

```python
if rounds_since_todo >= 3:
    messages.append({"role": "user",
                     "content": "<reminder>Update your todos.</reminder>"})
    rounds_since_todo = 0
```

当 agent 调用 `todo_write` 工具时，计数器归零。

---

## 第6章：Subagent 系统 (Subagent)

### 核心设计

Subagent 是一个独立、短暂、有限的 LLM Agent。主 agent 将复杂子任务委托给它，只获取最终摘要，不知道 subagent 内部的工具调用细节。

### 独立配置

Subagent 有自己的 system prompt 和工具集：

```python
SUB_SYSTEM = (
    "You are a coding subagent. Complete the task, "
    "return concise summary. Do not spawn more agents."
)

SUB_TOOLS = [bash, read_file, write_file, edit_file, glob]
# 刻意精简：没有 task 管理、cron、teammate 等高级工具
```

### 执行流程

```python
def spawn_subagent(description):
    messages = [{"role": "user", "content": description}]
    for _ in range(30):               # 最多 30 轮
        response = client.messages.create(
            model=MODEL, system=SUB_SYSTEM,
            messages=messages, tools=SUB_TOOLS, max_tokens=8000)

        messages.append({"role": "assistant", "content": response.content})

        if not has_tool_use(response.content):
            break                     # 没有工具调用 → 完成

        # 执行工具（同样经过 hooks 管道）
        results = []
        for block in response.content:
            if block.type == "tool_use":
                blocked = trigger_hooks("PreToolUse", block)
                if blocked:
                    output = str(blocked)
                else:
                    handler = SUB_HANDLERS.get(block.name)
                    output = call_tool_handler(handler, block.input, block.name)
                    trigger_hooks("PostToolUse", block, output)
                results.append({"type": "tool_result", ...})
        messages.append({"role": "user", "content": results})

    # 提取最后一条文本作为摘要返回
    return extract_last_text(messages)
```

### 关键设计决策

- Subagent 也经过 hooks 管道，权限检查依然生效
- 最多 30 轮，防止无限循环
- Subagent 工具集中不包含 `spawn_subagent`，防止递归

---

## 第7章：技能加载系统 (Skill Loading)

### 文件约定

每个技能是一个子目录，目录下有一个 `SKILL.md` 文件。文件开头可以有 YAML frontmatter 定义元数据：

```
skills/
  my-skill/
    SKILL.md       ← 必须有这个文件
```

`SKILL.md` 格式：
```markdown
---
name: my-skill
description: 这个技能的简短描述
---
# 技能标题

技能的实际内容...
```

### 注册表

启动时扫描 `skills/` 目录，将所有 `SKILL.md` 解析后存入全局注册表：

```python
SKILL_REGISTRY: dict[str, dict] = {}

def scan_skills():
    for directory in SKILLS_DIR.iterdir():
        if not directory.is_dir():
            continue
        manifest = directory / "SKILL.md"
        if not manifest.exists():
            continue

        raw = manifest.read_text()
        meta, _ = parse_frontmatter(raw)
        name = meta.get("name", directory.name)
        desc = meta.get("description", first_heading_line(raw))

        SKILL_REGISTRY[name] = {
            "name": name,
            "description": desc,
            "content": raw,     # 完整内容，包含 frontmatter
        }
```

### YAML Frontmatter 解析

```python
def parse_frontmatter(text):
    if not text.startswith("---"):
        return {}, text
    parts = text.split("---", 2)
    if len(parts) < 3:
        return {}, text
    try:
        meta = yaml.safe_load(parts[1]) or {}
    except yaml.YAMLError:
        meta = {}
    return meta, parts[2].strip()  # 返回 (元数据, 正文)
```

### 交互语义

- `list_skills()`: LLM 可查看已加载的所有技能及其描述
- `load_skill(name)`: LLM 请求加载某个技能的完整内容（注入到上下文或 system prompt 中）
- 技能目录在 system prompt 中列出，提示 LLM "Use load_skill(name) when a skill is relevant"

---

## 第8章：上下文压缩管道 (Context Compaction)

### 设计理念

**不等到 LLM 返回 `context_length_exceeded` 错误才压缩**，而是在每轮 LLM 调用之前主动执行一系列压缩步骤。压缩分 4 层，从轻到重，只有前面的层不够时才触发更重的层。

### 完整管线

```python
def prepare_context(messages):
    messages[:] = tool_result_budget(messages)    # 第1层：大结果持久化
    messages[:] = snip_compact(messages)           # 第2层：截断中间消息
    messages[:] = micro_compact(messages)           # 第3层：保留最新 N 条结果
    if estimate_size(messages) > CONTEXT_LIMIT:     # 第4层：LLM 全文摘要（兜底）
        messages[:] = compact_history(messages)
    return messages
```

**所有操作都通过 `messages[:] = ...` 原地修改列表**，确保调用方无需重新赋值。

### 第1层：tool_result_budget — 大输出持久化

**针对内容大小的压缩**：仅处理最新一条 user 消息中的 tool_result 块。

算法：
1. 找到最新一条 `role=user` 且 content 为 list 的消息
2. 收集其中所有 `type=tool_result` 的块
3. 计算总字节数，如果超过 `max_bytes`（默认 200KB）
4. 按块大小**从大到小**排序，逐个将大块替换为持久化引用

```python
def persist_large_output(tool_use_id, output):
    if len(output) <= 30000:
        return output          # 够小，保持原样
    path = f".task_outputs/tool-results/{tool_use_id}.txt"
    path.write_text(output)
    return (
        "<persisted-output>\n"
        f"Full output: {path}\n"
        f"Preview:\n{output[:2000]}\n"
        "</persisted-output>"
    )
```

### 第2层：snip_compact — 截断中间消息

**针对消息数量的压缩**：当 `len(messages) > max_messages`（默认 50），保留头部和尾部，中部截断。

```python
def snip_compact(messages, max_messages=50):
    if len(messages) <= max_messages:
        return messages

    head_end = 3                               # 保留开头 3 条
    tail_start = len(messages) - (max_messages - 3)  # 尾部从这条开始

    # 边界保护：不在 tool_use / tool_result 对中间截断
    if head_end > 0 and message_has_tool_use(messages[head_end - 1]):
        while head_end < len(messages) and is_tool_result_message(messages[head_end]):
            head_end += 1
    if tail_start > 0 and is_tool_result_message(messages[tail_start]) \
       and message_has_tool_use(messages[tail_start - 1]):
        tail_start -= 1

    if head_end >= tail_start:
        return messages   # 无需截断

    return (messages[:head_end]
            + [{"role": "user", "content": f"[snipped {n} messages]"}]
            + messages[tail_start:])
```

**边界保护的重要性**：如果恰好截断了 `assistant(tool_use) → user(tool_result)` 中的一半，LLM 会看到孤立的 tool_use 或孤立的 tool_result，导致行为异常。所以截断点必须落在完整的"对话对"之间。

### 第3层：micro_compact — 压缩旧工具结果

**针对旧结果的压缩**：只保留最新的 `KEEP_RECENT_TOOL_RESULTS`（默认 3）条 tool_result 的完整内容。更早的 tool_result 中超过 120 字符的内容剪裁为占位符：

```python
def micro_compact(messages):
    tool_results = collect_all_tool_results(messages)
    if len(tool_results) <= KEEP_RECENT_TOOL_RESULTS:
        return messages

    for _, _, block in tool_results[:-KEEP_RECENT_TOOL_RESULTS]:
        if len(str(block["content"])) > 120:
            block["content"] = "[Earlier tool result compacted. Re-run if needed.]"
    return messages
```

### 第4层：compact_history — LLM 全文摘要

只有前 3 层执行后仍然超过 `CONTEXT_LIMIT` 字节时才触发。步骤：
1. 将全部消息写入 `transcripts/transcript_{timestamp}.jsonl` 存档
2. 取前 80000 字符的对话 JSON，发送给 LLM 做摘要
3. 用 LLM 返回的摘要替换全部历史

```python
messages = [{"role": "user", "content": f"[Compacted]\n\n{summary}"}]
```

### 第5层（被动）：reactive_compact — 应急压缩

不在管线中主动执行，而是当 LLM 返回 `prompt_too_long` 错误后的**被动应急处理**。与第4层类似，但保留最后 5 条消息的尾部：

```python
def reactive_compact(messages):
    save_transcript(messages)       # 存档
    tail_start = max(0, len(messages) - 5)
    summary = summarize(messages[:tail_start])
    return [{"role": "user", "content": f"[Reactive compact]\n\n{summary}"},
            *messages[tail_start:]]
```

---

## 第9章：记忆系统 (Memory)

### 存储

```python
MEMORY_INDEX = WORKSPACE / ".memory" / "MEMORY.md"
```

一个 Markdown 文件，用户手动或 agent 通过工具写入。纯文本，人可读可编辑。

### 加载与注入

`update_context` 读取 `MEMORY.md` 的前 2000 字符，存入 context dict。在 system prompt 组装时注入：

```python
if context.get("memories"):
    sections.append(f"Relevant memories:\n{context['memories']}")
```

### 设计决策

- 内存中不缓存解析结果——每次读取磁盘（文件不大，2000 字符）
- 不自动更新——agent 需要显式调用 `write_file` 写入记忆
- 记忆内容由 agent 自行决定（可以通过 LLM 决策写入什么），但当前教学版本无自动写入逻辑

---

## 第10章：System Prompt 动态组装 (Prompt Assembly)

### 分块定义

```python
PROMPT_SECTIONS = {
    "identity": "You are a coding agent. Act, don't explain.",
    "tools": "Available tools: bash, read_file, ...",
    "workspace": f"Working directory: {WORKDIR}",
    "memory": "Relevant memories are injected below when available.",
}
```

基础块是静态的，拼装时动态注入可变部分。

### 动态拼装

每轮 LLM 调用前，根据当前上下文重新构建 system prompt：

```python
def assemble_system_prompt(context):
    sections = [PROMPT_SECTIONS["identity"],
                PROMPT_SECTIONS["tools"],
                PROMPT_SECTIONS["workspace"]]
    sections.append(f"Current time: {datetime.now().isoformat(timespec='seconds')}")
    sections.append("Skills catalog:\n" + list_skills())
    if context.get("memories"):
        sections.append(f"Relevant memories:\n{context['memories']}")
    if connected_mcp_servers:
        sections.append(f"Connected MCP servers: {', '.join(mcp_names)}")
    return "\n\n".join(sections)
```

动态部分包括：
- 当前时间 — 让 LLM 感知实际时间
- 技能目录 — 列出所有已注册技能
- 记忆 — 从 MEMORY.md 读取
- MCP 服务器列表 — 已连接的 MCP 工具

### 缓存机制

对 context dict 做 JSON 序列化比较，如果与上一轮完全一致，避免重复构建：

```python
_last_key, _last_prompt = None, None

def get_system_prompt(context):
    global _last_key, _last_prompt
    key = json.dumps(context, sort_keys=True, default=str)
    if key == _last_key and _last_prompt:
        return _last_prompt       # 缓存命中
    _last_key = key
    _last_prompt = assemble_system_prompt(context)
    return _last_prompt
```

---

## 第11章：错误恢复系统 (Error Recovery)

### 整体架构

错误恢复分**两层** + 一个非异常路径：

```
LLM 调用
  → with_retry (第1层：处理瞬时错误 429/529)
  → 外层 try/except (第2层：处理 prompt_too_long 等非瞬时错误)
  → stop_reason 检查 (非异常路径：处理 max_tokens 截断)
```

### RecoveryState

```python
class RecoveryState:
    def __init__(self):
        self.has_escalated = False           # max_tokens 是否已升级过
        self.recovery_count = 0               # continuation 重试次数
        self.consecutive_529 = 0              # 连续 529 错误计数
        self.has_attempted_reactive_compact = False  # 是否已应急压缩过
        self.current_model = PRIMARY_MODEL     # 当前使用的模型
```

这个状态对象在 agent_loop 的整个生命周期中保持，跨轮次共享。

### 第1层：with_retry — 瞬时错误自动重试

```python
def with_retry(fn, state):
    for attempt in range(MAX_RETRIES):         # 最多 10 次
        try:
            result = fn()
            state.consecutive_529 = 0          # 成功 → 重置计数器
            return result
        except Exception as e:
            if is_rate_limit(e):               # 429
                sleep(retry_delay(attempt))
                continue
            if is_overloaded(e):               # 529
                state.consecutive_529 += 1
                if consecutive >= 3 and FALLBACK_MODEL:
                    state.current_model = FALLBACK_MODEL   # 切换备用模型
                sleep(retry_delay(attempt))
                continue
            raise                              # 非瞬时错误 → 抛给外层
    raise RuntimeError("Max retries exceeded")
```

**退避算法**：
```python
def retry_delay(attempt):
    base = min(500ms * (2 ** attempt), 32_000ms) / 1000  # 指数增长，32秒封顶
    jitter = random.uniform(0, base * 0.25)                # 0~25% 随机抖动
    return base + jitter
```

延迟表：
| attempt | base | jitter 范围 | 实际延迟 |
|---|---|---|---|
| 0 | 0.5s | 0~0.125s | 0.5~0.625s |
| 1 | 1.0s | 0~0.25s | 1.0~1.25s |
| 2 | 2.0s | 0~0.5s | 2.0~2.5s |
| ... | ... | ... | ... |
| 6+ | 32.0s | 0~8.0s | 32~40s |

**lambda 闭包技巧**：
```python
with_retry(
    lambda mt=max_tokens, mdl=state.current_model:
        client.messages.create(model=mdl, max_tokens=mt, ...),
    state)
```
使用 `lambda mt=max_tokens` 的默认参数技巧，确保每次重试时捕获的是**当前时刻**的变量值，而不是重试时变量的最新值。因为 `max_tokens` 可能在重试期间被外层 escalate 修改。

### 第2层：prompt_too_long 应急处理

```python
except Exception as e:
    if is_prompt_too_long_error(e) and not state.has_attempted_reactive_compact:
        messages[:] = reactive_compact(messages)
        state.has_attempted_reactive_compact = True
        continue
    # 仍然失败 → 报错退出
```

`has_attempted_reactive_compact` 确保只压缩一次，防止无限循环。

### max_tokens 截断恢复（非异常路径）

```python
if response.stop_reason == "max_tokens":
    if not state.has_escalated:
        max_tokens = ESCALATED_MAX_TOKENS   # 8K → 64K
        state.has_escalated = True
        continue                            # 不保存截断输出，直接重试
    # 已升级过 → 保存输出 + continuation prompt
    messages.append({"role": "assistant", "content": response.content})
    if state.recovery_count < MAX_RECOVERY_RETRIES:
        messages.append({"role": "user", "content": CONTINUATION_PROMPT})
        state.recovery_count += 1
        continue
    return  # 超过重试次数，放弃
```

正常完成后重置：
```python
max_tokens = DEFAULT_MAX_TOKENS
state.has_escalated = False
```

---

## 第12章：任务系统 (Task System)

### 数据结构

```python
@dataclass
class Task:
    id: str                    # "task_{timestamp}_{random4digit}"
    subject: str               # 一行摘要
    description: str           # 详细描述
    status: str                # "pending" | "in_progress" | "completed"
    owner: str | None          # 认领者标识
    blockedBy: list[str]       # 前置依赖的 task_id 列表
    worktree: str | None       # 绑定的 git worktree 名称
```

### 持久化

每个 task 是一个独立的 JSON 文件：`.tasks/task_{id}.json`

序列化使用 `dataclasses.asdict()`：
```python
def save_task(task):
    path = f".tasks/{task.id}.json"
    path.write_text(json.dumps(asdict(task), indent=2))
```

所有修改立即写入磁盘，不依赖内存缓存。这意味着 task 状态在 agent 重启后不丢失，不同线程/teammate 可以直接读文件共享状态。

### 状态机

```
pending  ──claim──→  in_progress  ──complete──→  completed
  ↑                    ↑                         ↑
  无人认领              已被人认领                  完成
```

### 依赖解析 (`can_start`)

```python
def can_start(task_id):
    task = load_task(task_id)
    for dep_id in task.blockedBy:
        if not file_exists(f".tasks/{dep_id}.json"):
            return False                         # 依赖不存在
        if load_task(dep_id).status != "completed":
            return False                         # 依赖未完成
    return True
```

- 只支持 AND 关系（所有依赖完成才可开始）
- 依赖检查在 `claim_task` 时执行一次（而非持续轮询）
- 引用不存在的 task_id 视为未满足

### `claim_task` 校验顺序

```python
def claim_task(task_id, owner="agent"):
    task = load_task(task_id)
    if task.status != "pending":                 # 1. 状态校验
        return "cannot claim — not pending"
    if task.owner:
        return "cannot claim — already owned"    # 2. 所有权校验
    if not can_start(task_id):
        return "cannot start — blocked by X"     # 3. 依赖校验

    task.owner = owner
    task.status = "in_progress"
    save_task(task)
    return f"Claimed {task_id}"
```

顺序很重要：状态优先于依赖检查，避免浪费计算。

### `complete_task` 解锁通知

```python
def complete_task(task_id):
    task.status = "completed"
    save_task(task)

    # 扫描所有 pending task，找出被当前 task 解锁的
    unblocked = [t.subject for t in list_tasks()
                 if t.status == "pending"
                 and t.blockedBy
                 and can_start(t.id)]

    msg = f"Completed {task_id}"
    if unblocked:
        msg += f"\nUnblocked: {', '.join(unblocked)}"
    return msg
```

---

## 第13章：后台任务系统 (Background Tasks)

### 触发条件

工具调用满足以下任一条件时放入后台线程：

1. **显式请求**：`bash` 工具传入 `run_in_background=True`
2. **自动检测**：命令包含慢操作关键词：
   ```
   install, build, test, deploy, compile,
   docker build, pip install, npm install,
   cargo build, pytest, make
   ```

### 执行模型

```python
background_tasks: dict[str, dict] = {}       # bg_id → {status, command, ...}
background_results: dict[str, str] = {}       # bg_id → output
background_lock = threading.Lock()

def start_background_task(block, handlers):
    bg_id = f"bg_{counter:04d}"
    background_tasks[bg_id] = {
        "tool_use_id": block.id,
        "command": block.input.get("command", ""),
        "status": "running",
    }

    def worker():
        handler = handlers.get(block.name)
        result = call_tool_handler(handler, block.input, block.name)
        trigger_hooks("PostToolUse", block, result)      # 后台也触发 hook
        with background_lock:
            background_tasks[bg_id]["status"] = "completed"
            background_results[bg_id] = str(result)

    threading.Thread(target=worker, daemon=True).start()
    # 立即返回占位 tool_result
    return (f"[Background task {bg_id} started] "
            f"Result will arrive as a task_notification.")
```

关键设计：
- 立即给 LLM 返回占位信息，不让主循环阻塞
- 后台线程也会触发 `PostToolUse` hook（大输出警告等）
- daemon 线程：主进程退出时自动终止

### 结果回注

在每轮 agent_loop 开始前，检查已完成的后台任务：

```python
def inject_background_notifications(messages):
    ready = collect_completed_background_tasks()
    for bg_id, output in ready:
        messages.append({"role": "user", "content": [
            {"type": "text", "text": f"""<task_notification>
  <task_id>{bg_id}</task_id>
  <status>completed</status>
  <command>{command}</command>
  <summary>{output[:200]}</summary>
</task_notification>"""}
        ]})
```

通知格式使用 XML 结构，让 LLM 清楚地识别这是系统的异步通知。

---

## 第14章：Cron 定时调度 (Cron Scheduler)

### 表达式支持

标准 5 字段 cron：`minute hour day-of-month month day-of-week`

```python
# 字段规则
*        匹配所有值
*/N      每 N 单位触发一次
A,B,C    逗号分隔的多值
A-B      范围
# dom 和 dow 同时为非 * → OR 关系
```

### cron_matches 匹配算法

```python
def cron_matches(cron_expr, dt):
    minute, hour, dom, month, dow = cron_expr.strip().split()
    m = match_field(minute, dt.minute)
    h = match_field(hour, dt.hour)
    month_ok = match_field(month, dt.month)

    if not (m and h and month_ok):
        return False

    if dom == "*" and dow == "*":
        return True
    if dom == "*":
        return match_field(dow, dow_val)
    if dow == "*":
        return match_field(dom, dt.day)
    return match_field(dom, dt.day) or match_field(dow, dow_val)
```

### 调度循环

```python
def cron_scheduler_loop():
    while True:
        sleep(1)
        now = datetime.now()
        marker = now.strftime("%Y-%m-%d %H:%M")    # 分钟级去重标记
        for job in scheduled_jobs.values():
            if cron_matches(job.cron, now) and _last_fired[job.id] != marker:
                cron_queue.append(job)
                _last_fired[job.id] = marker
                if not job.recurring:
                    del scheduled_jobs[job.id]      # 一次性任务，执行后删除
```

**去重机制**：`_last_fired` 记录上次触发的时间标记（精确到分钟）。同一分钟内即使循环多次，不会重复触发同一 job。

### 持久化

`durable=True` 的 job 在创建/取消时写入 `.scheduled_tasks.json`：

```python
@dataclass
class CronJob:
    id: str
    cron: str
    prompt: str          # 触发时作为 user 消息注入
    recurring: bool
    durable: bool        # True = 重启后恢复
```

启动时调用 `load_durable_jobs()` 从磁盘恢复。

### 与 agent_loop 集成

主循环每轮开始消费 cron 队列：

```python
fired = consume_cron_queue()
for job in fired:
    messages.append({"role": "user",
                     "content": f"[Scheduled] {job.prompt}"})
```

另外，`cron_autorun_loop` 线程在没有用户输入时也能自动消费队列并驱动 agent_loop。

---

## 第15章：Agent 团队通信 (MessageBus)

### 核心设计

使用追加式 JSONL 文件实现 agent 间的异步点对点通信。无需消息队列、无需 Redis。

### 实现

```python
class MessageBus:
    def send(self, from_agent, to_agent, content,
             msg_type="message", metadata=None):
        msg = {
            "from": from_agent,
            "to": to_agent,
            "content": content,
            "type": msg_type,
            "ts": time.time(),
            "metadata": metadata or {}
        }
        inbox = MAILBOX_DIR / f"{to_agent}.jsonl"
        with open(inbox, "a") as f:
            f.write(json.dumps(msg) + "\n")

    def read_inbox(self, agent):
        inbox = MAILBOX_DIR / f"{agent}.jsonl"
        if not inbox.exists():
            return []
        msgs = [json.loads(line)
                for line in inbox.read_text().splitlines()
                if line.strip()]
        inbox.unlink()           # 读取后删除
        return msgs
```

### 设计要点

| 特性 | 实现方式 | 理由 |
|---|---|---|
| 持久性 | 文件系统 | 崩溃后消息不丢 |
| 消费语义 | read + unlink | 防止重复处理（at-most-once） |
| 容错性 | JSONL（每行独立） | 某行损坏不影响其他消息 |
| 可审计 | 纯文本文件 | 可用任何编辑器查看历史 |
| 路由 | `metadata.request_id` | 支持请求-响应匹配（见第16章） |

### 消息类型

| type | 用途 |
|---|---|
| `message` | 普通文本消息 |
| `result` | teammate 完成工作后的摘要 |
| `plan_approval_request` | teammate 请求 lead 审批计划 |
| `plan_approval_response` | lead 返回审批结果 |
| `shutdown_request` | 请求 teammate 关闭 |
| `shutdown_response` | teammate 确认关闭 |

---

## 第16章：团队协议系统 (Team Protocols)

### 核心设计

协议系统在 MessageBus 之上构建**有状态的请求-响应语义**，用于需要对方确认的操作（计划审批、关闭请求）。

### ProtocolState

```python
@dataclass
class ProtocolState:
    request_id: str          # "req_{6位随机数}"
    type: str                # "shutdown" | "plan_approval"
    sender: str
    target: str
    status: str              # "pending" | "approved" | "rejected"
    payload: str             # 请求的附加数据（如计划文本）
    created_at: float
```

### 完整流程（以 shutdown 为例）

**1. 发起请求（lead 端）**
```python
def run_request_shutdown(teammate):
    req_id = new_request_id()
    pending_requests[req_id] = ProtocolState(
        request_id=req_id, type="shutdown",
        sender="lead", target=teammate,
        status="pending", payload="")
    BUS.send("lead", teammate, "Shut down.", "shutdown_request",
             {"request_id": req_id})
    return f"Shutdown request sent to {teammate}"
```

**2. 接收并响应（teammate 端）**
```python
# teammate 的 handle_inbox_message 方法中
if msg_type == "shutdown_request":
    req_id = msg["metadata"]["request_id"]
    BUS.send(name, "lead", "Shutting down.", "shutdown_response",
             {"request_id": req_id, "approve": True})
    return True   # 触发 shutdown
```

**3. 响应匹配（lead 端）**
```python
def match_response(response_type, request_id, approve):
    state = pending_requests.get(request_id)
    if not state:
        return   # 请求不存在
    # 类型校验：不能被跨类型的响应匹配
    if state.type == "shutdown" and response_type != "shutdown_response":
        return
    if state.type == "plan_approval" and response_type != "plan_approval_response":
        return
    state.status = "approved" if approve else "rejected"
```

**4. 消费收件箱时自动匹配**
```python
def consume_lead_inbox(route_protocol=True):
    msgs = BUS.read_inbox("lead")
    if route_protocol:
        for msg in msgs:
            meta = msg.get("metadata", {})
            req_id = meta.get("request_id", "")
            msg_type = msg.get("type", "")
            if req_id and msg_type.endswith("_response"):
                match_response(msg_type, req_id, meta.get("approve", False))
    return msgs
```

### 安全设计

- `match_response` 校验 `response_type` 必须与原始请求的 `type` 对应，防止一个 `shutdown_response` 误匹配为 `plan_approval`
- `request_id` 是 6 位随机数，防止碰撞
- 响应只在消费收件箱时路由，不在接收时路由——确保消息处理和协议路由的解耦

---

## 第17章：自治 Agent (Autonomous Agents)

### 核心设计

每个 teammate 是一个独立的 daemon 线程，运行自己的 agent loop。与主 agent 的区别：
- 不是交互式（不等待 stdin 输入）
- 自动从 task 池认领工作
- 通过 MessageBus 与 lead 通信

### spawn_teammate_thread 完整流程

```python
def spawn_teammate_thread(name, role, prompt):
    # 1. 去重检查
    if name in active_teammates:
        return "already exists"

    protocol_ctx = {"waiting_plan": None}   # 计划审批门状态

    def run():
        wt_ctx = {"path": None}             # worktree 上下文
        messages = [{"role": "user", "content": prompt}]

        while True:
            # 2. 收件箱优先处理（协议消息优先级最高）
            inbox = BUS.read_inbox(name)
            for msg in inbox:
                handled = handle_inbox_message(msg, ...)
                if handled:                 # shutdown → 退出
                    return

            # 3. 模型推理 + 工具调用（最多 10 轮）
            for _ in range(10):
                if protocol_ctx["waiting_plan"]:
                    time.sleep(IDLE_POLL_INTERVAL)  # 等待审批，不推理
                    continue

                response = client.messages.create(...)
                messages.append({"role": "assistant", "content": response.content})

                if not has_tool_use(response.content):
                    break   # 没有工具调用，本批推理结束

                # 执行工具...
                # 特别注意 submit_plan 工具：
                if block.name == "submit_plan":
                    send_plan_approval_request()
                    protocol_ctx["waiting_plan"] = request_id
                    break    # 暂停，等待审批

            # 4. 10 轮后进入 idle 模式
            idle_result = idle_poll(name, messages, ...)
            if idle_result in ("shutdown", "timeout"):
                break

        # 5. 退出前发送结果
        BUS.send(name, "lead", summary, "result")

    active_teammates[name] = True
    threading.Thread(target=run, daemon=True).start()
```

### idle_poll — 空闲轮询策略

```python
def idle_poll(agent_name, messages, ...):
    for _ in range(IDLE_TIMEOUT // IDLE_POLL_INTERVAL):  # 60/5 = 12 次
        time.sleep(IDLE_POLL_INTERVAL)                    # 5 秒

        # 优先级1：检查收件箱
        inbox = BUS.read_inbox(agent_name)
        if inbox:
            for msg in inbox:
                if msg["type"] == "shutdown_request":
                    respond_shutdown(); return "shutdown"
            messages.append({"role": "user",
                             "content": json.dumps(inbox)})
            return "work"    # 有消息 → 回去推理

        # 优先级2：自动认领 task
        unclaimed = scan_unclaimed_tasks()   # status=pending & owner=None & can_start
        if unclaimed:
            task = unclaimed[0]
            claim_task(task["id"], agent_name)
            messages.append({"role": "user",
                             "content": f"Task {task['id']}: {task['subject']}"})
            return "work"    # 认领成功 → 回去推理

    return "timeout"         # 超时 → 退出
```

### 计划审批门 (Plan Approval Gate)

```python
protocol_ctx = {"waiting_plan": None}

# teammate 调用 submit_plan 时
protocol_ctx["waiting_plan"] = request_id   # 关门
BUS.send(name, "lead", plan, "plan_approval_request", {"request_id": request_id})

# agent_loop 中检查
if protocol_ctx["waiting_plan"]:
    time.sleep(IDLE_POLL_INTERVAL)   # 暂停推理，只轮询收件箱
    continue                         # 等待 plan_approval_response

# 收到审批响应时（handle_inbox_message 中）
if msg_type == "plan_approval_response":
    if request_id == protocol_ctx["waiting_plan"]:
        protocol_ctx["waiting_plan"] = None   # 开门
    messages.append({"role": "user",
                     "content": "[Plan approved]" if approved
                                else "[Plan rejected]"})
```

门的核心作用：**teammate 在等待审批期间不进行模型推理**，防止在无监督的情况下执行高风险操作。

### 自主认领策略

`scan_unclaimed_tasks()` 扫描 `.tasks/` 目录，找 `status=pending && owner=None && can_start=True` 的 task。认领**第一个**符合条件的（不是随机、不是最优匹配）。

---

## 第18章：Worktree 工作隔离 (Worktree Isolation)

### 核心理念

为每个 task 创建独立的 git worktree，实现文件系统级别的**工作空间隔离**——不同 task 在不同目录下并行工作，互不干扰。

### 创建流程

```python
def create_worktree(name, task_id=""):
    # 1. 名称安全校验
    validate_worktree_name(name)     # 只允许 [A-Za-z0-9._-]{1,64}，禁止 . 和 ..

    # 2. task 存在性校验
    if task_id:
        try: load_task(task_id)
        except: return "task not found"

    # 3. 防重复创建
    path = WORKTREES_DIR / name
    if path.exists():
        return "already exists"

    # 4. git worktree add
    ok, result = run_git(["worktree", "add", str(path),
                          "-b", f"wt/{name}", "HEAD"])
    if not ok:
        return f"Git error: {result}"

    # 5. 绑定 task
    if task_id:
        task.worktree = name; save_task(task)

    # 6. 事件审计
    log_event("create", name, task_id)
    return f"Worktree '{name}' created at {path}"
```

### 名称验证

```python
VALID_WT_NAME = re.compile(r'^[A-Za-z0-9._-]{1,64}$')

def validate_worktree_name(name):
    if not name:        return "cannot be empty"
    if name in (".", ".."): return "invalid name"
    if not VALID_WT_NAME.match(name):
        return "only letters, digits, dots, underscores, dashes (1-64 chars)"
    return None
```

原因：worktree 名称变成文件系统路径，不安全的字符可能导致路径遍历攻击。

### 删除保护

```python
def remove_worktree(name, discard_changes=False):
    if not discard_changes:
        # 检查未提交变更
        files = count_modified_files(path)    # git status --porcelain
        commits = count_unpushed_commits(path) # git log @{push}..HEAD --oneline
        if files > 0 or commits > 0:
            return f"Has {files} file(s), {commits} commit(s). "
                   "Use discard_changes=true or keep_worktree."
    # 执行删除
    run_git(["worktree", "remove", str(path), "--force"])
    run_git(["branch", "-D", f"wt/{name}"])
    log_event("remove", name)
```

删除前自动检查工作目录是否有未保存的工作，防止误删。

### keep_worktree

如果用户想要保留 worktree 进行手动审查（不自动删除），调用 `keep_worktree(name)`。它只记录事件，不做任何 git 操作。

### 与 Teammate 的集成

Teammate 认领 worktree 绑定的 task 后，所有文件操作自动切换到该 worktree 目录：

```python
# Teammate 内部维护的上下文
wt_ctx = {"path": None}

def _run_bash(command):
    return run_bash(command, cwd=wt_ctx["path"])

def _run_write(path, content):
    return run_write(path, content, cwd=wt_ctx["path"])

# 认领 task 时自动绑定 worktree
task = load_task(task_id)
if task.worktree:
    wt_ctx["path"] = str(WORKTREES_DIR / task.worktree)
```

---

## 第19章：MCP 插件系统 (Model Context Protocol)

### 客户端模型

```python
class MCPClient:
    def __init__(self, name):
        self.name = name
        self.tools: list[dict] = []          # 服务端暴露的工具 schema
        self._handlers: dict[str, callable] = {}  # 工具名 → 执行函数

    def register(self, tool_defs, handlers):
        self.tools = tool_defs
        self._handlers = handlers

    def call_tool(self, tool_name, args):
        handler = self._handlers.get(tool_name)
        if not handler:
            return f"MCP error: unknown tool '{tool_name}'"
        return handler(**args)
```

### 工具池动态合并

```python
mcp_clients: dict[str, MCPClient] = {}

def assemble_tool_pool():
    tools = list(BUILTIN_TOOLS)
    handlers = dict(BUILTIN_HANDLERS)

    for server_name, client in mcp_clients.items():
        safe_server = normalize_mcp_name(server_name)
        for tool_def in client.tools:
            safe_tool = normalize_mcp_name(tool_def["name"])
            prefixed = f"mcp__{safe_server}__{safe_tool}"

            tools.append({
                "name": prefixed,
                "description": tool_def.get("description", ""),
                "input_schema": tool_def.get("inputSchema", {}),
            })

            # lambda 闭包捕获 current client 和 tool_name
            handlers[prefixed] = (
                lambda args, c=client, t=tool_def["name"], **kw:
                    c.call_tool(t, kw))
    return tools, handlers
```

### 命名规范

- 前缀格式：`mcp__{server}__{tool}`
- 名称归一化：非 `[a-zA-Z0-9_-]` 字符替换为下划线
- 这保证了 MCP 工具与内置工具命名空间隔离，不会发生名称冲突
- LLM 在 system prompt 中被告知：`"MCP tools are prefixed mcp__{server}__{tool}."`

### Lambda 闭包注意事项

```python
handlers[prefixed] = lambda args, c=client, t=tool_def["name"], **kw: c.call_tool(t, kw)
```

必须使用默认参数 `c=client, t=tool_def["name"]` 捕获循环变量的**当前值**，而不是延迟求值的引用。否则所有 lambda 都会指向循环中最后一个 client 和 tool_name。

---

## 第20章：全系统集成 (Comprehensive Integration)

### 完整 agent_loop 流程

```python
def agent_loop(messages, context):
    tools, handlers = assemble_tool_pool()        # 合并 MCP 工具
    state = RecoveryState()
    max_tokens = DEFAULT_MAX_TOKENS
    global rounds_since_todo

    while True:
        # ========== 阶段1：注入外部事件 ==========
        fired = consume_cron_queue()               # [14] 定时触发
        for job in fired:
            messages.append({"role": "user", "content": f"[Scheduled] {job.prompt}"})

        inject_background_notifications(messages)  # [13] 后台任务完成通知

        if rounds_since_todo >= 3:                 # [5] 主动提醒
            messages.append({"role": "user", "content": "<reminder>Update todos.</reminder>"})
            rounds_since_todo = 0

        # ========== 阶段2：上下文准备 ==========
        prepare_context(messages)                  # [8] 4层压缩管线
        context = update_context(context, messages) # [9] 刷新记忆/[19] MCP状态/[17] teammate状态
        tools, handlers = assemble_tool_pool()      # [19] 重新合并（MCP 可能新增）

        # ========== 阶段3：LLM 调用 ==========
        try:
            system = assemble_system_prompt(context)  # [10] 动态拼接 prompt
            response = with_retry(                    # [11] 第1层错误恢复：429/529
                lambda: client.messages.create(
                    model=state.current_model,
                    system=system,
                    messages=messages,
                    tools=tools,                       # [2][19] 合并后的工具池
                    max_tokens=max_tokens),
                state)
        except Exception as e:
            if is_prompt_too_long_error(e) and not state.has_attempted_reactive_compact:
                messages[:] = reactive_compact(messages)  # [8] 应急压缩
                state.has_attempted_reactive_compact = True
                continue
            # 不可恢复
            messages.append({"role": "assistant", "content": [{"type": "text", "text": f"[Error] {e}"}]})
            return

        # ========== 阶段4：max_tokens 恢复 ==========
        if response.stop_reason == "max_tokens":
            if not state.has_escalated:        # [11] 首次 → 升级 token 预算
                max_tokens = ESCALATED_MAX_TOKENS
                state.has_escalated = True
                continue
            # 已升级 → continuation prompt
            messages.append({"role": "assistant", "content": response.content})
            if state.recovery_count < MAX_RECOVERY_RETRIES:
                messages.append({"role": "user", "content": CONTINUATION_PROMPT})
                state.recovery_count += 1
                continue
            return

        # 正常 → 重置
        max_tokens = DEFAULT_MAX_TOKENS
        state.has_escalated = False

        # ========== 阶段5：工具执行 ==========
        messages.append({"role": "assistant", "content": response.content})
        if not has_tool_use(response.content):
            trigger_hooks("Stop", messages)    # [4] 停止 hook
            return

        results = []
        compacted_now = False
        for block in response.content:
            if block.type != "tool_use":
                continue

            # 特判：用户主动请求压缩
            if block.name == "compact":
                messages[:] = compact_history(messages)   # [8]
                messages.append({"role": "user", "content": "[Compacted.]"})
                compacted_now = True
                break

            # Hook 拦截（权限等）
            blocked = trigger_hooks("PreToolUse", block)  # [3][4]
            if blocked:
                results.append({"type": "tool_result",
                                "tool_use_id": block.id, "content": str(blocked)})
                continue

            # 后台任务
            if should_run_background(block.name, block.input):  # [13]
                bg_id = start_background_task(block, handlers)
                results.append({"type": "tool_result",
                                "tool_use_id": block.id,
                                "content": f"[Background task {bg_id} started]"})
                continue

            # 正常执行
            handler = handlers.get(block.name)
            output = call_tool_handler(handler, block.input, block.name)  # [2]
            trigger_hooks("PostToolUse", block, output)  # [4]

            if block.name == "todo_write":
                rounds_since_todo = 0        # [5]
            else:
                rounds_since_todo += 1

            results.append({"type": "tool_result",
                            "tool_use_id": block.id, "content": output})

        if compacted_now:
            continue

        # ========== 阶段6：结果回传 ==========
        messages.append({"role": "user", "content": results})
        # 循环回到阶段1
```

### 各组件间的数据流

```
┌─────────────────────────────────────────────────────────────────────┐
│                           agent_loop                                │
│                                                                     │
│  cron_autorun ──→ cron_queue ──→ 阶段1 ──→ messages               │
│  background threads ──→ background_results ──→ 阶段1 ──→ messages │
│                                                                     │
│  阶段2: prepare_context  (4层压缩管线)                              │
│         update_context   (memory + MCP + teammate 状态)              │
│         assemble_tool_pool (内置 + MCP 合并)                        │
│                                                                     │
│  阶段3: with_retry → LLM API ──429/529──→ 重试 / 切换模型          │
│                       ──prompt_too_long──→ reactive_compact         │
│                       ──max_tokens──→ escalate / continue           │
│                                                                     │
│  阶段5: PreToolUse hooks → permission + log                         │
│         tool execution → background / normal                        │
│         PostToolUse hooks → large_output warning                    │
│                                                                     │
│  teammate threads ──→ MessageBus ←── lead                          │
│       ↕                                                         ↕  │
│  ProtocolState                      ProtocolState                   │
│  (等待approval)                     (等待response)                 │
│                                                                     │
│  Task System (.tasks/*.json) ← 2 方可读写                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 线程安全

主 agent_loop 和 cron_autorun_loop 通过 `agent_lock` 互斥访问共享的 `history` 和 `context`：

```python
agent_lock = threading.Lock()

# 用户输入触发
with agent_lock:
    agent_loop(history, context)

# cron 自动触发
with agent_lock:
    agent_loop(history, context)
```

### 组件加载顺序

```
程序启动
  → scan_skills()           [7]  扫描 skills/ 目录
  → load_durable_jobs()     [14] 恢复持久化的 cron jobs
  → cron_scheduler_loop()   [14] 启动 cron 调度线程
  → cron_autorun_loop()     [20] 启动 cron 自动运行线程

用户输入
  → trigger_hooks("UserPromptSubmit")  [4]
  → agent_loop()                       [1][20]
     → prepare_context()               [8]
     → update_context()                [9]
     → assemble_tool_pool()            [19]
     → assemble_system_prompt()        [10]
     → with_retry + try/except         [11]
     → tool execution with hooks       [2][3][4][5][6][7][12][13][17][18][19]
```

---

## 附录：可直接复用的架构决策总结

| # | 设计决策 | 理由 |
|---|---|---|
| 1 | 主循环 `while True` + 事件注入 | 统一的迭代模型，外部事件通过队列/通知注入 |
| 2 | 工具定义与 handler 双表分离 | LLM 看 schema，Python 执行函数，互不污染 |
| 3 | Deny list + Destructive list 两层权限 | 硬拒绝自动拦截 + 软确认人工判断 |
| 4 | Hook 管道的「首个非 None = 拦截」 | 简单且支持链式处理，注册顺序 = 优先级 |
| 5 | rounds_since_todo 主动提醒 | 简单计数器驱动，不依赖 LLM 自主记录状态 |
| 6 | Subagent 独立 system prompt + 精简工具集 | 防止能力泄露和递归 |
| 7 | 技能 = 子目录 + SKILL.md + YAML frontmatter | 易扩展，人可维护 |
| 8 | 压缩在 LLM 调用前而非错误后 | 主动防御，减少 API 错误 |
| 9 | MEMORY.md 文件持久化 | 零依赖；人可编辑；重启不丢失 |
| 10 | Prompt 分块 + 动态拼接 + JSON 序列化缓存 | 避免重复构建；context 变化自动更新 |
| 11 | with_retry 429/529 两层处理 + 模型切换 | 瞬时错误退避；重载自动切备用模型；非瞬时错误立即抛 |
| 12 | Task 状态文件持久化 + 依赖图 | 崩溃可恢复；多 agent 共享无锁 |
| 13 | 慢操作关键词检测 + 后台线程 + 结果 XML 回注 | 主循环不阻塞；模型通过通知感知异步完成 |
| 14 | Cron 5 字段 + 分钟去重 + 文件持久化 | 标准 cron 语法；防重复；重启恢复 |
| 15 | JSONL 文件邮箱 = 消息队列 | 零依赖；可审计；解析容错；at-most-once 消费 |
| 16 | ProtocolState + request_id 匹配请求响应 | 防止跨类型响应误匹配 |
| 17 | idle_poll: 收件 > 自动认领 > 超时退出 | 清晰的优先级；daemon 线程不会永久空转 |
| 18 | Worktree 名称正则校验 + 删除前检查变更 | 防路径注入；防误删工作 |
| 19 | MCP 工具前缀命名空间 + lambda 闭包捕获 | 隔离内置工具；防止循环变量延迟绑定问题 |
| 20 | agent_lock 保护共享状态 | 多线程安全访问 messages/context |
