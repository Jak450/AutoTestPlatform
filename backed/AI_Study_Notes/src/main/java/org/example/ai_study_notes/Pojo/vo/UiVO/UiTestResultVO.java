package org.example.ai_study_notes.Pojo.vo.UiVO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiTestResultVO {
    private String executionId;
    private boolean success;
    private long duration;
    private int passedSteps;
    private int failedSteps;
    private String error;
    private List<UiStepResultVO> steps;
}

