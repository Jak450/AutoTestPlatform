package org.example.ai_study_notes.utils;

import org.example.ai_study_notes.Pojo.dto.ReportQueryDTO;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

public class BuildReportFileNameUtils {


    public static String buildReportFileName(ReportQueryDTO queryDTO) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss");
        StringBuilder name = new StringBuilder("测试报告-");
        if (queryDTO != null) {
            if (queryDTO.getStartTime() != null) {
                name.append("从").append(queryDTO.getStartTime().format(fmt));
            }
            if (queryDTO.getEndTime() != null) {
                name.append("到").append(queryDTO.getEndTime().format(fmt));
            }
            if (queryDTO.getModuleName() != null && !queryDTO.getModuleName().isEmpty()) {
                name.append("模块").append(queryDTO.getModuleName().replaceAll("\\s+", "_"));
            }
        }
        name.append(".zip");
        return URLEncoder.encode(name.toString(), StandardCharsets.UTF_8);
    }
}
