# API 文档 - 自动化测试平台

## 统一约定

- 后端基础端口：`8080`
- 前端开发代理：`AutoTest_fronted/vite.config.js` 将 `/api` 代理到 `http://localhost:8080`
- 前端 axios：`axios.defaults.baseURL = '/api'`
- 大多数接口返回 `Result<T>`：成功通常为 `{ code: 1, data: T, msg: null }`，失败通常为 `{ code: 0, data: null, msg: '错误信息' }`
- 部分历史接口直接返回 `List` 或 VO，不包 `Result<T>`

## API 测试模块

### 项目管理

Base path: `/api/projects`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| GET | `/api/projects` | 获取所有 API 项目 | `List<ProjectVO>` |
| POST | `/api/projects` | 新增 API 项目 | `Result` |
| PUT | `/api/projects/{id}` | 更新 API 项目 | `Result` |
| DELETE | `/api/projects/{id}` | 删除 API 项目 | `Result` |

Project DTO/VO 主要字段：`id`, `name`。

### 用例管理

Base path: `/api/use-cases`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| GET | `/api/use-cases?pid={pid}` | 获取指定 API 项目下的用例 | `List<UseCaseVO>` |
| GET | `/api/use-cases/{id}` | 获取单个 API 用例详情 | `UseCase` |
| POST | `/api/use-cases` | 新增 API 用例 | `Result` |
| PUT | `/api/use-cases/{id}` | 更新 API 用例 | `Result` |
| DELETE | `/api/use-cases/{id}` | 删除 API 用例 | `Result` |

UseCase 主要字段：`id`, `pid`, `name`, `url`, `method`, `header`, `param`, `assertStr`, `description`。

### 单接口测试执行

Base path: `/api/test`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/test` | 执行单个 API 请求并返回响应和断言结果 |

Request 主要字段：

```json
{
  "method": "GET|POST|PUT|DELETE|PATCH",
  "url": "https://example.com/api",
  "header": "{...}",
  "param": "{...}",
  "assertStr": "{...}"
}
```

Response 主要字段：`status`, `result`, `headers`, `time`, `size`, `assertResults` 或兼容旧格式 `assertResult`。

### API 批量执行

Base path: `/api`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/execute` | 批量并发执行 API 用例；标注 `@Idempotent` 和 `@ApiTest` |

Request:

```json
{
  "useCaseIds": [1, 2, 3],
  "executionCount": 1,
  "maxConcurrency": 5
}
```

Response: `BatchExecuteResultVO`，主要字段：`total`, `success`, `failed`, `totalTime`, `details`。

### 报告管理

Base path: `/api/report`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/report/cases` | 按模块、用例、状态、时间范围查询执行报告 | `Result<List<TestCaseReportVO>>` |
| POST | `/api/report/export` | 生成并下载 Allure HTML ZIP | `ResponseEntity<byte[]>` |

ReportQuery 主要字段：`moduleName`, `caseName`, `status`, `startTime`, `endTime`。

## UI 测试模块

### UI 项目管理

Base path: `/api/ui-projects`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| GET | `/api/ui-projects` | 获取所有 UI 项目 | `List<UIProject>` |
| POST | `/api/ui-projects` | 新增 UI 项目 | `Result` |
| PUT | `/api/ui-projects/{id}` | 更新 UI 项目 | `Result` |
| DELETE | `/api/ui-projects/{id}` | 删除 UI 项目 | `Result` |

UIProject 主要字段：`id`, `name`, `description`, `createTime`, `updateTime`。

### UI 用例管理

Base path: `/api/ui-use-cases`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| GET | `/api/ui-use-cases?pid={projectId}` | 获取指定 UI 项目下的用例 | `List<UiUseCaseDTO>` |
| POST | `/api/ui-use-cases` | 新增 UI 用例 | `Result` |
| PUT | `/api/ui-use-cases/{id}` | 更新 UI 用例 | `Result` |
| DELETE | `/api/ui-use-cases/{id}` | 删除 UI 用例 | `Result` |

UIUseCase 主要字段：`id`, `projectId`, `name`, `description`, `url`, `browser`, `viewport`, `headless`, `timeout`, `steps`。

### UI 单用例执行

Base path: `/api/ui-test`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/ui-test/run` | 执行一次 UI 测试请求 | `Result<UiTestResultVO>` |

