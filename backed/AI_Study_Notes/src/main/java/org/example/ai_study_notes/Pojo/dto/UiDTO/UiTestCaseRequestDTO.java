package org.example.ai_study_notes.Pojo.dto.UiDTO;

import lombok.Data;

import java.util.List;

/**
 * 前端提交的 UI 测试请求
 */
@Data
public class UiTestCaseRequestDTO {

    private String url;

    private String browser;

    private String viewport;

    private Boolean headless;

    private Integer timeout;

    private List<UiTestStepDTO> steps;
}

