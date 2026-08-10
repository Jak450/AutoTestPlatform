package org.example.ai_study_notes.agent.tool.tools;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.generator.DraftCaseRunner;
import org.example.ai_study_notes.agent.tool.Args;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 草稿试跑：对生成的用例草稿真实执行一次（不保存、不落报告），返回可用性报告。
 */
@Component("agentTrialRunCasesTool")
public class TrialRunCasesTool implements ToolExecutor {

    private final DraftCaseRunner draftCaseRunner;

    public TrialRunCasesTool(DraftCaseRunner draftCaseRunner) {
        this.draftCaseRunner = draftCaseRunner;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("trial_run_cases")
                .label("草稿试跑")
                .description("对用例草稿真实执行（不保存、不落报告），返回是否可执行、断言是否通过、失败原因分类与汇总；repeat 为每条执行次数（默认1），sampleSize 可随机抽样前 N 条")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "cases", Map.of("type", "array", "items", Map.of("type", "object"), "description", "用例草稿数组（name/url/method/header/param/assertStr）"),
                                "repeat", Map.of("type", "integer", "minimum", 1, "maximum", 20, "description", "每条用例执行次数，默认 1"),
                                "sampleSize", Map.of("type", "integer", "minimum", 1, "description", "随机抽样条数（可选，小于总数时生效）")),
                        "required", List.of("cases")))
                .permission(ToolPermission.CONFIRM_EXECUTE)
                .category("生成")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(this)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        List<Map<String, Object>> cases = Args.listOfMaps(args, "cases");
        Integer repeat = Args.integer(args, "repeat", 1);
        Integer sampleSize = Args.integer(args, "sampleSize", null);
        Map<String, Object> report = draftCaseRunner.run(cases, repeat, sampleSize);
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        return ToolResult.success("trial_run_cases", report,
                "试跑完成：共 " + summary.get("total") + " 条（抽样 " + summary.get("sampled")
                        + "，每条 " + summary.get("repeat") + " 次），可用 " + summary.get("usableCases")
                        + " 条，抖动 " + summary.get("flakyCases") + " 条");
    }
}
