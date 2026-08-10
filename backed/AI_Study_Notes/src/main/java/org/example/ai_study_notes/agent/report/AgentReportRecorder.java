package org.example.ai_study_notes.agent.report;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Aop.anno.ApiTest;
import org.example.ai_study_notes.Pojo.dto.BatchDTO;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;
import org.example.ai_study_notes.service.TestReportService;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;

/**
 * Agent 工具层报告落库：
 * 工具直调 Service 会绕过 Controller 上的 @ApiTest 切面，这里显式补录批量执行报告，
 * 与 TestReportAspect 行为保持一致。
 */
@Slf4j
@Component
public class AgentReportRecorder {

    private final TestReportService testReportService;
    private final UseCaseService useCaseService;

    public AgentReportRecorder(TestReportService testReportService, UseCaseService useCaseService) {
        this.testReportService = testReportService;
        this.useCaseService = useCaseService;
    }

    public void recordBatch(BatchExecuteDTO batchExecuteDTO, BatchExecuteResultVO result) {
        if (result == null || result.getDetails() == null || batchExecuteDTO == null) {
            return;
        }
        ApiTest apiTest = apiTestProxy();
        LocalDateTime batchStart = LocalDateTime.now().minusNanos(result.getTotalTime() == null
                ? 0 : result.getTotalTime() * 1_000_000L);
        LocalDateTime batchEnd = LocalDateTime.now();
        for (BatchExecuteResultVO.ExecuteDetailVO detail : result.getDetails()) {
            try {
                UseCase useCase = detail.getUseCaseId() == null ? null : useCaseService.getUseCasesById(detail.getUseCaseId());
                if (useCase == null) {
                    continue;
                }
                BatchDTO batchDTO = BatchDTO.builder().id(detail.getUseCaseId()).build();
                ApiResponseVO responseVO = detail.getResult();
                LocalDateTime caseStart = batchStart;
                LocalDateTime caseEnd = batchEnd;
                if (detail.getDuration() != null && detail.getDuration() > 0) {
                    caseEnd = caseStart.plusNanos(detail.getDuration() * 1_000_000L);
                }
                testReportService.recordCaseReport(batchDTO, apiTest, useCase, responseVO, null, caseStart, caseEnd);
            } catch (Exception e) {
                log.warn("Agent 批量执行报告落库失败 caseId={}: {}", detail.getUseCaseId(), e.getMessage());
            }
        }
    }

    private ApiTest apiTestProxy() {
        return (ApiTest) Proxy.newProxyInstance(
                ApiTest.class.getClassLoader(),
                new Class<?>[]{ApiTest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "name" -> "接口测试";
                    case "module" -> "Agent批量执行";
                    case "description" -> "Agent 批量执行接口测试用例";
                    case "annotationType" -> ApiTest.class;
                    default -> method.getDefaultValue();
                });
    }
}
