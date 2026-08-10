# Agent 评测报告（多轮）

- 生成时间: 2026/8/10 16:09:31
- 任务数: 1，总轮次: 2（每任务 2 轮）
- 通过率: 1/2 (50%)
- 平均轮数: 2.0
- token 总量: undefined，平均 9732/轮

| 任务 | 通过率 | 工具调用（最近一轮） | stopReason（最近一轮） | 平均轮数 | 平均tokens |
|---|---|---|---|---|---|
| trial_run_generated | 1/2 | list_files, read_file_content, generate_cases, trial_run_cases | awaiting_confirmation | 4 | 19464 |

**trial_run_generated** 失败明细（通过 1/2）：
- 第 1 轮: 工具=[list_files, generate_cases, read_file_content, parse_document] stop=stop
