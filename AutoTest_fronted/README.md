# JApiTest 前端项目

## 项目介绍

这是JApiTest的Vue前端项目，使用Vue 3 + Element Plus开发，提供项目管理、用例管理、API测试和批量执行功能。

## 技术栈

- Vue 3 + Composition API
- Vue Router 4
- Axios
- Element Plus
- Vite

## 开发环境设置

1. 安装依赖

```bash
cd src/main/webapp/frontend
npm install
```

2. 启动开发服务器

```bash
npm run dev
```

开发服务器默认运行在 http://localhost:5173

## 构建项目

```bash
npm run build
```

构建后的文件将输出到 `dist` 目录。

## 功能说明

1. **项目管理**：创建、编辑、删除项目
2. **用例管理**：创建、编辑、删除、执行API测试用例
3. **API测试**：在线测试API，支持各种HTTP方法、请求头、请求体和断言
4. **批量执行**：选择多个用例批量执行，查看执行结果和统计信息

## 注意事项

1. 确保后端服务正在运行在 http://localhost:8080
2. API请求通过代理转发到后端服务
3. 构建后的文件需要部署到后端的webapp目录下以确保正常访问