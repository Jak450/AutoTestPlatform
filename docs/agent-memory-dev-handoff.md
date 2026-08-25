# AutoTestPlatform Agent 记忆系统开发交接文档

> 用途：新会话继续开发时的环境与上下文速查。更新日期：2026-08-25。

## 当前进度

- 分支：`codex/agent-knowledge`（已 push 到 origin，未合并）
- 已完成：工具注解化（@AgentTool）+ 记忆系统子计划 1-4（存储/检索/写入/Neo4j 关系图/反馈闭环/遗忘调度/旧代码清理）
- 下一步：执行 `docs/superpowers/plans/2026-08-25-memory-layer-confirmation-cleanup.md`（4b：Qdrant/Neo4j 遗忘 + 会话式批量确认 + 前端记忆面板移除 + agent_memory 清理）

在新会话里说："执行 docs/superpowers/plans/2026-08-25-memory-layer-confirmation-cleanup.md，用 Inline 模式"即可。

## 环境

| 组件 | 状态/命令 |
|---|---|
| MySQL | Windows 服务 MySQL80，库 `app_test`（root/123456），表已就绪 |
| Redis | 未常驻；启动：`Start-Process "D:\datacontrolclass\redis\redis-server.exe" -ArgumentList "D:\datacontrolclass\redis\redis.windows-service.conf" -WindowStyle Hidden`；停：`D:\datacontrolclass\redis\redis-cli.exe shutdown` |
| Qdrant | Docker 容器 `qdrant`（REST 6333 / gRPC 6334），运行中 |
| Neo4j | Docker 容器 `neo4j`（HTTP 7474 / Bolt 7687，neo4j/autotest123456），数据卷 `deploy/neo4j-data` |
| Docker | 已可用（WSL2 已启用），Docker Desktop 需手动开着 |
| 前端 | `AutoTest_fronted`（Vite，`npm.cmd run dev`，代理 8080） |

## API Key（用户环境变量，不提交 git）

- `EMBEDDING_API_KEY`：硅基流动（BAAI/bge-m3，base-url `https://api.siliconflow.cn/v1`）
- `DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL`（=deepseek-v4-flash）：DeepSeek agent 模型
- 启动后端需注入（沙箱里读不到用户环境变量，必须提权）：
  ```powershell
  $env:DEEPSEEK_API_KEY=[System.Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY","User")
  $env:EMBEDDING_API_KEY=[System.Environment]::GetEnvironmentVariable("EMBEDDING_API_KEY","User")
  $env:DEEPSEEK_MODEL=[System.Environment]::GetEnvironmentVariable("DEEPSEEK_MODEL","User")
  ```

## 构建与测试约定

- Maven 用 IntelliJ 自带：`D:\wangzhikang\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3\bin\mvn.cmd`
- 单元测试：`mvn -f backed\AI_Study_Notes\pom.xml test`
- 集成测试（需 MySQL + Redis + Qdrant + Neo4j）：`mvn -f backed\AI_Study_Notes\pom.xml test "-Dsurefire.excludedGroups="`
- 打包：`mvn -f backed\AI_Study_Notes\pom.xml -DskipTests package`
- PowerShell 传参注意：`-Dsurefire.excludedGroups=` 需整体加引号；`-Dtest=A,B` 需引号

## 权限与沙箱

- Maven 联网下载、git commit/push、docker、mysql 写库、setx、GUI 启动等都需要 `require_escalated`（自动审批可能已记住部分前缀）
- 工作区可写：`D:\桌面\AutoTestPlatform`；`.git` 目录读-only，提交必须提权

## 代码约定（重要，防误导）

- Mapper 用 `@Repository`（项目 `@MapperScan(annotationClass=Repository.class)`），不要用 `@Mapper`
- 分层：api(Controller) → service(业务) → repository(Mapper/外部存储封装) → infra(接口)；业务层禁止直接注入 Mapper
- 外部依赖封装隔离：`EmbeddingClient`、`QdrantVectorStore`、`Neo4jGraphRepository`
- 关系谓词受控词表：`RelationType` 枚举
- 记忆系统 4 层：L1 工作记忆（AgentLoop messages）/ L2 会话（agent_message）/ L3 情景（memory_episode）/ L4 长期（memory_fact / memory_experience / memory_knowledge + Neo4j）
- `agent_memory` 表已废弃（4b 移除），偏好走 `memory_fact`（entity="user"）
- 清理 Redis 产物：dump.rdb / server_log.txt 会落在工作区根（Redis 无工作目录启动时），测试后删除

## 遗留尾巴（不在当前计划内）

- 任务计划完成信号（update_task_plan 勾完）作为"完整工作完成"的检测（当前用收尾语关键词 + 超时兜底）
- "对/不对"的 LLM 语义解析（当前规则解析）
- Neo4j 关系在业务变更时的显式归档入口
- 旧 AI 模块（`ai.ark.*`）清理（未列入范围）
- 检索评测 golden set 真实标注数据（工具链已就绪）
