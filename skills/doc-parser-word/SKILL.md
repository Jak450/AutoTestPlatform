# Skill: Doc Parser - Word

## 描述
解析 Word (.doc/.docx) 格式的测试需求文档，提取结构化信息。

## 输入
- Word 文档二进制内容

## 处理流程
1. 提取文档中所有文本，保留标题、段落、列表层次结构
2. 识别表格数据（接口列表、参数字段等）
3. 提取 API 接口描述信息
4. 提取测试场景和断言条件
5. 输出结构化 JSON

## 输出格式
同 doc-parser-markdown 的输出格式。

```json
{
  "title": "文档标题",
  "sections": [...],
  "apis": [
    {
      "name": "接口名称",
      "module": "所属模块",
      "url": "",
      "method": "",
      "headers": {},
      "params": {},
      "expected": {},
      "assertions": []
    }
  ],
  "rawText": "原始文档全文"
}
```

## 注意事项
- 处理 Word 中的嵌套表格和合并单元格
- 提取页眉页脚中的版本信息、日期等元数据
- 保留批注/评论内容（可能包含补充需求）