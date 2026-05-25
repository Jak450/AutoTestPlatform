# 技术栈文档 - 自动化测试平台

## 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.3.4 | 前端框架 |
| Vite | 4.4.9 | 构建工具 |
| Element Plus | 2.3.12 | UI组件库 |
| Vue Router | 4.2.4 | 前端路由 |
| Axios | 1.5.0 | HTTP客户端 |
| xlsx | 0.18.5 | Excel导入导出 |

## 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 2.6.13 | 后端框架 |
| MyBatis Plus | 3.5.14 | ORM框架 |
| MySQL Connector | 8.0.33 | MySQL驱动 |
| Druid | 1.2.15 | 数据库连接池 |
| Lombok | 1.18.30 | 代码简化 |
| Spring AOP | - | 面向切面编程 |
| Spring Data Redis | - | Redis缓存 |
| Apache HttpClient5 | 5.3 | HTTP客户端(API测试) |
| Selenium | 4.18.1 | 浏览器自动化(UI测试) |
| Allure | 2.30.0 | 测试报告框架 |
| Jackson | - | JSON序列化 |

## 构建工具

| 工具 | 用途 |
|------|------|
| Maven | 后端构建 |
| Vite | 前端构建 |
| Allure Maven Plugin | Allure报告构建 |

## 开发工具

- IntelliJ IDEA (`.idea/` 配置)
- Postman / 浏览器 DevTools (API调试)
- MySQL Workbench (数据库管理)

## 运行时环境

| 组件 | 说明 |
|------|------|
| 前端服务器 | Vite Dev Server (开发) / Nginx (生产) |
| 后端服务器 | Spring Boot Embedded Tomcat (端口8080) |
| 数据库 | MySQL 8.0+ (test_platform) |
| 缓存 | Redis |
| 浏览器驱动 | ChromeDriver / GeckoDriver / EdgeDriver |

## 关键路径配置

| 配置项 | 路径/说明 |
|--------|----------|
| UI驱动配置 | `resources/UI_conf/config.properties` |
| 定时任务开关 | `task.scheduler.enable.accessUserInfo` |
| 定时任务Cron | `task.cron.accessUserInfo` |
| 前端API代理 | Vite配置 → localhost:8080 |
| Allure报告静态资源 | `file:target/allure-report/` |
