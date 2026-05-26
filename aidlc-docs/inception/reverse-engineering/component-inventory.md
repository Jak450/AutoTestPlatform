# 组件清单 - 自动化测试平台

## 后端组件 (backed/AI_Study_Notes/)

### Controller 层 (9个组件)

| 组件 | 路径 | 职责 |
|------|------|------|
| ProjectController | `controller/ApiTest/` | API项目管理 CRUD |
| UseCaseController | `controller/ApiTest/` | API用例管理 CRUD |
| API_TestController | `controller/ApiTest/` | 单接口测试执行 |
| BatchTestController | `controller/ApiTest/` | 批量并发接口测试 |
| ReportController | `controller/ApiTest/` | 报告查询和导出 |
| UIProjectController | `controller/UITest/` | UI项目管理 CRUD |
| UIUseCaseController | `controller/UITest/` | UI用例管理 CRUD |
| UiTestController | `controller/UITest/` | UI单用例测试执行 |
| UIBatchTestController | `controller/UITest/` | UI批量测试执行 |

### Service 层 (7个接口 + 6个实现)

| 接口 | 实现 | 职责 |
|------|------|------|
| ProjectService | ProjectServiceImpl | API项目管理逻辑 |
| UseCaseService | UseCaseServiceImpl | API用例管理逻辑 |
| ApiTestService | ApiTestServiceImpl | HTTP请求执行 + 断言 + 批量并发 |
| TestReportService | TestReportServiceImpl | Allure报告生成、查询、导出 |
| UIProjectService | UIProjectServiceImpl | UI项目管理逻辑 |
| UIUseCaseService | UIUseCaseServiceImpl | UI用例管理逻辑 |
| UiTestService | UiTestServiceImpl | UI测试执行 + 批量并发 |

### Mapper 层 (5个组件)

| 组件 | 对应表 | 职责 |
|------|--------|------|
| ProjectMapper | project | API项目数据访问 |
| UseCaseMapper | use_case | API用例数据访问 |
| TestCaseReportMapper | test_case_report | 测试报告数据访问 |
| UIProjectMapper | uiproject | UI项目数据访问 |
| UIUseCaseMapper | ui_use_cases | UI用例数据访问 |

### Entity 层 (5个组件)

| 组件 | 表名 | 关键字段 |
|------|------|----------|
| Project | project | id, name |
| UseCase | use_case | id, pid, name, url, method, header, param, assertStr |
| TestCaseReport | test_case_report | id, caseId, status, allureResultJson, startTime, endTime |
| UIProject | uiproject | id, name, description, createTime, updateTime |
| UIUseCase | ui_use_cases | id, projectId, name, url, browser, viewport, steps(JSON) |

### 基础设施组件

| 组件 | 路径 | 职责 |
|------|------|------|
| WebConfig | config/ | 静态资源映射(Allure报告) |
| AsyncConfig | config/ | 线程池配置(核心10/最大50) |
| RedisUtils | config/ | Redis序列化配置 |
| BloomFilterConfig | config/ | 布隆过滤器(暂未启用) |
| TestReportAspect | Aop/ | 测试报告AOP切面 |
| ApiTest (注解) | Aop/anno/ | 自定义注解标记测试方法 |
| GlobalExceptionHandler | handler/ | 全局异常处理 |
| CleanTestReportCaseTask | Task/ | 定时清理报告数据 |
| WebDriverFactory | POM/driver/ | WebDriver创建(Chrome/Firefox/Edge) |
| BasePage | POM/page/ | 页面操作封装(点击/输入/滚动等) |
| ContentTypeUtils | utils/ | HTTP请求ContentType处理 |
| JsonUtils | utils/ | JSON解析工具 |
| ReadProperties | utils/ | 配置文件读取 |
| BuildReportFileNameUtils | utils/ | 报告文件名生成 |

### DTO/VO 层 (15+个组件)

| 组件 | 类型 | 用途 |
|------|------|------|
| ApiRequestDTO | DTO | API测试请求参数 |
| BatchExecuteDTO | DTO | 批量执行参数(useCaseIds,执行次数,并发数) |
| BatchDTO | DTO | 单用例批次参数 |
| ProjectDTO | DTO | 项目创建/更新 |
| UseCaseUpdateDTO | DTO | 用例更新 |
| ReportQueryDTO | DTO | 报告查询条件 |
| FileDataDTO | DTO | 文件数据上传(未使用) |
| UiUseCaseDTO | DTO | UI用例CRUD |
| UiTestCaseRequestDTO | DTO | UI测试请求 |
| UiTestStepDTO | DTO | UI测试步骤 |
| UiAssertionDTO | DTO | UI步骤断言 |
| ApiResponseVO | VO | API响应结果 |
| BatchExecuteResultVO | VO | API批量执行结果 |
| TestCaseReportVO | VO | 报告展示VO |
| UiTestResultVO | VO | UI测试结果 |
| UiBatchExecuteResultVO | VO | UI批量执行结果 |
| UiStepResultVO | VO | UI步骤执行结果 |
| AssertResult | VO | 断言结果 |
| ProjectVO | VO | 项目展示VO |
| UseCaseVO | VO | 用例展示VO |
| Result\<T\> | 通用 | 统一API响应包装 |

## 前端组件 (AutoTest_fronted/)

### 页面组件 (8个)

| 组件 | 路由 | 功能模块 |
|------|------|----------|
| Projects.vue | /projects | API项目管理 |
| UseCases.vue | /use-cases | API用例管理 |
| ApiTest.vue | /api-test | 单接口测试 |
| BatchExecute.vue | /batch-execute | API批量执行 |
| UiTest.vue | /ui-test | UI测试工作台 |
| UiProjects.vue | /ui-projects | UI项目管理 |
| UiUseCases.vue | /ui-use-cases | UI用例管理 |
| UiBatchExecute.vue | /ui-batch-execute | UI批量执行 |

### 基础层组件

| 组件 | 路径 | 职责 |
|------|------|------|
| App.vue | src/ | 主布局(侧边栏+主内容区) |
| router/index.js | src/ | 路由配置(8条路由) |
| main.js | src/ | 入口文件(Axios配置) |

## 组件间关系图

```
Frontend                    Backend
─────────                   ────────
Projects.vue  ───GET/POST──→ ProjectController → ProjectService → ProjectMapper → project
UseCases.vue  ───GET/POST──→ UseCaseController → UseCaseService → UseCaseMapper → use_case
ApiTest.vue   ────POST─────→ API_TestController → ApiTestService → HttpClient5 (外部API)
BatchExecute  ────POST─────→ BatchTestController → ApiTestService.batchExecute() → 并发执行
                                                    ↓
                                              TestReportAspect
                                                    ↓
                                              TestReportService → TestCaseReportMapper → test_case_report

UiTest.vue    ────POST─────→ UiTestController → UiTestService → WebDriverFactory → Selenium
UiUseCases    ────GET/POST──→ UIUseCaseController → UIUseCaseService → UIUseCaseMapper → ui_use_cases
UiProjects    ────GET/POST──→ UIProjectController → UIProjectService → UIProjectMapper → uiproject
UiBatchExec   ────POST─────→ UIBatchTestController → UiTestService.batchTest() → 并发UI执行

Report        ────POST─────→ ReportController → TestReportService → exportAllureHtmlZip
                                                    ↓
                                              Allure CLI → HTML报告
```