Request 主要字段：

```json
{
  "url": "https://example.com",
  "browser": "chrome",
  "viewport": "1920x1080",
  "headless": true,
  "timeout": 30,
  "steps": [
    {
      "name": "点击登录",
      "action": "click",
      "locatorType": "css",
      "locatorValue": "#login",
      "actionValue": "",
      "waitTime": 0,
      "assertion": null
    }
  ]
}
```

### UI 批量执行

Base path: `/api`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/ui-batch-test` | 批量并发执行 UI 用例；标注 `@Idempotent` | `UiBatchExecuteResultVO` |

Request 与 API 批量执行相同：`useCaseIds`, `executionCount`, `maxConcurrency`。

### UI 步骤动作类型

| 动作 | 说明 |
|---|---|
| `click` | 点击元素 |
| `input` | 输入文本 |
| `getText` / `gettext` | 获取元素文本 |
| `getAttribute` / `getattribute` | 获取元素属性 |
| `hover` | 鼠标悬停 |
| `waitVisible` / `waitvisible` | 等待元素可见 |
| `waitHidden` / `waithidden` | 等待元素隐藏 |
| `scroll` | 滚动页面 |
| `screenshot` | 截图 |
| `switchWindow` / `switchwindow` | 切换窗口 |
| `customCode` / `customcode` | 执行自定义 JavaScript |

## AI 模块 API

Base path: `/api/ai`

### 管道模式

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/ai/analyze-requirement` | 非流式需求分析，返回 session、分析结果和问题 | `Result<Map<String,Object>>` |
| POST | `/api/ai/analyze-requirement-stream` | SSE 风格流式需求分析 | `text/event-stream` |
| POST | `/api/ai/submit-answers?sessionId={id}` | 提交澄清答案并返回下一轮问题/是否可生成 | `Result<Map<String,Object>>` |
| POST | `/api/ai/generate-cases?sessionId={id}` | 根据上下文和 Q&A 生成 API 用例 JSON | `Result<String>` |

Analyze request:

```json
{
  "fileName": "requirement.md",
  "content": "需求文档文本内容",
  "projectId": 1
}
```

Stream event data 当前是 JSON 字符串，常见 `type`：

| type | 说明 |
|---|---|
| `stage` | 阶段变化，如 `parsing`, `analyzing`, `generating_questions` |
| `token` | 某阶段模型增量输出 |
| `done` | 问题生成完成，包含 `questions` 和 `canGenerate` |
| `session` | 服务端创建 `sessionId` |
| `error` | 错误信息 |

### Agent 模式

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/ai/agent-analyze` | ReAct Agent 首轮分析需求 | `Result<Map<String,Object>>` |
| POST | `/api/ai/agent-generate?sessionId={id}` | 提交用户回答并继续 Agent 生成 | `Result<Map<String,Object>>` |

Agent 可能返回：

- `type=questions`：需要前端展示澄清问题。
- `type=cases`：直接返回生成用例。
- `type=error`：超过轮数或执行失败。

### 测试结果 AI 分析

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/ai/analyze-result/{reportId}` | 分析 `test_case_report` 中的一条报告 | `Result<String>` |

## 幂等能力现状

- 当前没有 `/api/idempotent/token` 控制器。
- `@Idempotent` 使用 Redis key：`idempotent:{requestURI}:{MD5(args)}`。
- 方法返回或抛异常后会删除 key，因此更偏向“防并发重复提交”，不是“成功后固定时间禁止重复”。
