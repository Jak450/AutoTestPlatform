package org.example.ai_study_notes.Aop;

import io.qameta.allure.Allure;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.ai_study_notes.Aop.anno.ApiTest;
import org.example.ai_study_notes.Pojo.dto.BatchDTO;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;
import org.example.ai_study_notes.service.TestReportService;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 测试报告切面类
 * <p>
 * 该切面是Allure报告生成流程的触发点，负责拦截所有标注了@{@link ApiTest}注解的方法，
 * 收集测试执行的关键信息（如执行时间、结果、异常等），并委托给{@link TestReportService}
 * 生成Allure格式的测试报告数据。
 * </p>
 */
@Aspect
@Component
public class TestReportAspect {

    /**
     * 用例服务，用于获取用例详情信息
     */
    @Autowired
    private UseCaseService useCaseService;

    /**
     * 测试报告服务，核心依赖，负责生成Allure格式的测试报告数据
     */
    @Autowired
    private TestReportService testReportService;

    /**
     * 测试执行报告生成切面
     * <p>
     * 这是Allure报告生成流程的关键入口点，通过AOP技术拦截所有标注了@{@link ApiTest}注解的方法，
     * 实现无侵入式的测试报告数据收集。该切面在不修改原有业务代码的情况下，自动收集测试执行信息并生成报告。
     * </p>
     * 
     * <p>执行流程：</p>
     * <ol>
     *   <li>从方法参数中提取批次信息（BatchDTO）</li>
     *   <li>根据批次ID获取对应的测试用例信息</li>
     *   <li>记录测试开始时间</li>
     *   <li>执行目标测试方法，捕获返回结果或异常</li>
     *   <li>记录测试结束时间</li>
     *   <li>调用报告服务记录测试执行结果，生成Allure报告数据</li>
     * </ol>
     * 
     * @param joinPoint 切点对象，提供对被拦截方法的访问
     * @param apiTest ApiTest注解实例，包含测试相关的元数据
     * @return 被拦截方法的原始返回值，确保业务流程不受影响
     * @throws Throwable 原始方法可能抛出的异常，保证异常正确传递
     */
    @Around("@annotation(apiTest)")
    public Object report(ProceedingJoinPoint joinPoint, ApiTest apiTest) throws Throwable {
        // 获取方法参数列表
        Object[] args = joinPoint.getArgs();
        
        // 从参数中提取批次信息，支持BatchDTO和BatchExecuteDTO两种类型
//        BatchDTO batchDTO = extractBatchDTO(args);
        BatchExecuteDTO batchExecuteDTO = extractBatchExecuteDTO(args);
        
        // 记录测试开始时间
        LocalDateTime startTime = LocalDateTime.now();
        
        // 初始化响应结果和异常变量
        Object result = null;
        Throwable throwable = null;
        
        try {
            // 执行目标测试方法
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            // 捕获测试过程中的异常，用于标记测试状态和生成错误报告
            throwable = ex;
            // 重新抛出异常，不影响原有异常处理流程
            throw ex;
        } finally {
            // 记录测试结束时间
            LocalDateTime endTime = LocalDateTime.now();
            
            // 处理单个用例执行（BatchDTO）
//            if (batchDTO != null && result instanceof ApiResponseVO) {
//                UseCase useCase = Optional.ofNullable(batchDTO.getId())
//                        .map(useCaseService::getUseCasesById)
//                        .orElse(null);
//                if (useCase != null) {
//                    testReportService.recordCaseReport(batchDTO, apiTest, useCase,
//                            (ApiResponseVO) result, throwable, startTime, endTime);
//                }
//            }
            // 处理批量执行（BatchExecuteDTO）
            if (batchExecuteDTO != null && result instanceof BatchExecuteResultVO) {
                recordBatchExecuteReports(batchExecuteDTO, apiTest, (BatchExecuteResultVO) result, 
                        throwable, startTime, endTime);
            }
        }
        
        // 返回原始结果，确保业务流程正常执行
        return result;
    }
    
