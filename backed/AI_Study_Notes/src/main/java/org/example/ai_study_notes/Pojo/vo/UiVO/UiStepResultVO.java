package org.example.ai_study_notes.Pojo.vo.UiVO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiStepResultVO {
    private String name;
    private String action;
    private String status;
    private long duration;
    private String message;
    private String screenshotBase64;
    private String actualValue;
}

