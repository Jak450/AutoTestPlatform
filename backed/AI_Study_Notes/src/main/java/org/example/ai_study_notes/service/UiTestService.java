package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestCaseRequestDTO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiBatchExecuteResultVO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiTestResultVO;

public interface UiTestService {
    UiTestResultVO run(UiTestCaseRequestDTO requestDTO);

    UiBatchExecuteResultVO batchTest(BatchExecuteDTO batchExecuteDTO);
}

