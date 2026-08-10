# Agent 评测报告

- 生成时间: 2026/8/10 15:52:01
- 通过率: 8/8 (100%)
- 平均轮数: 2.3
- token 总量: 81170，平均 10146/任务

| 任务 | 结果 | 工具调用 | stopReason | 轮数 | tokens |
|---|---|---|---|---|---|
| query_projects | ✅ | list_projects | stop | 2 | 8291 |
| query_use_cases | ✅ | list_use_cases | stop | 2 | 8367 |
| query_reports | ✅ | query_reports | stop | 2 | 9187 |
| list_memory | ✅ | list_memory | stop | 2 | 8248 |
| list_templates | ✅ | list_templates | stop | 2 | 8284 |
| write_project_confirm | ✅ | create_project | awaiting_confirmation | 1 | 4104 |
| gen_cases_from_doc | ✅ | list_files, list_templates, parse_document, read_file_content, generate_cases, parse_document, read_file_content | stop | 6 | 30412 |
| run_api_confirm | ✅ | run_api_test | awaiting_confirmation | 1 | 4277 |
