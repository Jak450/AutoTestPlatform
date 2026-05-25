package org.example.ai_study_notes.Pojo.dto.UiDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 描述 UI 自动化步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UiTestStepDTO {

    private String name;
    private String action;
    private String locatorType;
    private String locatorValue;
    private String actionValue;
    private String customCode;
    private Integer waitTime;
    private UiAssertionDTO assertion;
}

