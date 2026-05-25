package org.example.ai_study_notes.Pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量执行DTO
 * 用于接收前端传来的批量执行请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchExecuteDTO {
    /**
     * 用例ID列表
     */
    private List<Integer> useCaseIds;
    
    /**
     * 执行次数（每个用例执行多少次）
     */
    private Integer executionCount;
    
    /**
     * 最大并发数
     */
    private Integer maxConcurrency;
}

