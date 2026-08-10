package org.example.ai_study_notes.agent.generator;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.AssertResult;
import org.example.ai_study_notes.service.ApiTestService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        List<Map<String, Object>> details = new ArrayList<>();
        int executable = 0;
        int assertPassed = 0;
        int assertFailed = 0;
        int noAssert = 0;
        int usable = 0;
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> c = cases.get(i);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("index", i + 1);
            detail.put("name", c.get("name"));
            detail.put("method", c.get("method"));
            detail.put("url", c.get("url"));
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
                detail.put("executable", exec);
                detail.put("status", response.getStatus());
                detail.put("timeMs", response.getTime());
                if (!exec) {
                    detail.put("category", "no_response");
                    detail.put("message", "未收到 HTTP 响应");
                    details.add(detail);
                    continue;
                }
                executable++;
                int status = response.getStatus();
                String category = status >= 200 && status < 300 ? "http_ok"
                        : status >= 300 && status < 400 ? "http_redirect"
                        : status >= 400 && status < 500 ? "http_client_error"
                        : "http_server_error";
                detail.put("category", category);
                List<AssertResult> asserts = response.getAssertResults();
                if (asserts == null || asserts.isEmpty()) {
                    noAssert++;
                    detail.put("assertStatus", "no_assert");
                    detail.put("message", "未配置断言（已可执行，建议补充断言）");
                    details.add(detail);
                    continue;
                }
                boolean allPassed = asserts.stream().allMatch(a -> Boolean.TRUE.equals(a.getResult()));
                detail.put("assertStatus", allPassed ? "passed" : "failed");
                detail.put("assertCount", asserts.size());
                detail.put("message", allPassed ? "断言全部通过" : "存在未通过的断言");
                if (allPassed) {
                    assertPassed++;
                    usable++;
                } else {
                    assertFailed++;
                }
            } catch (Exception e) {
                detail.put("executable", false);
                detail.put("category", "exec_error");
                detail.put("message", "执行异常: " + e.getMessage());
                details.add(detail);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", cases.size());
        summary.put("executable", executable);
        summary.put("notExecutable", cases.size() - executable);
        summary.put("assertPassed", assertPassed);
        summary.put("assertFailed", assertFailed);
        summary.put("noAssert", noAssert);
        summary.put("usable", usable);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("details", details);
        return result;
    }
}
