package org.example.ai_study_notes.Pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ai_study_notes.Pojo.entity.TestCaseReport;

import java.time.LocalDateTime;

/**
 * 用例粒度的测试报告展示对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseReportVO {

    private Long id;
    private Integer caseId;
    private String caseName;
    private String moduleName;
    private String description;
    private String apiUrl;
    private String requestMethod;
    private String requestHeaders;
    private String requestBody;
    private Integer responseStatus;
    private String responseHeaders;
    private String responseBody;
    private Long duration;
    private String assertDetail;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;

    public static TestCaseReportVO fromEntity(TestCaseReport report) {
        if (report == null) {
            return null;
        }
        return TestCaseReportVO.builder()
                .id(report.getId())
                .caseId(report.getCaseId())
                .caseName(report.getCaseName())
                .moduleName(report.getModuleName())
                .description(report.getDescription())
                .apiUrl(report.getApiUrl())
                .requestMethod(report.getRequestMethod())
                .requestHeaders(report.getRequestHeaders())
                .requestBody(report.getRequestBody())
                .responseStatus(report.getResponseStatus())
                .responseHeaders(report.getResponseHeaders())
                .responseBody(report.getResponseBody())
                .duration(report.getDuration())
                .assertDetail(report.getAssertDetail())
                .status(report.getStatus())
                .startTime(report.getStartTime())
                .endTime(report.getEndTime())
                .createdAt(report.getCreatedAt())
                .build();
    }
}

