# 依赖关系文档 - 自动化测试平台

## 外部 Maven 依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| spring-boot-starter-web | 2.6.13 | Web应用基础 |
| mybatis-plus-boot-starter | 3.5.14 | ORM框架 |
| mysql-connector-java | 8.0.33 | MySQL驱动 |
| druid-spring-boot-starter | 1.2.15 | 数据库连接池 |
| spring-boot-starter-data-redis | - | Redis缓存 |
| spring-boot-starter-aop | - | AOP支持 |
| httpclient5 | 5.3 | HTTP请求库 |
| selenium-server (本地jar) | 4.18.1 | UI自动化 |
| allure-java-commons | 2.30.0 | Allure报告 |
| allure-generator | 2.30.0 | 报告生成 |
| allure-model | 2.30.0 | 报告数据模型 |
| lombok | 1.18.30 | 代码简化 |

## 外部 NPM 依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| vue | ^3.3.4 | 前端框架 |
| element-plus | ^2.3.12 | UI组件库 |
| vue-router | ^4.2.4 | 路由 |
| axios | ^1.5.0 | HTTP客户端 |
| xlsx | ^0.18.5 | Excel处理 |
| vite | ^4.4.9 | 构建工具 |
| @vitejs/plugin-vue | ^4.3.4 | Vue插件 |

## 内部依赖链

### API测试模块
```
Controller → Service → Mapper → MySQL
                ↓
          HttpClient5 → 目标API
                ↓
          Allure Reporter → test_case_report
```

### UI测试模块
```
Controller → Service → Mapper → MySQL
                ↓
          WebDriverFactory → Selenium WebDriver
                ↓
          BasePage (POM) → 目标网页
                ↓
          UiTestResultVO ← 步骤执行结果
```

### AOP拦截链
```
@ApiTest 注解 → TestReportAspect → 提取BatchExecuteDTO
       ↓
  执行目标方法 → 捕获结果/异常
       ↓
  TestReportService.recordCaseReport()
       ↓
  buildAllureResultJson() → 存入 test_case_report
```

### 并发执行架构
```
BatchExecuteDTO → ApiTestServiceImpl.batchExecute()
       ↓
  Semaphore(控制并发数) + CompletableFuture
       ↓
  @Async线程池(testExecutor: core=10, max=50)
       ↓
  ApiRequestDTO → HttpClient5.run() → 断言
       ↓
  收集结果 → BatchExecuteResultVO
       ↓
  TestReportAspect → 逐个记录报告
```

### 报告导出流程
```
ReportController.exportAllure()
       ↓
  TestReportService.exportAllureHtmlZip()
       ↓
  queryCaseReports() → 查询数据库
       ↓
  writeAllureJson() → 生成*-result.json
       ↓
  generateHtmlWithAllureJava() → allure generate CLI
       ↓
  zipDirectory() → 返回ZIP字节数组
```

### 定时任务
```
CleanTestReportCaseTask
  @Scheduled(cron = "${task.cron.accessUserInfo}")
       ↓
  deleteCaseReports(过去24小时数据)
```