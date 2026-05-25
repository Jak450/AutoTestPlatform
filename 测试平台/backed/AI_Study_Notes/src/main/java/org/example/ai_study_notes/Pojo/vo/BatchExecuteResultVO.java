package org.example.ai_study_notes.Pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量执行结果VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchExecuteResultVO {
    /**
     * 总执行数
     */
    private Integer total;
    
    /**
     * 成功数
     */
    private Integer success;
    
    /**
     * 失败数
     */
    private Integer failed;
    
    /**
     * 总耗时（毫秒）
     */
    private Long totalTime;
    
    /**
     * 执行详情列表
     */
    private List<ExecuteDetailVO> details;
    
    /**
     * 执行详情
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ExecuteDetailVO {
        /**
         * 用例ID
         */
        private Integer useCaseId;
        
        /**
         * 用例名称
         */
        private String useCaseName;
        
        /**
         * 执行次数（第几次执行）
         */
        private Integer executionIndex;
        
        /**
         * 执行结果
         */
        private ApiResponseVO result;
        
        /**
         * 是否成功
         */
        private Boolean success;
        
        /**
         * 执行耗时（毫秒）
         */
        private Long duration;
    }
}

