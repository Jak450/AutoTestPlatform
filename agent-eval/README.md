# Agent 评测脚手架

对 Agent 做端到端效果测评：真实对话链路（登录 → 建会话 → 发消息 → SSE 事件流），
校验工具调用序列与成功标准，输出报告。

## 运行

```bash
# 前置：后端已启动（含 DEEPSEEK_API_KEY）
node agent-eval/run-eval.mjs
```

环境变量（可选）：

```bash
EVAL_BASE_URL=http://localhost:8080
EVAL_USER=admin
EVAL_PASSWORD=12345678
```

## 输出

- 终端：每个任务 PASS/FAIL + 汇总（通过率 / 平均轮数 / token）
- `agent-eval/report.md`：详细报告（工具调用、stopReason、失败明细）

## 任务集（tasks.jsonl）

每行一个任务：

```json
{
  "id": "query_projects",
  "prompt": "查看当前平台有哪些项目",
  "expectedTools": ["list_projects"],
  "setupUpload": { "fileName": "a.md", "content": "需求文档内容" },
  "successCriteria": {
    "textContains": "项目",
    "stopReason": "awaiting_confirmation"
  }
}
```

字段：

- `prompt`：发给 Agent 的用户消息（必填）
- `expectedTools`：期望被调用的工具名列表（可选，全部命中才算过）
- `setupUpload`：任务开始前上传到会话的文档（可选）
- `successCriteria`：自定义成功标准（可选）
  - `textContains` / `textNotContains`：最终回复文本包含/不包含
  - `stopReason`：期望的终止原因（`stop` / `awaiting_confirmation` 等）
  - `toolCalled`：必须调用过某工具

## 判定规则

任务通过 = `expectedTools` 全部命中 + 事件流无 error + stopReason 合法 + `successCriteria` 通过。
写入 `agent-eval/tasks.jsonl` 时注意：**同一会话多次运行会积累对话历史**，评测前建议用不同
`id`（runner 每次新建会话，互不影响）。

## 后续接入 CI

可把 `node agent-eval/run-eval.mjs` 加入 GitHub Actions：起一个带 MySQL/Redis/DeepSeek key 的
后端，跑任务集后把 `report.md` 作为 artifact，并让失败（passRate < 阈值）阻断合并。
