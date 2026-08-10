package org.example.ai_study_notes.agent.generator;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.AssertResult;
import org.example.ai_study_notes.service.ApiTestService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿试跑：对生成的用例草稿真实执行一次（不落报告、不保存），
 * 输出可用性分类（可执行/断言/失败原因）。
 */
@Slf4j
@Component
public class DraftCaseRunner {

    private final ApiTestService apiTestService;

    public DraftCaseRunner(ApiTestService apiTestService) {
        this.apiTestService = apiTestService;
    }

    public Map<String, Object> run(List<Map<String, Object>> cases) {
        return run(cases, 1, null);
    }

    /**
     * 多轮试跑：每条用例执行 repeat 次（默认 1），可选随机抽样 sampleSize 条；
     * 输出通过率、失败分类与抖动（flaky）标记。
     */
    public Map<String, Object> run(List<Map<String, Object>> cases, int repeat, Integer sampleSize) {
        int safeRepeat = Math.max(1, Math.min(repeat, 20));
        List<Map<String, Object>> sampled = cases;
        if (sampleSize != null && sampleSize > 0 && sampleSize < cases.size()) {
            List<Map<String, Object>> shuffled = new ArrayList<>(cases);
            Collections.shuffle(shuffled);
            sampled = new ArrayList<>(shuffled.subList(0, sampleSize));
        }
        List<Map<String, Object>> details = new ArrayList<>();
        int executableRuns = 0;
        int assertPassedRuns = 0;
        int assertFailedRuns = 0;
        int noAssertRuns = 0;
        int usableCases = 0;
        int flakyCases = 0;
        for (int i = 0; i < sampled.size(); i++) {
            Map<String, Object> c = sampled.get(i);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", c.get("name"));
            detail.put("method", c.get("method"));
            detail.put("url", c.get("url"));
            List<Map<String, Object>> runs = new ArrayList<>();
            for (int r = 0; r < safeRepeat; r++) {
                runs.add(runOnce(c));
            }
            for (Map<String, Object> run : runs) {
                if (Boolean.TRUE.equals(run.get("executable"))) {
                    executableRuns++;
                }
                String assertStatus = String.valueOf(run.getOrDefault("assertStatus", ""));
                switch (assertStatus) {
                    case "passed" -> assertPassedRuns++;
                    case "failed" -> assertFailedRuns++;
                    case "no_assert" -> noAssertRuns++;
                    default -> {
                    }
                }
            }
            boolean allExecutable = runs.stream().allMatch(r -> Boolean.TRUE.equals(r.get("executable")));
            boolean anyAssertFailed = runs.stream().anyMatch(r -> "failed".equals(String.valueOf(r.get("assertStatus"))));
            boolean allAssertPassed = runs.stream().allMatch(r -> "passed".equals(String.valueOf(r.get("assertStatus"))));
            boolean usable = allExecutable && allAssertPassed;
            boolean flaky = allExecutable && !usable
                    && runs.stream().anyMatch(r -> "passed".equals(String.valueOf(r.get("assertStatus"))))
                    && anyAssertFailed;
            detail.put("runs", runs);
            detail.put("executableRuns", (int) runs.stream().filter(r -> Boolean.TRUE.equals(r.get("executable"))).count());
            detail.put("usable", usable);
            detail.put("flaky", flaky);
            if (usable) {
                usableCases++;
            }
            if (flaky) {
                flakyCases++;
            }
            details.add(detail);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", cases.size());
        summary.put("sampled", sampled.size());
        summary.put("repeat", safeRepeat);
        summary.put("totalRuns", sampled.size() * safeRepeat);
        summary.put("executableRuns", executableRuns);
        summary.put("notExecutableRuns", sampled.size() * safeRepeat - executableRuns);
        summary.put("assertPassedRuns", assertPassedRuns);
        summary.put("assertFailedRuns", assertFailedRuns);
        summary.put("noAssertRuns", noAssertRuns);
        summary.put("usableCases", usableCases);
        summary.put("flakyCases", flakyCases);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("details", details);
        return result;
    }

    private Map<String, Object> runOnce(Map<String, Object> c) {
        Map<String, Object> run = new LinkedHashMap<>();
        try {
            ApiRequestDTO request = ApiRequestDTO.builder()
                    .method(String.valueOf(c.get("method")))
                    .url(String.valueOf(c.get("url")))
                    .header(c.get("header") == null ? null : String.valueOf(c.get("header")))
                    .param(c.get("param") == null ? null : String.valueOf(c.get("param")))
                    .assertStr(c.get("assertStr") == null ? null : String.valueOf(c.get("assertStr")))
                    .build();
            ApiResponseVO response = apiTestService.run(request);
            boolean exec = response.getStatus() != null;
            run.put("executable", exec);
            run.put("status", response.getStatus());
            run.put("timeMs", response.getTime());
            if (!exec) {
                run.put("category", "no_response");
                run.put("assertStatus", "no_assert");
                run.put("message", "未收到 HTTP 响应");
                return run;
            }
            int status = response.getStatus();
            String category = status >= 200 && status < 300 ? "http_ok"
                    : status >= 300 && status < 400 ? "http_redirect"
                    : status >= 400 && status < 500 ? "http_client_error"
                    : "http_server_error";
            run.put("category", category);
            List<AssertResult> asserts = response.getAssertResults();
            if (asserts == null || asserts.isEmpty()) {
                run.put("assertStatus", "no_assert");
                run.put("message", "未配置断言（已可执行，建议补充断言）");
                return run;
            }
            boolean allPassed = asserts.stream().allMatch(a -> Boolean.TRUE.equals(a.getResult()));
            run.put("assertStatus", allPassed ? "passed" : "failed");
            run.put("assertCount", asserts.size());
            run.put("message", allPassed ? "断言全部通过" : "存在未通过的断言");
        } catch (Exception e) {
            run.put("executable", false);
            run.put("category", "exec_error");
            run.put("assertStatus", "no_assert");
            run.put("message", "执行异常: " + e.getMessage());
        }
        return run;
    }
}
