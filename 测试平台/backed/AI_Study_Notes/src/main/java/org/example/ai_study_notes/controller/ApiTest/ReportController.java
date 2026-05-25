package org.example.ai_study_notes.controller.ApiTest;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.ReportQueryDTO;
import org.example.ai_study_notes.Pojo.entity.TestCaseReport;
import org.example.ai_study_notes.Pojo.vo.TestCaseReportVO;
import org.example.ai_study_notes.service.TestReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static org.example.ai_study_notes.utils.BuildReportFileNameUtils.buildReportFileName;

/**
 * 测试报告相关接口
 * <p>
 * 提供查询批次、查询用例、导出Allure结果等能力，
 * 方便前端按时间范围筛选后生成测试报告。
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private TestReportService testReportService;

    /**
     * 查询批次下的测试用例执行记录
     *
     * @param queryDTO 查询条件（批次、状态、时间范围）
     * @return 用例执行详情
     */
    @PostMapping("/cases")
    public Result<List<TestCaseReportVO>> listCases(@RequestBody ReportQueryDTO queryDTO) {
        List<TestCaseReport> reports = testReportService.queryCaseReports(queryDTO);
        List<TestCaseReportVO> result = reports.stream()
                .map(TestCaseReportVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 导出Allure结果压缩包
     *
     * @param queryDTO 查询条件（时间范围、批次等）
     * @return Allure结果文件
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportAllure(@RequestBody ReportQueryDTO queryDTO) {
        // 直接生成并返回可打开的 Allure HTML 报告压缩包
        byte[] data = testReportService.exportAllureHtmlZip(queryDTO);
        if (data.length == 0) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        String fileName = buildReportFileName(queryDTO);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }



}