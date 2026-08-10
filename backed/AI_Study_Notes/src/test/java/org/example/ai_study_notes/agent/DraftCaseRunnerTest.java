package org.example.ai_study_notes.agent;

import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.AssertResult;
import org.example.ai_study_notes.agent.generator.DraftCaseRunner;
import org.example.ai_study_notes.service.ApiTestService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DraftCaseRunnerTest {

    @Test
    void classifiesExecutableAndAssertions() {
        ApiTestService service = mock(ApiTestService.class);
        when(service.run(any(ApiRequestDTO.class))).thenReturn(
                ApiResponseVO.builder()
                        .status(200)
                        .assertResults(List.of(
                                AssertResult.builder().field("status").result(true).build(),
                                AssertResult.builder().field("token").result(true).build()))
                        .build());
        DraftCaseRunner runner = new DraftCaseRunner(service);

        Map<String, Object> report = runner.run(List.of(
                Map.of("name", "正常登录", "url", "http://x/api/login", "method", "POST",
                        "header", "{}", "param", "{}", "assertStr", "{}")));

        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals(1, summary.get("total"));
        assertEquals(1, summary.get("executableRuns"));
        assertEquals(1, summary.get("assertPassedRuns"));
        assertEquals(1, summary.get("usableCases"));
    }

    @Test
    void marksTransportFailureNotExecutable() {
        ApiTestService service = mock(ApiTestService.class);
        when(service.run(any(ApiRequestDTO.class))).thenThrow(new RuntimeException("连接超时"));
        DraftCaseRunner runner = new DraftCaseRunner(service);

        Map<String, Object> report = runner.run(List.of(
                Map.of("name", "超时", "url", "http://x/api", "method", "GET")));

        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        assertEquals(1, summary.get("notExecutableRuns"));
        List<Map<String, Object>> details = (List<Map<String, Object>>) report.get("details");
        List<Map<String, Object>> runs = (List<Map<String, Object>>) details.get(0).get("runs");
        assertEquals("exec_error", runs.get(0).get("category"));
    }
}
