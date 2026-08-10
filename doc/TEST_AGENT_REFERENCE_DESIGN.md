# 测试 Agent 参考设计文档
### 借鉴 DeerFlow 架构的亮点设计（可实施规范）

> 本文档是一份**实现规范**，不是概念介绍。目标是让另一位开发者（或 AI 编码代理）拿着本文档，就能在你的项目中落地一个生产级的"测试 Agent"。
>
> 每个设计条目统一按五段结构撰写：
> 1. **设计意图** —— 为什么需要它，解决什么问题；
> 2. **规范** —— 接口签名、数据结构、行为语义（这是实现者必须遵守的部分）；
> 3. **边界条件** —— 易错点、并发、失败、异常路径；
> 4. **测试场景映射** —— 该设计在"测试 Agent"里的具体用途；
> 5. **出处** —— DeerFlow 源码位置，供实现时核对原语义。
>
> 约定：所有 Python 签名均基于 Python 3.12 + 类型标注。若你的项目不用 LangChain/LangGraph，只需在"状态 + 中间件 + 工具"三层保持同样语义，框架可替换。

---

## 目录

1. [目标架构与分层](#1-目标架构与分层)
2. [沙箱执行层](#2-沙箱执行层)
3. [工具结果结构化元数据（核心）](#3-工具结果结构化元数据核心)
4. [中间件链（洋葱模型）](#4-中间件链洋葱模型)
5. [会话状态与 reducer](#5-会话状态与-reducer)
6. [子代理并发执行](#6-子代理并发执行)
7. [上下文压缩与持久化](#7-上下文压缩与持久化)
8. [运行生命周期与事件流](#8-运行生命周期与事件流)
9. [配置系统](#9-配置系统)
10. [安全设计](#10-安全设计)
11. [契约与版本演进](#11-契约与版本演进)
12. [可观测性与工程质量](#12-可观测性与工程质量)
13. [实施路线图](#13-实施路线图)
14. [附录：关键语义速查表](#附录关键语义速查表)

---

## 1. 目标架构与分层

### 1.1 设计意图

测试 Agent 的核心诉求与 DeerFlow 高度同构：**读代码 → 改文件 → 执行命令（跑测试）→ 读大量工具输出 → 归纳 → 再改**。这是一个"读-改-验证"的循环，天然需要以下横切能力：

- 安全执行任意命令（跑 pytest / 起服务 / 装依赖）；
- 处理海量、不可信、可能爆量的工具输出；
- 并发执行多个独立测试目标；
- 失败恢复（测试挂了不等于 agent 挂了）；
- 成本控制（token / 运行时长预算）；
- 失败分类（flaky / 失败 / 错误 / 超时）的语义统一。

分层的目的：让**核心执行引擎（harness）**与**业务入口（应用层）**解耦，同一个引擎可被 Web API、CLI、CI 触发器复用，且依赖方向单向（业务层依赖引擎，引擎永不反向依赖业务层）。

### 1.2 规范

**严格的两层结构 + 依赖单向性**：

```
my-test-agent/
├── packages/
│   └── harness/                # 引擎层：import 前缀 myta.*（对外可发布的库）
│       ├── sandbox/            # 命令执行抽象
│       ├── middlewares/        # 横切中间件
│       ├── state.py            # 会话状态 + reducer
│       ├── executor/           # 子代理/子任务并发执行
│       ├── tools/              # 内置工具
│       ├── config/             # 配置系统
│       ├── context/            # 上下文压缩与持久化
│       └── runtime/            # 运行生命周期 + 事件流
└── app/                        # 应用层：import 前缀 app.*（Web API、CLI、CI）
```

**强制约束**：

1. `app` 可以 `import` harness，harness **永不** `import app`。用一个测试在 CI 中强制：扫描 harness 目录源码，若出现 `from app` / `import app` 即失败。
2. harness 内部轻量模块（类型、配置、注册表）不得引入重型模块（图构建、执行器），避免状态/模式导入时拖入整棵依赖树。
3. 所有跨层协议（状态枚举、工具元数据、事件类型）必须**下沉到 harness 或独立 contracts 包**，双方都从同一处导入。

### 1.3 边界条件

- 依赖防火墙测试必须**扫描源码文本**而非只靠运行时，因为 `TYPE_CHECKING` / 延迟导入会绕过运行时检查。
- 若用 Python，用 `sys.modules` 模拟切走重型模块，保证轻量模块可单测（无需配置文件）。

### 1.4 测试场景映射

- Web 界面、CLI（`myta run --target tests/api`）、CI 评论触发器三个入口都驱动同一个 harness——只有中间件/工具配不同的启动上下文。

### 1.5 出处

- `backend/AGENTS.md` §"Harness / App Split"；`backend/tests/test_harness_boundary.py`。

---

## 2. 沙箱执行层

### 2.1 设计意图

测试 Agent 必须能安全、可预期地执行任意命令：跑测试、装依赖、启动被测服务。需要解决：**挂死的命令、后台进程、输出爆量、密钥泄漏、路径语义统一**五大问题。

### 2.2 规范

#### (a) 抽象接口

```python
class Sandbox(ABC):
    def execute_command(
        self,
        command: str,
        env: dict[str, str] | None = None,   # 每调用注入的请求级环境变量（见 §10 密钥治理）
        timeout: float | None = None,         # 每调用墙钟超时（秒）
    ) -> str:
        """执行命令，返回合并后的 stdout+stderr。"""

    def read_file(self, path: str) -> str: ...
    def write_file(self, path: str, content: str, append: bool = False) -> None: ...
    def list_dir(self, path: str, max_depth: int = 2) -> list[str]: ...
    def download_file(self, path: str) -> bytes: ...
    def update_file(self, path: str, content: bytes) -> None: ...
    def glob(self, path, pattern, *, include_dirs=False, max_results=200) -> tuple[list[str], bool]: ...
    def grep(self, path, pattern, *, glob=None, literal=False, case_sensitive=False,
             max_results=100) -> tuple[list[GrepMatch], bool]: ...
```

#### (b) `execute_command` 的强制性执行语义

| 关注点 | 规范 | 为什么 |
|---|---|---|
| **超时与进程组** | 命令在独立进程组运行；`timeout` 到点后杀死**整个进程组**（含孙进程）；返回一条给模型的"超时提示" | 测试最怕挂死：`pytest` 死循环、等待 stdin、起 server 不退。只杀父进程会留下孤儿 |
| **stdin** | 指向 `/dev/null`（立即 EOF） | 防止命令因等待用户输入而永久阻塞 |
| **后台进程** | 命令含 `&` 或 `nohup` 时立即返回；后台未重定向的输出用**有界管道排空线程**持续读取，不落匿名临时文件 | 防止后台进程输出撑爆管道导致父进程死锁；也避免临时文件无限膨胀 |
| **输出捕获** | stdout/stderr 合并返回；工具层对返回串再设字节上限 | 测试日志爆量是常态，必须在回模型上下文前截断 |
| **提示引导** | `bash` 工具描述里明确告诉模型"长驻进程要放后台、不要在前台等它" | 从源头减少挂起 |

#### (c) 环境策略（继承环境擦除 + 注入）

```python
# env_policy.py
_SECRET_NAME_PATTERNS = ("*KEY*", "*SECRET*", "*TOKEN*", "*PASS*", "*CREDENTIAL*", "*DSN*")
_BLOCKED_EXACT_NAMES = frozenset({
    "DATABASE_URL", "REDIS_URL", "MONGODB_URI", "MONGO_URL", "AMQP_URL",
    "POSTGRES_URL", "POSTGRESQL_URL", "MYSQL_URL", "CLICKHOUSE_URL",
    "CONNECTION_STRING", "CONN_STR", "GH_PAT", "GITHUB_PAT",
    "MYSQL_PWD", "REDISCLI_AUTH", "REDIS_AUTH", "PGSERVICEFILE", ...
})

def is_blocked_env_name(name: str) -> bool:
    upper = name.upper()
    return upper in _BLOCKED_EXACT_NAMES or any(
        fnmatch.fnmatchcase(upper, p) for p in _SECRET_NAME_PATTERNS
    )

def build_sandbox_env(injected: dict[str, str] | None = None) -> dict[str, str]:
    env = {k: v for k, v in os.environ.items() if not is_blocked_env_name(k)}
    if injected:
        env.update(injected)          # 注入值总是胜出——它来自请求而非宿主环境
    return env
```

- 匹配**全部大写化后的变量名**，大小写不敏感；
- 良性变量（PATH/HOME/SHELL/LANG/PWD/TMPDIR/VIRTUAL_ENV）不含上述 token，自动保留；
- **注入值即使命中了擦除模式也保留**——因为注入是经过授权上游（skill 声明 + 请求提供）的值；
- 密钥形名默认就擦除（不是可选），安全默认开。

#### (d) env 键校验（抽象层兜底）

```python
_ENV_NAME_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
# 在 Sandbox 抽象层对 env 的每个 key 校验；不合法抛 ValueError。
```

意图：今天所有实现都把 env 作为结构化字典传给子进程（不经 shell），但**未来某个实现可能把 key 拼进 shell 字符串**——抽象层校验保证这种退化发生时不会变成注入漏洞。

#### (e) 虚拟路径系统

- Agent 只能看到**虚拟路径**：`/mnt/workspace`（被测代码）、`/mnt/reports`（测试报告）、`/mnt/cache`（依赖缓存）、`/mnt/uploads`（输入文件）；
- 物理位置：`{data_dir}/users/{user_id}/threads/{thread_id}/...`；
- 翻译发生在两层（纵深防御）：Provider acquire 时构建 `PathMapping`（虚拟前缀→真实目录）；工具层对命令字符串再做一次 `replace_virtual_paths()`（同时做路径校验）；
- Docker/远程实现把目录直接 volume-mount 到相同虚拟路径，因此**三种实现对外契约完全一致**——本地、容器、远程 CI runner 可互换。

#### (f) Provider 生命周期

```python
class SandboxProvider(ABC):
    def acquire(self, thread_id: str | None = None) -> Sandbox: ...
    async def acquire_async(self, thread_id: str | None = None) -> Sandbox: ...  # 异步路径
    def get(self, sandbox_id: str) -> Sandbox | None: ...                        # 事件循环安全的进程内查找
    def release(self, sandbox_id: str) -> None: ...
```

- 异步 agent/工具路径必须调用异步生命周期钩子——**Docker 创建、发现、跨进程加锁、就绪轮询、释放都不能阻塞事件循环**；
- 本地实现用 per-thread LRU 缓存（如 256 条）加 `threading.Lock` 保护；
- **Warm pool**（对容器/VM）：release 后保留 N 个实例热复用，带 `idle_timeout`（默认 600s）、`replicas` 上限（默认 3）、最旧 warm 驱逐、周期性 idle 检查器。健康检查失败视为"未知"而非"死亡"，避免误杀。

#### (g) 同路径写串行化

对"写文件/替换文件"类操作，按 `(sandbox_id, path)` 加锁——防止同一 turn 内多个并行工具调用共用一份过期状态互相覆盖。

### 2.3 边界条件

- 超时杀掉进程组后，要处理残留后台进程与端口占用（测试 agent 的 server 残留）；
- 有界管道排空必须是**有界的**（读满即暂停），否则后台进程持续输出仍会阻塞；
- `glob`/`grep` 的 `max_results` 必须返回 `(结果, 是否截断)` 元组——让上层知道结果不全；
- 远程沙箱实现可能不支持 `timeout` 参数，抽象层允许忽略但要在文档注明。

### 2.4 测试场景映射

- `run_test_suite`（pytest）、`install_deps`、`start_service`（后台化）、`read_report`（读 coverage/报告）都走同一 `Sandbox` 接口；
- 本地开发 = LocalSandbox；CI = 容器沙箱；未来远程 = 同一接口新实现。

### 2.5 出处

- `backend/packages/harness/deerflow/sandbox/sandbox.py`（接口、env 校验）；
- `backend/packages/harness/deerflow/sandbox/env_policy.py`（擦除策略）；
- `backend/packages/harness/deerflow/sandbox/tools.py`（bash 工具语义）；
- `backend/AGENTS.md` §"Sandbox System"。

---

## 3. 工具结果结构化元数据（核心）

### 3.1 设计意图

工具执行后，上层（错误处理、停滞检测、循环检测、预算、前端展示、后续模型调用）都需要知道"这次工具调用结果如何、可不可救、下一步该干嘛"。如果每层都各自解析工具返回的文本，会：
- 产生无数套脆弱的字符串协议；
- 对同一个失败给出互相矛盾的分类。

因此：**每个工具结果统一打一份结构化元数据，上层只消费它，不解析文本。**

### 3.2 规范

**元数据结构**（存在工具消息的 `additional_kwargs["deerflow_tool_meta"]`）：

```python
TOOL_META_KEY = "deerflow_tool_meta"

@dataclass(frozen=True, slots=True)
class ToolResultMeta:
    status: Literal["success", "error", "partial_success"]
    error_type: str | None          # 见分类表
    recoverable_by_model: bool
    recommended_next_action: Literal["continue", "rewrite_query", "try_alternative", "summarize", "stop"]
    source: Literal["exception", "tool_return", "content_analysis", "progress_middleware"]
```

**错误分类表**（关键词匹配，命中第一行即得元数据；全部未命中 → 未知）：

| error_type | 触发关键词示例 | recoverable_by_model | recommended_next_action |
|---|---|---|---|
| `auth` | 401, 403, unauthorized, authentication, invalid api key | False | `stop` |
| `rate_limited` | rate limit, rate limited | False | `summarize` |
| `transient` | timeout, connection, network error, temporarily unavailable | False | `try_alternative` |
| `config` | not configured, not installed, missing required, disabled | False | `stop` |
| `permission` | permission denied, access denied, forbidden | True | `try_alternative` |
| `no_results` | no results found, no content found | True | `rewrite_query` |
| `not_found` | not found, no such file, does not exist, 404 | True | `rewrite_query` |
| `internal` | unexpected error, internal error, 500 | False | `stop` |
| `unknown` | （兜底） | True | `try_alternative` |

**匹配细则**（准确性关键）：

- **数字码要锚定词边界**：`404` 用 `\b404\b` 匹配，避免 `took 500ms` 里的 500 误命中 `internal`；
- **JSON 错误优先**：内容若是 `{"error": "..."}`，只对 `error` 字段值分类（避免 `query` 字段里的词误命中）；`{"error": null|"none"|"false"}` 视为无错误；
- 内容若不是有效 JSON 才整段分类；
- **partial_success**：内容含 "partial results"/"limited results"/"truncated"/"no results found" 等标记 → `status="partial_success"` + `rewrite_query`，即使工具自己返回 status=success；
- `source` 记录元数据从哪来：异常路径的**分类优先于**工具自返回的打标（异常是更权威的信号）。

**打标函数**：

```python
def normalize_tool_result(msg: ToolMessage | Command) -> ToolMessage | Command:
    """给 ToolMessage 打 meta；已有 meta 的不覆盖（除异常路径）。"""
```

- 异常包装路径（§4 的 ToolErrorHandlingMiddleware）必须调用一个 `stamp_exception_meta`，它**总是覆盖**已有 meta，且 `status="error"`、`source="exception"`；
- 工具自返回路径 `normalize_tool_message`：已有 meta 则原样返回（不重复打标）。

### 3.3 边界条件

- 分类规则列表在模块加载时预编译成正则，**不要在热路径上做惰性写入**；
- 关键词大小写不敏感（先 lower）；
- "工具返回 success 但内容实际是空结果" 必须能被停滞检测捕获——partial 标记就是为此存在；
- 对测试 agent：把 `flaky`（第一次失败、重跑通过）视为"recoverable"，但要用独立字段记录，不能与"干净失败"混在一个 status。

### 3.4 测试场景映射

定义测试专用分类，扩展现有枚举而不是另起炉灶：

| 测试场景 | error_type | recoverable | next_action |
|---|---|---|---|
| 测试套件跑完但全 PASS | success | — | continue |
| 用例失败（断言挂了） | `test_failed`（recoverable=True） | True | rewrite_query（去修） |
| 用例报错（异常/崩溃） | `test_error`（recoverable=True） | True | try_alternative |
| 用例超时 | `test_timeout` | False | try_alternative |
| flaky（重跑才过） | `test_flaky` | True | summarize |
| 覆盖率文件不存在 | `not_found` | True | rewrite_query |
| CI 限流/依赖源不可达 | `rate_limited`/`transient` | False | try_alternative |

### 3.5 出处

- `backend/packages/harness/deerflow/agents/middlewares/tool_result_meta.py`（完整分类实现）；
- 消费方示例：`tool_progress_middleware.py`、`loop_detection_middleware.py`。

---

## 4. 中间件链（洋葱模型）

### 4.1 设计意图

中间件是横切关注点的载体：清洗、预算、错误处理、读-写门禁、预算、循环检测。它们必须**以严格的顺序**包裹模型调用与工具调用，因为每一层都依赖外层已经处理好的数据形态。

### 4.2 机制基础（若用 LangChain）

- `AgentMiddleware` 提供两个包裹点：`wrap_model_call(call_next, state, config)`（包裹模型调用）与 `wrap_tool_call(call_next, request, config)`（包裹工具执行）；
- **`after_model` 按注册的逆序分发**——后注册的中间件先跑 after 阶段。这个逆序是很多顺序决策的根源；
- 中间件是"洋葱"：最外层最先包住调用，最内层最后包住调用。**InputSanitization 必须最外层**，让所有内层（包括 LLM 重试）看到已清洗的消息。

### 4.3 规范（测试 Agent 最小必需集，按注册顺序）

> 顺序编号是强约束，理由附在每条后。实现必须用"注册顺序 + 断言"钉住（见 4.4）。

| # | 中间件 | 职责 | 顺序理由 |
|---|---|---|---|
| 1 | **InputSanitization** | 清洗用户输入，剥离/转义注入标签；在 `additional_kwargs.original_user_content` 保留清洗前的原文作为溯源 | 最外层：所有内层（含重试）都看到干净消息 |
| 2 | **ToolOutputBudget** | 每个工具结果设置字节上限，超限截断并注明"已截断" | 注册在 Sanitization 外层：Sanitization 先中和原始输出，Budget 再作最终裁剪 |
| 3 | **ToolResultSanitization** | **仅对不可信来源**的工具结果（远程抓取、CI 日志、被测程序 stdout）中和框架标签（`<system-reminder>` 等）与边界标记；本地文件内容**不做**处理 | 注册在 Budget 内层：先中和原始输出，再截断 |
| 4 | **ReadBeforeWrite**（默认开） | `read_file` 在结果上打"内容 hash 标记"；`write_file`(覆盖/追加已存在文件) 与替换操作被阻止，除非该路径最新的标记 hash 与文件当前 hash 一致 | **注册在 ToolProgress/ToolError 外层**：被阻止的写立即返回，不消耗停滞/进度槽 |
| 5 | **ToolProgress** | per-(thread, tool) 状态机：按 §3 元数据累计"连续无新信息"次数；`ACTIVE → WARNED → BLOCKED`，BLOCKED 后该工具再调直接给提示 | 注册在 ToolError 外层：ToolError 先打标，ToolProgress 拿到已打标的干净结果再判断 |
| 6 | **ToolErrorHandling** | 工具抛异常 → 转为 `ToolMessage(status="error")`；给所有结果打 `deerflow_tool_meta`（§3）；内容模板：`Error: Tool '<name>' failed with <Exc>: <detail(≤500字)>。 <恢复提示>` | **最内层工具守卫**：捕获所有工具异常并产出 §3 元数据，是外层守卫的唯一数据源 |
| 7 | **LoopDetection** | 检测重复工具调用模式（完全相同调用集；或单一工具类型反复调用），硬停：清空结构化 `tool_calls` 与原始元数据、强制 final answer、记录 `loop_capped` | 模型回复后的调用模式守卫（after_model） |
| 8 | **TokenBudget** | per-run token 预算。硬停**不抛异常**：剥掉本 turn 的 `tool_calls`、强制 `finish_reason="stop"`、让 run 自然完成并给最终答案；记录 `stop_reason=token_capped` | 预算优先于停滞判断 |
| 9 | **TerminalResponse** | 模型在工具执行后返回**空的终态 AIMessage** → 注入隐藏恢复提示重试一次；再次为空 → 落一个可见错误 fallback，run 记为 error（而不是静默成功） | 注册在 loop/token 之后：靠逆序分发让 loop/token 的 after 先跑（清 tool_calls），它最后兜底 |
| 10 | **Guardrail**（可选） | 工具调用前鉴权（可插拔 Provider），deny 时返回错误 ToolMessage | 在工具执行前 |

**ToolProgress 状态机细节**（按 error_type 分三类）：

| 类别 | 条件 | 状态转移 |
|---|---|---|
| (a) | `recoverable_by_model=True`（no_results/not_found/permission/重复成功） | ACTIVE → WARNED（终态，之后每次问题都重注提示） |
| (b) | `recoverable_by_model=False` 且 action≠stop（rate_limited/transient） | ACTIVE → WARNED → BLOCKED（再累计若干次后） |
| (c) | `recoverable_by_model=False` 且 action=stop（auth/config/internal） | 首次即 BLOCKED |

**ToolProgress 与 LoopDetection 的分工**（两者可在同一模型调用注入 HumanMessage 提示而不冲突）：
- ToolProgress = **结果质量守卫**：工具执行后触发，阻止"不再产生新信息"的具体工具；
- LoopDetection = **调用模式守卫**：模型回复后触发，硬停整个 turn；
- 两者不读对方内部状态。

### 4.4 顺序纪律的保障

- 中间件链通过一个**集中构造函数**组装（而非散落各处 append）；
- 对关键顺序用**断言/测试**钉住（如 `assert sanitization_before_budget(middlewares)`），防止重构时顺序漂移；
- 每个中间件文档注明注册位置与理由。

### 4.5 边界条件

- `after_model` **逆序分发**：注册越靠后的中间件，其 after_model 越先执行。因此：清理/收尾类（如 Safety 类）要注册在最后，让它们最先看到原始模型输出、先剥离有问题的 tool_calls；预算/循环守卫注册在它们之前，让 after 在**已清理的消息**上记账——顺序一错，守卫就会对尚未清洗的调用误判；
- ReadBeforeWrite 的 hash 标记随消息存——**一旦上下文被压缩丢掉了 read 消息，标记自动失效**，下一次写必须重新读（这是特性不是 bug，保证"改前必读"）；
- 写操作**永不刷新标记**：连续两次编辑之间必须强制重新读，防止模型拿旧内容连写。

### 4.6 测试场景映射

- ReadBeforeWrite 直接防止"模型没看测试文件就整文件覆写"；
- TokenBudget 防止"一个坏测试反复跑把整轮预算烧光"；
- LoopDetection 防"模型反复跑同一条失败命令不换思路"；
- ToolProgress 防"模型反复读同一个不存在文件/反复跑空用例集"。

### 4.7 出处

- 组装：`backend/packages/harness/deerflow/agents/middlewares/tool_error_handling_middleware.py`（`build_lead_runtime_middlewares` / `build_subagent_runtime_middlewares`）与 `backend/packages/harness/deerflow/agents/lead_agent/agent.py::build_middlewares`；
- 各中间件文件：`backend/packages/harness/deerflow/agents/middlewares/*.py`；
- 顺序与理由：`backend/AGENTS.md` §"Middleware Chain"。

---

## 5. 会话状态与 reducer

### 5.1 设计意图

Agent 的图状态（state）是所有节点/中间件共享的数据总线。字段多了以后，必须**显式声明每个字段的合并语义**，否则并发更新、历史重放、压缩都会产生不可预期的覆盖。

### 5.2 规范

用 LangGraph 的 `AgentState`（TypedDict）或自研状态机，为每个字段配一个 reducer。**Reducer 接收 `(existing, new)`，返回合并结果，且必须能接受"节点没碰该字段 → new 为 None"的情况**。

```python
class TestState(AgentState):
    messages: ...                                        # 对话
    tests: Annotated[list[TestResult], merge_test_results]      # 测试结果台账
    fix_attempts: Annotated[list[FixAttempt], merge_fix_attempts]  # 修复尝试记录
    artifacts: Annotated[list[str], merge_artifacts]            # 去重保序
    reports: Annotated[list[ReportRef], merge_report_refs]      # 只存引用
    goal: Annotated[Goal | None, merge_goal]                    # 无变更则保留
    summary_text: Annotated[str | None, last_value]             # 压缩总结（LastValue）
```

**各 reducer 语义（必须精确实现）**：

| reducer | 语义 |
|---|---|
| `merge_test_results` | 同 `test_id` 最新版本胜出，**保持首次出现顺序**；**终态永不降级**（terminal 状态不被非 terminal 覆盖）；只保留最近 N 条（如 50） |
| `merge_artifacts` | `dict.fromkeys(existing + new)` —— 去重且保序 |
| `merge_goal` | `new is None` → 保留 `existing`；否则 `new` 胜出 |
| `last_value` | 只有显式写入才更新；不写则保留 |
| 冲突型合并 | 两个节点写入同一共享键且值**不等**（如不同 sandbox_id）→ **抛错而非静默选一**（fail-closed），暴露生命周期/隔离 bug |
| 引用型字段 | 只存 `(name, path, summary)` 引用，正文/大 payload 按需读盘，**绝不塞进 checkpoint** |

**"终态永不降级"的判据**（借鉴 DeerFlow 的委派账本）：

```python
TERMINAL_STATUSES = frozenset({"passed", "failed", "error", "skipped", "timed_out"})

def merge_test_results(existing, new):
    if not new:
        return existing or []
    by_id, order = {}, []
    for entry in [*(existing or []), *new]:
        eid = entry["test_id"]
        prev = by_id.get(eid)
        # 终态不被非终态覆盖
        if prev is not None and prev["status"] in TERMINAL_STATUSES and entry["status"] not in TERMINAL_STATUSES:
            continue
        if eid not in by_id:
            order.append(eid)
        by_id[eid] = entry
    merged = [by_id[eid] for eid in order]
    return merged[-MAX_ENTRIES:]  # 截断，保留最近
```

### 5.3 边界条件

- 压缩会重写 `messages` 通道（`RemoveMessage`），导致消息索引重置——**任何依赖消息索引的字段（如 skill 读取时间、光标）在压缩后要能自我修正**；
- reducer 必须幂等：LangGraph 重放会重复应用，不能产生重复条目；
- fail-closed 的冲突合并要提供清晰的错误信息，帮助定位隔离 bug。

### 5.4 测试场景映射

- `merge_test_results` 保证"一个用例先 fail 后 pass（被修好）时，账本以 pass 收尾"；
- 台账截断防止海量用例把上下文撑爆；
- `reports` 只存引用，coverage 报告正文按需读。

### 5.5 出处

- `backend/packages/harness/deerflow/agents/thread_state.py`（全部 reducer 实现）；
- `backend/AGENTS.md` §"ThreadState"。

---

## 6. 子代理并发执行

### 6.1 设计意图

父 Agent 需要并行跑多个独立任务（多个测试目标、多个仓库、多个命令），同时要处理：事件循环冲突、超时、取消、资源上限、结果聚合。

### 6.2 规范

#### (a) 执行形态

`task()` 工具（由 SubagentLimit 中间件裁剪数量）→ 执行器 → 后台线程 → 轮询 → 事件流 → 结果。

#### (b) 双线程池 + 持久隔离事件循环

```python
_scheduler_pool  = ThreadPoolExecutor(max_workers=3, thread_name_prefix="subagent-scheduler-")
_execution_pool  = ThreadPoolExecutor(max_workers=3, thread_name_prefix="subagent-execution-")
```

- **调度池**负责提交与编排（把任务塞进隔离事件循环、管理超时/取消），**执行池**负责实际运行子代理（驱动 `agent.astream`）；两个池各自 3 worker；
- **持久隔离事件循环**：一个 daemon 线程运行 `loop.run_forever()`（永不关闭）。当 `execute()` 检测到**调用方已经在运行事件循环**（`asyncio.get_running_loop()` 成功且 running）时，用 `asyncio.run_coroutine_threadsafe` 把协程 marshall 到隔离循环，然后同步 `future.result(timeout=...)` 等待。
  - 为什么：父 agent 通常跑在事件循环上，若为每个子任务临时创建再关闭一个 loop，共享的 loop-affine 资源（httpx 客户端、aiohttp）会被绑定到已关闭的 loop 而失效。
- 用 `copy_context()` 捕获调用方 ContextVar（trace id、user id），提交时 `context.run(...)` 恢复。

#### (c) 终态一次性（并发安全）

```python
def try_set_terminal(self, status, *, result=None, error=None, stop_reason=None, ...) -> bool:
    """设置终态，恰好一次。后台超时/取消与执行 worker 会竞争同一 result 持有者。
    第一个终态迁移获胜；后续写入必须被忽略。返回是否成功迁移。"""
    with self._state_lock:
        if self.status.is_terminal:
            return False
        ... # 写入
        self.status = status
        return True
```

#### (d) 协作式取消

```python
# cancel_event: threading.Event
# 只在 astream 迭代边界检查（异步执行循环里），不强制打断工具调用
if result.cancel_event.is_set():
    result.try_set_terminal(SubagentStatus.CANCELLED, error="Cancelled by user")
    return result
```

- 线程不能强杀，只能靠协作：在流式迭代边界检查 cancel_event；
- 超时路径：`future.result(timeout=timeout_seconds)` 抛 `TimeoutError` → 置 cancel_event + cancel future + 记 `TIMED_OUT`。

#### (e) stop_reason（加性演进，见 §11）

```python
@dataclass
class SubagentResult:
    status: SubagentStatus          # completed / failed / cancelled / timed_out
    stop_reason: str | None = None  # token_capped | turn_capped | loop_capped | None
```

- 被守卫硬停的 run **保持正常 status**，用加性字段说明"为什么提前结束"；
- 执行器用 `hasattr(mw, "consume_stop_reason")` 鸭子类型收集所有守卫，逐个读取，返回第一个非 None 原因（对新增守卫零耦合）。

#### (f) 资源上限（SubagentLimit）

- **per-response 并发上限**（如 3，模型可见值与强制值一致）；
- **per-run 总委派上限**（如 6）：基于**当前 run 的委派账本条目**计数（非线程全历史），防止"一次 planning checkpoint 合法批次 → 下个 checkpoint 再来一轮"绕过并发限制；同一线程的后续 turn 获得新的 run 预算；
- 达到上限：剥掉多余 `task` 调用、强制 `finish_reason="stop"`、追加一条**可见的限额说明**——让 run 能整合已有结果而不是以空 tool-call 响应收场。

#### (g) 步进捕获与批量持久化

- **只扫新增 tail**：`stream_mode="values"` 每步重放全量 state，所以要维护一个游标，只处理 `messages[processed_count:]`。多工具调用 super-step 会一次追加多条 ToolMessage，**只取 `messages[-1]` 会丢数据**；
- 用 `id` 集合去重（O(1)），防止每块重扫成 O(n²)；
- 事件持久化**批量写**（`put_batch`），在终态、达到阈值、finally 时 flush——单条写是低频路径（每写一个锁/事务），深任务会发成百上千事件。

#### (h) 结果结构

```python
@dataclass
class SubagentResult:
    task_id: str
    trace_id: str                 # 关联父任务日志
    status: SubagentStatus
    result: str | None = None
    error: str | None = None
    stop_reason: str | None = None
    started_at / completed_at: datetime | None
    ai_messages: list[dict] | None = None      # 流式收集的完整 AI 消息
    cancel_event: threading.Event
    _state_lock: threading.Lock    # 保护终态迁移
```

### 6.3 边界条件

- `asyncio.get_running_loop()` 在无运行 loop 的线程里抛 `RuntimeError`，要 try/except 后走 `asyncio.run` 路径；
- 超时与正常完成竞争同一 result → `try_set_terminal` 保证首终态胜出；
- 隔离 loop 关闭时机：`atexit` 注册，stop → join(timeout=1) → close；若线程还活着则跳过 close（不能 close 正在跑的 loop）；
- 子代理图的 checkpointer 必须禁用（一次性 run，永不复用），避免继承父 run 的检查点。

### 6.4 测试场景映射

- 并行跑多个测试文件/多个目标仓库；
- `stop_reason` 让父 agent 知道"这个子任务是被预算/turn/循环截断的，结果可能不完整，要复用部分结果或重试/提预算"；
- 委派账本让父 agent 在后续 turn 复用已完成子任务的结论。

### 6.5 出处

- `backend/packages/harness/deerflow/subagents/executor.py`；
- `backend/packages/harness/deerflow/subagents/step_events.py`；
- `backend/packages/harness/deerflow/agents/middlewares/subagent_limit_middleware.py`；
- `backend/AGENTS.md` §"Subagent System"。

---

## 7. 上下文压缩与持久化

### 7.1 设计意图

测试 Agent 的上下文杀手是海量测试输出。当接近 token 上限时，必须**自动压缩旧上下文**，且压缩后模型仍要知道"已经跑过哪些、失败在哪、试过哪些方案"——否则每次压缩都像失忆。

### 7.2 规范

- **触发条件**：token 数 / 消息数 / 输入上下文比例 三选一（可配置）；
- **keep 策略**：保留最近若干条消息，旧的被总结；
- **总结存放**：总结写入 `summary_text` 状态通道（LastValue），**不是**写一条 summary 消息进 `messages`——保证压缩不改变消息协议形态；
- **持久化投影**：每个模型请求前，DurableContext 中间件把 `summary_text`、委派账本摘要、活跃工具引用作为**隐藏的受保护数据块**（hidden HumanMessage，非 system 指令）注入请求——压缩后的历史、委派过的工作、当前激活的工具依然可见；
- **静态权威规则**（system prompt）与**不可信字段值**（summary 文本、委派结果、外部描述）**分离注入**：前者用 SystemMessage，后者用隐藏 HumanMessage，防止不可信文本被提升为系统指令；
- 手动压缩（`POST /compact`）复用同一压缩中间件，写新 checkpoint，更新 `summary_text`。

### 7.3 边界条件

- 压缩用 `RemoveMessage` 重写 `messages`，导致 `len(messages)` 中途收缩——**步进捕获游标要能感知收缩并复位**（否则压缩点之后的新步骤被丢）；
- 压缩后的消息 tail 可能是"assistant tool-call + tool-result"（无 leading user）——不注入 summary 的话，strict 后端会拒收 assistant-first 请求；
- 消息数 keep 策略丢弃 read 结果时，ReadBeforeWrite 的 hash 标记自动失效（特性）。

### 7.4 测试场景映射

- 长会话跑几十个测试套件，压缩后仍知道"前 20 个套件 PASS，第 21 个失败在 test_x，已尝试修复方案 A/B"；
- 隐藏数据块保证模型不会把总结当"用户指令"。

### 7.5 出处

- `backend/packages/harness/deerflow/agents/middlewares/summarization_middleware.py`；
- `backend/packages/harness/deerflow/agents/middlewares/durable_context_middleware.py`；
- `backend/AGENTS.md` §"Context Summarization" / §"DurableContextMiddleware"。

---

## 8. 运行生命周期与事件流

### 8.1 设计意图

一次 run（从提交任务到产出最终回复）是一个长生命周期过程，涉及：并发准入、状态机、SSE 流式输出、token 计量、持久化历史、取消。需要一个独立的运行时层管理，而不让 HTTP handler 直接驱动图。

### 8.2 规范

#### (a) RunManager（进程内注册表 + 可选持久 Store）

- 进程内存放活跃 RunRecord（含取消/流控状态）；配置了持久 RunStore 时，历史 run 从 store 水合；
- **同 run_id 下内存记录优先生效**——任务/中止/流控状态绑定活跃本地 run；
- `cancel()` 返回**枚举**而非布尔：`cancelled`（本地取消）/ `taken_over`（多 worker 下租约过期被接管）/ `lease_valid_elsewhere`（返回 409 + Retry-After）/ `not_cancellable`（终态）/ `unknown`。

#### (b) 并发准入与 multitask 策略

- 同一线程串行化：新 run 在进入图之前 **`wait_for_prior_finalizing`**——等旧 run 完成落盘/标题生成，避免状态竞争；
- 策略：`interrupt`（打断旧 run）/ `rollback`（回滚到运行前 checkpoint 快照）。

#### (c) run_agent 主流程（顺序固定）

1. `wait_for_prior_finalizing(thread_id, run_id)`；
2. 初始化 RunJournal（token 计量 + 生命周期事件，作为 LangChain callback 挂图根）；
3. `set_status(running)`；
4. 捕获**运行前 workspace 快照**（用于结束后 diff）与**运行前 checkpoint 快照**（用于 rollback）；
5. 收集 `pre_existing_message_ids`（运行前 checkpoint 里的消息 id 集合）；
6. 发布 `metadata` 事件（含 run_id、thread_id）；
7. 注入 runtime context（thread_id、run_id、app_config、trace id、pre_existing_message_ids、journal）；
8. 构建 agent、挂 checkpointer/store；
9. `graph.astream(stream_mode=...)` 循环：`values`（全量状态快照）/ `messages`（增量 chunk，前端拼接）/ `custom`（自定义事件）；
10. 结束：flush 事件、发布 `end`、`set_status(terminal)`。

#### (d) StreamBridge（生产者/消费者解耦）

```python
@dataclass(frozen=True)
class StreamEvent:
    id: str       # 单调递增，作为 SSE id 字段，支持 Last-Event-ID 重连
    event: str    # metadata / updates / events / error / end / ...
    data: Any

class StreamBridge(ABC):
    async def publish(self, run_id: str, event: str, data: Any) -> None: ...
    async def publish_end(self, run_id: str) -> None: ...
    def subscribe(self, run_id, *, last_event_id=None, heartbeat_interval=15.0) -> AsyncIterator[StreamEvent]:
        # 无事件到达超过 heartbeat_interval 时 yield HEARTBEAT_SENTINEL
        # 生产者 publish_end 后 yield END_SENTINEL
    async def cleanup(self, run_id, *, delay=0) -> None: ...
```

- 两种实现：内存（进程内 asyncio.Queue）与 Redis（跨进程/多 worker）；
- Redis 用滚动保留缓冲 TTL（`stream_ttl_seconds`，在 publish/publish_end 时刷新）作**泄漏安全网**，不是 run 超时；
- 启动时孤儿恢复：为恢复的 run 发布 END_SENTINEL + 调度清理；
- SSE 端点可携带 `Last-Event-ID` 重连，从断点续传。

#### (e) RunJournal 与事件存储

- RunJournal：`on_llm_end` 累加 token usage（按模型拆分的 input/output），`on_chain_start/end` 记录生命周期；输出到 RunEventStore；
- RunEventStore：分页读取（`after_seq` 前进游标、可按 entity/task_id 过滤）；批量写 `put_batch`（低频 `put` 每写一个事务/锁）；
- 事件带类别（`category`），如 `subagent` 类别的事件不进 thread feed 但可被 events 端点按 task_id 分页拉取。

#### (f) 残留错误标记屏蔽

`pre_existing_message_ids` 用于屏蔽历史中残留的错误 fallback 标记：某次 run 失败打的 `deerflow_error_fallback=true` 标记，若不屏蔽，会让同一线程**之后所有 run** 被误判为 error。判定只在"本次 run 产生的消息"上进行。

### 8.3 边界条件

- `POST /wait` 要用 `wait_for_run_completion()` 排空流桥而非 `await record.task`——这样才尊重 `on_disconnect` 设置，真实断连时取消后台 run 而非返回过期 checkpoint；
- SSE 端点不能在 `http.response.start` 之前就写响应头（要覆盖 streaming 响应不吞 body——把 trace/响应头在 start 时写）；
- 取消在心跳关闭时走"租约"语义：单 worker 关心跳 → 保留旧 409 路径；多 worker 开心跳 → 过期租约可被接管。

### 8.4 测试场景映射

- 一次"跑测试 + 修复 + 复跑"的完整 run 可观测：每个测试的事件流可回放；
- 断线重连：前端/CLI 断网后带 `Last-Event-ID` 续接，不丢事件；
- 并行 run（不同线程）各自独立状态机。

### 8.5 出处

- `backend/packages/harness/deerflow/runtime/runs/worker.py`（run_agent）；
- `backend/packages/harness/deerflow/runtime/runs/manager.py`（RunManager/取消语义）；
- `backend/packages/harness/deerflow/runtime/stream_bridge/base.py`（StreamBridge）；
- `backend/packages/harness/deerflow/runtime/journal.py`（RunJournal）。

---

## 9. 配置系统

### 9.1 设计意图

一个可运行环境需要：主配置（模型、预算、沙箱、中间件开关）+ 扩展配置（外部服务、工具、插件）。两者都要支持**热更新**，但要明确"哪些字段可以热更新、哪些必须重启"。

### 9.2 规范

#### (a) 分层配置

- `config.yaml`：主配置（沙箱后端、token 预算、中间件开关、模型列表、提示词）；
- `extensions.json`：外部服务/插件（搜索 API、CI 网关、MCP 服务器）。

#### (b) 热重载（内容签名失效）

```python
def get_app_config() -> AppConfig:
    # 缓存解析结果，但每次调用检查:
    #  (resolved_path, mtime, size, sha256_content_digest)
    # 签名变化 → 重新解析。
```

- **不用 mtime 严格 `>` 比较**，而是内容签名比对——覆盖 `git checkout`（mtime 倒退）、`cp -p`（mtime 保留）、对象存储/网络挂载（mtime 陈旧）等场景；
- 每条请求都过 `get_app_config()`，所以 **per-run 字段（token 预算、提示词、工具列表、模型 max_tokens）下一条消息即生效**。

#### (c) 热重载边界（STARTUP_ONLY 登记表）

- 基础设施字段（数据库、沙箱、事件存储、消息队列、鉴权）登记为**重启生效**，配一个集中常量表 `STARTUP_ONLY_FIELDS`，并加测试钉住（改登记表必须改测试）；
- 字段的 Pydantic `description` 前缀 `"startup-only:"`，让 IDE 悬停即可见原因。

#### (d) 版本化与兼容

- 配置文件带 `config_version`；启动时与示例模板比较，过期则告警，提供自动合并缺失字段的命令；
- 配置值以 `$` 开头 → 解析为环境变量（`$OPENAI_API_KEY`）；
- 允许"宽松未知字段"（`extra="allow"`）以兼容供应商特定参数，但**用构建期告警兜底**（见 9.3）。

#### (e) 反射装配

- 工具/模型/沙箱用类路径字符串实例化：`module.path:ClassName`；
- 一个 `resolve_class(path, base_class)`：import 模块 + 校验类继承自 base_class；
- 未知 provider 缺依赖时给出**可操作的安装提示**（如"请运行 `uv add langchain-google-genai`"）。

### 9.3 边界条件

- `extra="allow"` 的副作用：拼写错误的键会在**请求期**才报晦涩错误。因此模型工厂要：把常见别名归一化（`api_base`→`base_url`）、给 OpenAI 系客户端默认注入合理参数（`stream_usage=True`、`stream_chunk_timeout=240s`）、对未知键在构建期显式 `logger.warning`；
- 不要在 `app.state` 缓存 `AppConfig`（会让热重载失效）——lifespan 里只留一次性 bootstrap 用的局部快照。

### 9.4 测试场景映射

- 测试 Agent 在运行中调整"测试超时秒数、重试次数、token 预算、沙箱后端"无需重启；
- 切换 CI 网关/搜索 API 只需改 extensions.json。

### 9.5 出处

- `backend/packages/harness/deerflow/config/app_config.py`；
- `backend/packages/harness/deerflow/config/reload_boundary.py`；
- `backend/packages/harness/deerflow/reflection/`；
- `backend/AGENTS.md` §"Configuration System"。

---

## 10. 安全设计

### 10.1 设计意图

测试 Agent 的输入面非常脏：用户指令、被测程序 stdout（可能打印任意字符/HTML/标签）、CI 日志、webhook/评论触发。必须**按来源分层信任**，并封死密钥的所有泄露路径。

### 10.2 规范

#### (a) 按来源分层信任

| 来源 | 处理 |
|---|---|
| 用户输入消息 | InputSanitization 清洗，剥离注入标签；保留原文作溯源 |
| 远程/外部内容（CI 日志、被测程序 stdout、网页） | ToolResultSanitization **中和框架标签**（`<system-reminder>` 等）与边界标记 |
| 本地文件内容（read_file 结果） | **不做**处理（信任本地沙箱） |
| webhook/评论触发的 run | **降权**：不给管理类工具（如"修改 agent 自身配置"） |

- 中和只针对**远程内容工具**（按工具名 allowlist），本地工具原样——避免过度转义破坏本地代码/测试文件语义。

#### (b) 密钥治理（六面封死）

请求级密钥（如被测系统的 token）按以下约束流动，**任意一条都不得泄漏值**：

1. **prompt**：值永不进消息；
2. **trace**：trace 元数据永不拷贝 `context`；
3. **checkpoint**：密钥存在 `runtime.context`，不在图状态里；
4. **audit**：审计日志只记名字，不记值；
5. **stdout**：bash 输出用 `mask_secret_values` 打码注入值；
6. **run 记录/API**：持久化的 run kwargs 存脱敏副本。

传递路径：调用方在 run 请求的 `context.secrets`（**带外，不是消息**）→ 运行时 context → `bash` 工具的 `execute_command(env=...)`（结构化 env，不拼进命令串）→ 沙箱子进程。

绑定校验三重要求：**skill/工具声明了该密钥名 × 调用方本次提供了值 × 该工具本次处于激活范围**（∩ 语义）。注入值**每次模型调用时**用注册表重新解析（不信任存储数据），防止调用方伪造来源字段。

#### (c) 非交互模式

后台/调度触发的 run 标记 `non_interactive=true` → 排除需要人类确认的工具（`ask_clarification`）。该标记是**内部专用**：仅内部认证调用方可注入，客户端伪造的 `non_interactive` 被丢弃。

#### (d) 不可信字符串入 prompt 前的转义

外部字符串（skill 名、测试输出摘要、网络内容）进 system/hidden context 前用 `html.escape`（属性用 `quote=True`，正文 `quote=False`），防止伪造框架标签。

### 10.3 边界条件

- 密钥注入值**即使命中擦除模式也保留**（它是授权值），宿主环境同名的原值被丢弃；
- 沙箱环境擦除是**默认开启**（不是可选），防止任何脚本读取平台凭据；
- 注入值不持久化、不存储——长期使用意味着每次请求重新提供。

### 10.4 测试场景映射

- 被测系统要求 token，测试脚本通过 `context.secrets` 注入，值不进 prompt/命令串/日志/报告；
- CI 评论触发 run：不给改配置权限，防任意评论者改变 agent 行为；
- 被测程序 stdout 打印 `<system-reminder>` 之类字符串不会劫持 agent。

### 10.5 出处

- `backend/packages/harness/deerflow/agents/middlewares/input_sanitization_middleware.py`、`tool_result_sanitization_middleware.py`；
- `backend/packages/harness/deerflow/sandbox/env_policy.py`；
- `backend/AGENTS.md` §"Request-Scoped Secrets"（#3861）、§"Skills System"；
- `backend/packages/harness/deerflow/runtime/secret_context.py`。

---

## 11. 契约与版本演进

### 11.1 设计意图

Agent 系统的协议（状态枚举、元数据字段、事件类型）横跨后端/前端/CLI/契约文件。若不设防，一次新增状态值就可能破坏旧消费者。原则：**用加性字段演进，用契约文件锁死，用测试防漂移。**

### 11.2 规范

#### (a) JSON 契约文件 = 唯一事实源

```
contracts/
├── subagent_status_contract.json    # 状态枚举 + stop_reason 词汇表 + 版本号
└── tool_meta_contract.json          # 元数据字段与取值
```

契约文件声明：状态枚举值、加性字段（stop_reason）的可取值、每条的语义说明、`version` 字段。

#### (b) 双向校验测试

- 后端从契约文件导入枚举，测试断言"代码里的枚举 == 契约里的值"（`test_status_values_match_contract`）；
- 前端/CLI 解析契约文件，类型定义与契约比对；
- 任何一侧新增值 → 另一侧/契约不同步 → CI 失败。

#### (c) 加性演进（核心原则）

- **新信号用可选字段，不用新枚举**。示例：子代理被预算截断，不用新增 `MAX_TURNS_REACHED` 状态（会破坏 v1 消费者），而是 `status="completed" + stop_reason="turn_capped"`；
- 旧消费者无视新字段即可；新消费者通过新字段获得更丰富语义；
- 分类/判定的权威来源是**结构化字段而非文本**——"判定 run 是否截断"只能读 `stop_reason`，禁止解析助手输出的文字（文字是展示层，会漂移）。

### 11.3 边界条件

- 契约文件必须带版本，升级契约要显式走版本迁移；
- 加性字段的默认语义要明确（`None` = 未触发，不是"未知"）；
- 新字段要同时更新契约 + 后端枚举 + 前端类型 + 钉住测试，缺一不可。

### 11.4 测试场景映射

- 测试结果状态枚举（passed/failed/error/skipped/xfail/flaky）前后端共享一份契约；
- 未来加"被跳过原因"用加性字段，不破坏现有卡片渲染。

### 11.5 出处

- `contracts/subagent_status_contract.json` 与 `backend/packages/harness/deerflow/subagents/status_contract.py`；
- `backend/tests/test_subagent_executor.py` 中 `test_status_values_match_contract`；
- `backend/AGENTS.md` §"Guardrail caps & stop_reason"。

---

## 12. 可观测性与工程质量

### 12.1 设计意图

生产级 Agent 必须：单次 run 有完整 trace、事件可回放、事件循环不被阻塞、代码有测试、文档与代码同步。

### 12.2 规范

#### (a) Tracing（根级附着，防重复 span）

- Langfuse/LangSmith 等 tracing **callback 挂图调用根**（graph root），不挂模型——保证单 run 单 trace，所有 node/LLM/工具调用成为子 span；
- 图内创建的模型（含中间件里的模型）必须传 `attach_tracing=False`，否则同一 LLM 调用产生重复 span，且根级属性（session_id/user_id）因模型成为嵌套观测而丢失。**用一个模块头注释列出所有"图内模型创建点"，新增加入清单**；
- trace 元数据映射：`session_id = thread_id`、`user_id = 有效用户 id`、`trace_name = agent 名`、自定义 tags。

#### (b) 请求级 trace context

- 一个 ContextVar 承载当前请求 trace id；
- HTTP 层中间件：每请求绑定 trace id（继承入站 `X-Trace-Id` 或新生成），在 `http.response.start` 写入响应头（覆盖 SSE，不吞 body）；
- 日志记录器经 filter 注入 `trace_id` 字段；
- 生成器（如 sync stream）在**每次 next() 步**绑定/恢复 ContextVar，避免 yield 之间跨请求泄漏和"Token created in different Context"异常。

#### (c) Blocking-IO 门禁

- 运行时检测：测试包装器（如 Blockbuster）包住业务代码，任何在 asyncio 事件循环上穿过业务代码的**同步阻塞 IO** 调用直接抛错、测试硬失败；
- 静态 AST 扫描作为补充（检测异步代码/async 可达的同步 helper 里的阻塞调用）；
- 目的：保证 HTTP 路径（uvicorn 单事件循环）不被文件 IO、DNS、SQL 同步调用卡死。所有文件/DB 工作走 `asyncio.to_thread` / 专用线程池。

#### (d) 工程质量纪律

- **TDD 强制**：每个特性/修复必须带单测；
- **文档同步**：用户可见变更更新 README，架构变更更新 AGENTS.md，同一变更集内完成；
- 契约/顺序用**测试钉住**（中间件顺序断言、契约值断言、启动字段登记表断言）。

### 12.3 边界条件

- 静态阻塞 IO 检测会误报（同名 helper 复用），要保守：只报告"异步代码内的同步调用"，不作为 CI 硬失败，运行时门禁才是硬规则；
- Monocle 这类进程级 OTel 初始化**只从应用生命周期入口**做，禁止 `import` 时触发（用测试钉住）。

### 12.4 测试场景映射

- 一次测试 run 的完整 trace：`myta run` → trace 含所有模型调用 + 每个 pytest 执行 + 每次文件修改；
- 事件循环健康：跑大量测试时 HTTP 端点依然响应。

### 12.5 出处

- `backend/packages/harness/deerflow/tracing/`（factory/metadata/monocle）；
- `backend/packages/harness/deerflow/trace_context.py`；
- `backend/Makefile`（`detect-blocking-io`）+ `backend/tests/blocking_io/`；
- `backend/AGENTS.md` §"Tracing System" / §"Request Trace Context"。

---

## 13. 实施路线图

按依赖关系分三批落地。**第一批是骨架，缺了无法运行；第二批解决实用性与成本；第三批达到生产级。**

### 第一批：MVP（能跑起来的测试循环）

1. **Sandbox 接口 + LocalSandbox**：`execute_command`（进程组超时、stdin=/dev/null、有界管道）、`read/write_file`、`list_dir`、环境擦除 + env 注入（§2.2）；
2. **工具元数据**：`normalize_tool_result` + 错误分类表（§3）——先只实现 status/error_type/recoverable/next_action 四字段 + 常用分类；
3. **四个必须中间件**（§4 的 #1/#2/#4/#6）：InputSanitization、ToolOutputBudget、ToolErrorHandling、TokenBudget；
4. **状态 + reducer**：TestState + `merge_test_results`（终态不降级、截断）（§5）；
5. **基础运行生命周期**：RunManager + 单线程 run + 事件流（`values`/`messages`）落到一个简单 StreamBridge（内存版）（§8）。

**验收**：能输入"跑 tests/ 并修复失败的测试"，agent 能：跑测试 → 读失败输出（截断后）→ 改文件 → 复跑 → 汇总，且单轮 token 不爆。

### 第二批：增强（实用、并行、省 token）

6. **ReadBeforeWrite + LoopDetection + ToolProgress + TerminalResponse**（§4 补齐）——防止破坏性写、卡循环、停滞、静默空回复；
7. **SubagentExecutor**（§6）：并行跑多个测试目标；`try_set_terminal` 终态一次性；协作取消；`stop_reason` 加性字段；
8. **SubagentLimit**（并发 3 / 每 run 总量上限）；
9. **上下文压缩 + DurableContext**（§7）：`summary_text` + 隐藏数据投影；
10. **配置热重载**（§9 的签名失效 + per-run 字段生效）。

**验收**：能并行跑多套件；跑 20+ 个套件的长会话上下文不爆；flaky/超时/卡循环都有明确分类与停住逻辑；改配置不重启即生效。

### 第三批：生产级（安全、可观测、可运维）

11. **Step 事件批量持久化 + RunEventStore 分页**（§8）；
12. **StreamBridge Redis 版 + Last-Event-ID 重连 + 孤儿恢复**；
13. **多 worker 租约取消语义**（cancel 枚举 + 心跳）；
14. **完整密钥治理**（六面封死 + 绑定三重要求）（§10）；
15. **Tracing + trace context**（§12 a/b）；
16. **Blocking-IO 门禁 + 契约文件 + 双向校验测试 + 顺序断言**（§12 c/d、§11）；
17. **GuardrailProvider + ToolResultSanitization**（§4 #10/#3）。

**验收**：可部署多副本；断线续传；跨 run 可观测；安全扫描无泄漏面；CI 全程守门。

---

## 附录：关键语义速查表

| 概念 | 精确语义 | 禁止事项 |
|---|---|---|
| `ToolResultMeta.status` | `success` / `error` / `partial_success` | 不要与"测试通过/失败"混为一谈 |
| `recoverable_by_model` | 模型采取行动（换查询/换工具/重试）能否改善 | 不能仅靠"重试"就置 True（rate_limited 是 False） |
| 终态不降级 | terminal 状态不被非 terminal 覆盖 | 不得反向覆盖 |
| 终态一次性 | `try_set_terminal` 首个终态迁移获胜 | 后续写入必须被忽略 |
| 中间件顺序 | InputSanitization 最外层；ToolErrorHandling 最内层工具守卫；收尾类靠逆序分发最先执行 after | after_model 是**逆序**分发（注册越靠后，after 越先跑） |
| ReadBeforeWrite | 写前必须有同路径的 read 标记且 hash 匹配 | 写不刷新标记；标记随消息存 |
| stop_reason | 加性可选字段，`None`=未触发 | 不新增枚举破坏旧消费者 |
| 环境擦除 | 大写化后按模式擦除；注入值总是胜出 | 擦除不是可选 |
| 配置签名 | `(path, mtime, size, sha256)` 全量比对 | 不用 mtime `>` 严格比较 |
| 契约 | JSON 文件唯一事实源 + 双向校验测试 | 单侧改值 |

---

*本文档基于对 DeerFlow 2.0 源码与文档的逐项核对整理。所有"出处"路径均为仓库内真实位置，实现过程中如有语义疑问，建议直接阅读对应源码。*
