# API 文档 - 自动化测试平台

## 接口测试模块 API

### 项目管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/projects | 获取所有项目 |
| POST | /api/projects | 新增项目 |
| PUT | /api/projects/{id} | 更新项目 |
| DELETE | /api/projects/{id} | 删除项目 |

**Project 模型**: { id, name }

### 用例管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/use-cases?pid={pid} | 获取指定项目下的用例 |
| GET | /api/use-cases/{id} | 获取单个用例详情 |
| POST | /api/use-cases | 新增用例 |
| PUT | /api/use-cases/{id} | 更新用例 |
| DELETE | /api/use-cases/{id} | 删除用例 |

**UseCase 模型**: { id, pid, name, url, method, header, param, assertStr, description }

### 接口测试执行

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/test | 执行单接口测试 |

**Request**: { method, url, header, param, assertStr }
**Response**: { status, result, headers, time, size, assertResults }

### 批量执行

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/execute | 批量并发执行接口测试 (@ApiTest) |

**Request**: { useCaseIds: [], executionCount, maxConcurrency }
**Response**: { total, success, failed, totalTime, details: [{ useCaseId, useCaseName, executionIndex, result, success, duration }] }

### 报告管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/report/cases | 查询测试报告列表 |
| POST | /api/report/export | 导出Allure HTML报告(ZIP) |

**ReportQuery**: { moduleName?, caseName?, status?, startTime?, endTime? }

---

## UI 测试模块 API

### UI 项目管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/ui-projects | 获取所有UI项目 |
| POST | /api/ui-projects | 新增UI项目 |
| PUT | /api/ui-projects/{id} | 更新UI项目 |
| DELETE | /api/ui-projects/{id} | 删除UI项目 |

**UIProject 模型**: { id, name, description, createTime, updateTime }

### UI 用例管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/ui-use-cases?pid={pid} | 获取指定UI项目下的用例 |
| POST | /api/ui-use-cases | 新增UI用例 |
| PUT | /api/ui-use-cases/{id} | 更新UI用例 |
| DELETE | /api/ui-use-cases/{id} | 删除UI用例 |

**UIUseCase 模型**: { id, projectId, name, description, url, browser, viewport, headless, timeout, steps: [] }

### UI 测试执行

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ui-test/run | 执行UI单用例测试 |

**Request**: { url, browser?, viewport?, headless?, timeout?, steps: [{ name, action, locatorType, locatorValue, actionValue, customCode, waitTime, assertion }] }
**Response**: { executionId, success, duration, passedSteps, failedSteps, error, steps: [{ name, action, status, actualValue, message, duration, screenshotBase64 }] }

### UI 批量执行

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ui-batch-test | 批量并发执行UI测试 |

**Request**: { useCaseIds: [], executionCount, maxConcurrency }
**Response**: { total, success, failed, totalTime, details: [{ useCaseId, useCaseName, executionIndex, result: UiTestResultVO, success, duration }] }

## UI 测试步骤动作类型

| 动作 | 说明 |
|------|------|
| click | 点击元素 |
| input | 输入文本 |
| gettext | 获取元素文本 |
| getattribute | 获取属性值 |
| hover | 鼠标悬停 |
| waitvisible | 等待元素可见 |
| waithidden | 等待元素隐藏 |
| scroll | 滚动页面 |
| screenshot | 截图 |
| switchwindow | 切换窗口 |
| customcode | 执行自定义JavaScript |

## 统一响应格式

成功: { code: 1, data: T, msg: null }
失败: { code: 0, data: null, msg: "错误信息" }
