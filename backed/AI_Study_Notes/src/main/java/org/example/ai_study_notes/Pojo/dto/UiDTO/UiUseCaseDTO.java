package org.example.ai_study_notes.Pojo.dto.UiDTO;

import lombok.Data;

import java.util.List;

/**
 * UI 用例的创建 / 更新 / 展示 DTO
 */
@Data
public class UiUseCaseDTO {

    private Integer id;

    /**
     * 所属项目ID
     */
    private String projectId;

    /**
     * 所属项目名称（仅用于展示）
     */
    private String projectName;

    /**
     * 用例名称
     */
    private String name;

    /**
     * 用例描述
     */
    private String description;

    /**
     * 目标 URL
     */
    private String url;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 窗口大小
     */
    private String viewport;

    /**
     * 是否无头模式
     */
    private Boolean headless;

    /**
     * 超时时间（秒）
     */
    private Integer timeout;

    /**
     * 测试步骤
     */
    private List<UiTestStepDTO> steps;
}


