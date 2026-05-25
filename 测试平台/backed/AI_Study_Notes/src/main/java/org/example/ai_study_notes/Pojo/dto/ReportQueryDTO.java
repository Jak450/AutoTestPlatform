package org.example.ai_study_notes.Pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试报告查询条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportQueryDTO {

    /**
     * 模块名称筛选
     */
    private String moduleName;

    /**
     * 用例名称关键字
     */
    private String caseName;

    /**
     * 用例执行状态：passed/failed/broken
     */
    private String status;

    /**
     * 开始时间（包含）
     */
    private LocalDateTime startTime;

    /**
     * 结束时间（包含）
     */
    private LocalDateTime endTime;
}

