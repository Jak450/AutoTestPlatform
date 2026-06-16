---
name: doc-parser-pdf
description: 解析 PDF 格式的需求文档，提取结构化信息（功能点、接口、字段等）并输出 JSON
---

# Skill: Doc Parser - PDF

## 描述
解析 PDF 格式的测试需求文档，提取结构化信息。

## 输入
- PDF 文档二进制内容

## 处理流程
1. 提取 PDF 中所有文本内容，保留段落结构
2. 识别表格数据（API 接口列表、参数表格等）
3. 提取 API 接口描述信息
4. 提取测试场景和断言条件
5. 输出结构化 JSON

## 输出格式
同 doc-parser-markdown 的输出格式，确保 Pipeline Orchestrator 可以统一处理。

```json
{
  "title": "文档标题",
  "sections": [...],
  "apis": [
    {
      "name": "接口名称",
      "module": "所属模块",
      "url": "https://api.example.com/endpoint",
      "method": "POST",
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
- PDF 中的表格需要逐行解析，保持行列对应关系
- 多栏布局需要按阅读顺序重新排列
- 图片中的文字无法提取，标注 `"[图片内容无法解析]"`