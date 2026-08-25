# 记忆检索评测

## 数据

- `golden-set.jsonl`：每行 `{"query": "...", "expected": ["type:id", ...], "scenario": "标签"}`
- 由测试人员从真实业务标注，目标 100 条；复制 `golden-set.example.jsonl` 后按格式填写。

## 运行

前置：后端已启动（`java -jar target/AI_Study_Notes-0.0.1-SNAPSHOT.jar`），并已登录取得 token。

```bash
TOKEN=<登录返回的 token> node agent-eval/memory-eval/run-memory-eval.mjs --topK 5
```

输出：Recall@5、MRR、各 scenario 分组结果、失败明细。
