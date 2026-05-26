package org.example.ai_study_notes.Task;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.dto.ReportQueryDTO;
import org.example.ai_study_notes.service.TestReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "task.scheduler.enable", name = "accessUserInfo" ,havingValue = "true")
public class CleanTestReportCaseTask {


    @Autowired
    private TestReportService testReportService;

    @Scheduled(cron = "${task.cron.accessUserInfo}")
    public void cleanTestReport() {

        ReportQueryDTO reportQueryDTO = new ReportQueryDTO();

        reportQueryDTO.setStartTime(LocalDateTime.now().minusDays(1));
        reportQueryDTO.setEndTime(LocalDateTime.now());
        log.info("定时删除数据");
        testReportService.deleteCaseReports(reportQueryDTO);
//        log.info("5分钟后查到的{}", testReportService.queryCaseReports(reportQueryDTO));

    }



}