    /**
     * 记录批量执行的测试报告
     * 为批量执行中的每个用例单独记录报告
     */
    private void recordBatchExecuteReports(BatchExecuteDTO batchExecuteDTO, 
                                          ApiTest apiTest,
                                          BatchExecuteResultVO batchResult,
                                          Throwable throwable,
                                          LocalDateTime batchStartTime,
                                          LocalDateTime batchEndTime) {
        if (batchResult == null || batchResult.getDetails() == null) {
            return;
        }
        
        // 遍历批量执行结果中的每个用例，为每个用例记录报告
        for (BatchExecuteResultVO.ExecuteDetailVO detail : batchResult.getDetails()) {
            try {
                // 获取用例信息
                UseCase useCase = Optional.ofNullable(detail.getUseCaseId())
                        .map(useCaseService::getUseCasesById)
                        .orElse(null);
                
                if (useCase == null) {
                    continue;
                }
                
                // 创建BatchDTO用于报告记录（复用现有接口）
                BatchDTO batchDTO = BatchDTO.builder()
                        .id(detail.getUseCaseId())
                        .build();
                
                // 获取该用例的执行结果
                ApiResponseVO responseVO = detail.getResult();
                
                // 计算该用例的执行时间（使用批量执行的总时间，或使用detail中的duration）
                LocalDateTime caseStartTime = batchStartTime;
                LocalDateTime caseEndTime = batchEndTime;
                
                // 如果有单独的duration，可以更精确地计算时间
                if (detail.getDuration() != null && detail.getDuration() > 0) {
                    caseEndTime = caseStartTime.plusNanos(detail.getDuration() * 1_000_000);
                }
                
                // 记录该用例的报告
                testReportService.recordCaseReport(batchDTO, apiTest, useCase, 
                        responseVO, throwable, caseStartTime, caseEndTime);
            } catch (Exception e) {
                // 单个用例报告记录失败不影响其他用例
                e.printStackTrace();
            }
        }
    }

    /**
     * 从方法参数中提取BatchDTO对象
     * <p>
     * 该辅助方法使用流式API查找参数数组中的BatchDTO实例，与参数位置无关，
     * 提高了代码的灵活性和可维护性，即使未来方法参数顺序发生变化，也不会影响报告生成。
     * </p>
     * 
     * @param args 方法参数数组
     * @return 找到的BatchDTO实例，如果不存在则返回null
     */
    private BatchDTO extractBatchDTO(Object[] args) {
        // 参数校验，避免空指针异常
        if (args == null || args.length == 0) {
            return null;
        }
        
        // 使用Stream API查找BatchDTO类型的参数
        return Arrays.stream(args)
                .filter(BatchDTO.class::isInstance)  // 过滤出BatchDTO类型的参数
                .map(BatchDTO.class::cast)           // 类型转换
                .findFirst()                         // 获取第一个匹配的参数
                .orElse(null);                       // 如果没有找到，返回null
    }
    
    /**
     * 从方法参数中提取BatchExecuteDTO对象
     * <p>
     * 支持批量并发执行的报告记录
     * </p>
     * 
     * @param args 方法参数数组
     * @return 找到的BatchExecuteDTO实例，如果不存在则返回null
     */
    private BatchExecuteDTO extractBatchExecuteDTO(Object[] args) {
        // 参数校验，避免空指针异常
        if (args == null || args.length == 0) {
            return null;
        }
        
        // 使用Stream API查找BatchExecuteDTO类型的参数
        return Arrays.stream(args)
                .filter(BatchExecuteDTO.class::isInstance)  // 过滤出BatchExecuteDTO类型的参数
                .map(BatchExecuteDTO.class::cast)            // 类型转换
                .findFirst()                                  // 获取第一个匹配的参数
                .orElse(null);                                // 如果没有找到，返回null
    }
}
