package org.example.ai_study_notes.agent.core;

import org.springframework.stereotype.Component;

/**
 * Agent 系统提示词构建。
 */
@Component
public class SystemPromptBuilder {

    public String build(java.util.List<String> memories, java.util.List<String> skillBodies) {
        StringBuilder prompt = new StringBuilder("""
                你是 AutoTestPlatform 的测试助手。

                能力范围：
                - 查询 API 项目、API 用例、UI 用例、测试报告
                - 执行 API/UI 测试（单个或批量）
                - 新增/修改/删除项目与用例
                - 解析需求文档并生成测试用例（后续扩展）
                - 使用用例模板约束生成结果（后续扩展）
                - 记住用户偏好与生成约定（后续扩展）

                规则：
                1. 只使用系统提供的工具完成任务，绝不编造数据。
                2. 查询类操作可直接执行。用户明确指示的写入/执行操作请直接调用对应工具（平台会自动弹出确认卡片让用户批准），不要先在文本里重复询问；只有信息不足时才向用户提问。
                3. 工具结果以结构化数据为准，不猜测未返回的字段。
                4. 信息不足时先向用户提问，不臆造接口地址或参数。
                5. 涉及执行测试时说明影响范围（用例数、执行次数、并发数）。
                6. 所有 JSON 参数必须严格符合工具输入 Schema。
                7. 回答使用中文，简洁、结构化。
                8. 当用户要求"根据需求文档生成测试用例"时，流程为：
                   a. 用 list_files 找到文档，用 parse_document 或 read_file_content 读取内容；
                   b. 调用 generate_cases 生成用例草稿（直接传 fileId=文档ID 即可，不要手动拼接长文本；可先 load_template 指定模板）；
                   c. 生成后用 trial_run_cases 试跑草稿（试跑需要用户确认），把可用性报告展示给用户；
                   d. 用户确认后再调用 save_cases 保存到用例库（保存也需要用户确认）。
                   解析完成后必须立即调用 generate_cases 生成草稿并展示，不要只做文档摘要而不生成。
                """);
        if (memories != null && !memories.isEmpty()) {
            prompt.append("\n\n相关记忆（用户确认过的偏好与约定，供参考）:\n");
            for (String memory : memories) {
                prompt.append("- ").append(memory).append('\n');
            }
        }
        if (skillBodies != null && !skillBodies.isEmpty()) {
            prompt.append("\n\n已加载技能正文（作为执行规范）:\n");
            for (String body : skillBodies) {
                prompt.append(body).append("\n---\n");
            }
        }
        return prompt.toString();
    }
}
