package org.example.ai_study_notes.service;

import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;

public interface ApiTestService {
    ApiResponseVO run(ApiRequestDTO apiRequestDTO);
    
    /**
     * 批量并发执行接口测试
     * @param batchExecuteDTO 批量执行请求
     * @return 批量执行结果
     */
    BatchExecuteResultVO batchExecute(BatchExecuteDTO batchExecuteDTO);
}
