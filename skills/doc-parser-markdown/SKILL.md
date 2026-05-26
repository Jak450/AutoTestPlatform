# Skill: Doc Parser - Markdown

## 描述
解析 Markdown 格式的测试需求文档，提取结构化信息。

## 输入
- Markdown 文档内容

## 处理流程
1. 识别文档中的标题层级（# ## ###），构建文档结构树
2. 提取 API 接口描述段落
   - 接口地址（URL）
   - HTTP 方法（GET/POST/PUT/DELETE）
   - 请求头信息
   - 请求参数/Body
   - 预期响应和断言条件
3. 提取模块归类信息（所属功能模块）
4. 提取测试场景描述（正常流程、异常流程、边界条件）
5. 提取代码块内容（JSON/XML/文本）

## 输出格式
```json
{
  "title": "文档标题",
  "sections": [
    {
      "level": 1,
      "title": "模块名称",
      "content": "描述文本",
      "type": "module|api|assert|note"
    }
  ],
  "apis": [
    {
      "name": "接口名称",
      "module": "所属模块",
      "url": "https://api.example.com/endpoint",
      "method": "POST",
      "headers": {"Content-Type": "application/json"},
      "params": {"key": "value"},
      "expected": {"status": 200, "body": "..."},
      "assertions": ["code == 200", "data.token 存在"]
    }
  ],
  "rawText": "原始文档全文"
}
```

## 约束
- 保留所有技术细节，不丢失关键字段
- 对不确定的信息标注 `"uncertain": true`
- 保持 JSON 结构完整性