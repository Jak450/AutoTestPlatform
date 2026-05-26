package org.example.ai_study_notes.Pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试用例执行详情实体
 * <p>
 * 一条记录对应Allure中的一条测试记录，可用于生成Allure报告文件。
 * 此类是Allure报告生成的核心数据模型，存储了所有测试执行信息和Allure所需的元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("test_case_report")
public class TestCaseReport {

    /**
     * 主键ID（自增）
     */
    @TableId
    private Long id;

    /**
     * 用例ID（对应用例库主键）
     */
    private Integer caseId;

    /**
     * 用例名称
     */
    private String caseName;

    /**
     * 所属模块
     */
    private String moduleName;

    /**
     * 用例描述
     */
    private String description;

    /**
     * API地址
     */
    private String apiUrl;

    /**
     * HTTP方法
     */
    private String requestMethod;

    /**
     * 请求头（原始JSON字符串）
     */
    private String requestHeaders;

    /**
     * 请求体（原始JSON字符串）
     */
    private String requestBody;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 响应Headers（JSON字符串）
     */
    private String responseHeaders;

    /**
     * 响应Body（JSON or Text）
     */
    private String responseBody;

    /**
     * 响应耗时，毫秒
     */
    private Long duration;

    /**
     * 断言结果详情（JSON字符串）
     */
    private String assertDetail;

    /**
     * 用例执行状态：passed/failed/broken
     */
    private String status;

    /**
     * Allure Result JSON字符串，存储生成Allure报告所需的完整元数据
     * <p>
     * 此字段是与Allure报告系统集成的关键，包含了UUID、历史ID、开始/结束时间、标签、参数、附件等
     * Allure报告引擎所需的全部信息，遵循Allure规范格式。
     */
    private String allureResultJson;

    /**
     * 用例开始时间
     */
    private LocalDateTime startTime;

    /**
     * 用例结束时间
     */
    private LocalDateTime endTime;

    /**
     * 记录生成时间
     */
    private LocalDateTime createdAt;
}

