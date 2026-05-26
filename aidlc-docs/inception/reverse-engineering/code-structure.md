# 代码结构文档 - 自动化测试平台

## 目录结构

```
测试平台/
├── AutoTest_fronted/                    # Vue 3 前端项目
│   ├── index.html                       # HTML 入口
│   ├── package.json                     # 前端依赖配置
│   ├── vite.config.js                   # Vite 构建配置 (代理 /api → localhost:8080)
│   ├── dist/                            # 构建产物
│   └── src/
│       ├── main.js                      # 应用入口
│       ├── App.vue                      # 根组件 (侧边栏 + 主内容布局)
│       ├── router/
│       │   └── index.js                 # 路由配置 (8条路由)
│       └── views/
│           ├── Projects.vue             # API项目管理
│           ├── UseCases.vue             # API用例管理
│           ├── ApiTest.vue              # API单接口测试
│           ├── BatchExecute.vue         # API批量执行
│           ├── UiTest.vue               # UI测试工作台
│           ├── UiProjects.vue           # UI项目管理
│           ├── UiUseCases.vue           # UI用例管理
│           └── UiBatchExecute.vue       # UI批量执行
│
└── backed/                              # Spring Boot 后端
    ├── pom.xml                          # Maven 依赖配置
    ├── full_database_schema.sql         # 完整数据库建表DDL
    ├── ui_use_cases_schema_mysql.sql    # UI用例表DDL
    ├── test_case_report_schema_mysql.sql # 测试报告表DDL
    ├── AI_Study_Notes/
    │   └── src/main/java/org/example/ai_study_notes/
    │       ├── AiStudyNotesApplication.java   # 启动类
    │       ├── config/
    │       │   ├── WebConfig.java              # 静态资源配置
    │       │   ├── AsyncConfig.java            # 线程池配置
    │       │   ├── RedisUtils.java             # Redis配置
    │       │   └── BloomFilterConfig.java      # 布隆过滤器(注释)
    │       ├── controller/
    │       │   ├── ApiTest/
    │       │   │   ├── ProjectController.java    # API项目CRUD
    │       │   │   ├── UseCaseController.java    # API用例CRUD
    │       │   │   ├── API_TestController.java   # 单接口测试
    │       │   │   ├── BatchTestController.java  # 批量并发测试
    │       │   │   └── ReportController.java     # 报告查询导出
    │       │   └── UITest/
    │       │       ├── UIProjectController.java  # UI项目CRUD
    │       │       ├── UIUseCaseController.java  # UI用例CRUD
    │       │       ├── UiTestController.java     # UI单用例测试
    │       │       └── UIBatchTestController.java # UI批量测试
    │       ├── service/
    │       │   ├── ApiTestService.java           # API测试接口
    │       │   ├── ProjectService.java           # API项目服务接口
    │       │   ├── UseCaseService.java           # API用例服务接口
    │       │   ├── TestReportService.java        # 报告服务接口
    │       │   ├── UIProjectService.java         # UI项目服务接口
    │       │   ├── UIUseCaseService.java         # UI用例服务接口
    │       │   ├── UiTestService.java            # UI测试服务接口
    │       │   └── Impl/                         # 服务实现
    │       ├── mapper/                           # MyBatis Plus Mapper
    │       ├── Pojo/
    │       │   ├── Result.java                   # 统一响应
    │       │   ├── entity/                       # 实体类
    │       │   ├── dto/                          # 数据传输对象
    │       │   └── vo/                           # 视图对象
    │       ├── Aop/
    │       │   ├── TestReportAspect.java         # 报告生成切面
    │       │   └── anno/ApiTest.java             # 自定义注解
    │       ├── POM/
    │       │   ├── driver/WebDriverFactory.java  # WebDriver工厂
    │       │   └── page/BasePage.java            # 页面操作基类
    │       ├── handler/GlobalExceptionHandler.java # 全局异常处理
    │       ├── Task/CleanTestReportCaseTask.java # 定时清理任务
    │       └── utils/                           # 工具类
    └── .idea/                                   # IDEA配置
```

## 后端包结构说明

```
org.example.ai_study_notes
├── AiStudyNotesApplication      @SpringBootApplication启动类
├── config                       配置层 (Web, Async, Redis, BloomFilter)
├── controller                   控制层 (API测试 + UI测试)
├── service                      服务层 (接口+实现)
├── mapper                       MyBatis Plus Mapper
├── Pojo                         数据模型
│   ├── Result                   统一返回结果
│   ├── entity                   数据库实体
│   ├── dto                      请求/传输对象
│   └── vo                       响应视图对象
├── Aop                          切面编程 (报告自动生成)
├── POM                          Page Object Model (UI自动化)
├── handler                      异常处理
├── Task                         定时任务
└── utils                        工具类
```

## 数据库表结构

| 表名 | 引擎 | 说明 |
|------|------|------|
| project | InnoDB | API项目表 (id, name) |
| use_case | InnoDB | API用例表 (id, pid, name, url, method, header, param, assertStr) |
| test_case_report | InnoDB | API测试报告表 (id, caseId, status, allureResultJson, duration, startTime, endTime) |
| uiproject | InnoDB | UI项目表 (id, name, description, createTime, updateTime) |
| ui_use_cases | InnoDB | UI用例表 (id, projectId, name, url, browser, viewport, headless, timeout, steps JSON) |
