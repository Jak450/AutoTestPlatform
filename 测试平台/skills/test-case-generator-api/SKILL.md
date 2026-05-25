# Skill: Test Case Generator - API

## 描述
根据需求文档解析结果 + 用户 Q&A 回答，生成符合 use_case 表 schema 的 API 测试用例 JSON，可直接通过 POST /api/use-cases 写入数据库。

## 目标 Schema
每个生成的用例必须严格符合以下结构：

```json
{
  "pid": 1,
  "name": "登录接口-正常流程",
  "url": "https://api.example.com/login",
  "method": "POST",
  "header": "{\"Content-Type\": \"application/json\"}",
  "param": "{\"username\":\"admin\",\"password\":\"123456\"}",
  "assertStr": "{\"code\": 200, \"data.token\": \"regex:^[a-z]+\"}",
  "description": "验证用户名密码正确时能成功登录"
}
```

### 字段约束
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pid | int | 是 | 所属项目ID（由用户选择或新建） |
| name | string | 是 | 用例名称，格式: `{接口名}-{场景}` |
| url | string | 是 | 完整接口地址 |
| method | string | 是 | GET/POST/PUT/DELETE/PATCH |
| header | string | 否 | JSON 字符串格式的请求头 |
| param | string | 否 | JSON 字符串格式的请求参数/Body |
| assertStr | string | 否 | JSON 格式断言配置 |
| description | string | 否 | 用例描述 |

### assertStr 格式
```json
{
  "code": 200,
  "data.token": "regex:^[a-z]+",
  "data.name": "期望值"
}
```
- 支持精确匹配: `"field": "value"`
- 支持正则匹配: `"field": "regex:pattern"`
- 支持嵌套字段: `"data.token"` 表示 data 对象下的 token 字段

## 处理流程

### Step 1: 输入分析
接收来自 Orchestrator 的上下文，包含：
- `parsedDoc`: doc-parser 输出的结构化文档
- `qaHistory`: 用户 Q&A 记录
- `projectId`: 用户选择的目标项目 ID

### Step 2: 问题生成
对比需求和 schema，自动发现缺失信息，生成澄清问题：
- URL 不完整 → "请提供完整的接口地址"
- 方法缺失 → "该接口使用什么 HTTP 方法？"
- 参数示例缺失 → "请提供一个请求参数示例"
- 断言条件不明确 → "如何判断接口返回成功？"
- 场景未覆盖 → "是否需要考虑异常流程？（如参数错误、未授权等）"

问题格式：
```json
{
  "questions": [
    {
      "id": 1,
      "field": "url",
      "question": "登录接口的完整地址是什么？",
      "context": "文档中提到登录功能但未给出URL"
    }
  ]
}
```

### Step 3: 用例生成
根据完整信息生成测试用例，分类策略：
- **正常流程**: 正确的参数组合，期望成功返回
- **异常流程**: 缺失参数、错误参数、未授权等
- **边界条件**: 参数长度限制、数值范围等
- **状态流转**: 多次调用状态变化

每个用例要生成独立的 JSON 对象，不要合并。

### Step 4: 输出
返回 use_case JSON 数组：
```json
{
  "projectId": 1,
  "projectName": "用户系统",
  "cases": [
    {
      "pid": 1,
      "name": "登录接口-正常流程",
      "url": "https://api.example.com/login",
      "method": "POST",
      "header": "{\"Content-Type\":\"application/json\"}",
      "param": "{\"username\":\"admin\",\"password\":\"123456\"}",
      "assertStr": "{\"code\":200,\"data.token\":\"regex:^[a-z]+\"}",
      "description": "验证用户名密码正确时能成功登录"
    }
  ],
  "summary": {
    "total": 5,
    "normal": 2,
    "abnormal": 2,
    "boundary": 1
  }
}
```

## 约束
- **必须**严格遵循 use_case schema，不可自行添加字段
- header/param/assertStr 必须是 JSON 字符串（序列化后的）
- URL 必须是完整地址（含协议头）
- method 必须是大写
- name 必须唯一且描述清晰
- 如果信息不足必须提问，不允许编造数据
- 不生成 UI 测试用例（那是 test-case-generator-ui 的职责）