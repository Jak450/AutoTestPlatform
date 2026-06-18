package org.example.ai_study_notes.service.Impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.POM.driver.WebDriverFactory;
import org.example.ai_study_notes.POM.page.BasePage;
import org.example.ai_study_notes.Pojo.dto.ApiRequestDTO;
import org.example.ai_study_notes.Pojo.dto.BatchExecuteDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestCaseRequestDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestStepDTO;
import org.example.ai_study_notes.Pojo.entity.UI.UIUseCase;
import org.example.ai_study_notes.Pojo.entity.UseCase;
import org.example.ai_study_notes.Pojo.vo.ApiResponseVO;
import org.example.ai_study_notes.Pojo.vo.BatchExecuteResultVO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiBatchExecuteResultVO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiStepResultVO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiTestResultVO;
import org.example.ai_study_notes.mapper.UIUseCaseMapper;
import org.example.ai_study_notes.service.UIUseCaseService;
import org.example.ai_study_notes.service.UiTestService;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UiTestServiceImpl implements UiTestService {

    @Autowired
    @Qualifier("testExecutor")
    private Executor uiTestExecutor;

    @Autowired
    private UIUseCaseMapper uiUseCaseMapper;

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public UiTestResultVO run(UiTestCaseRequestDTO requestDTO) {
        validateRequest(requestDTO);
        long start = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();
        List<UiStepResultVO> stepResults = new ArrayList<>();
        boolean success = true;
        String errorMessage = null;

        WebDriver driver = null;
        try {
            driver = WebDriverFactory.create(requestDTO);
            driver.get(requestDTO.getUrl());
            Duration timeout = Duration.ofSeconds(requestDTO.getTimeout() == null || requestDTO.getTimeout() <= 0 ? 30 : requestDTO.getTimeout());
            BasePage page = new BasePage(driver, timeout);

            for (UiTestStepDTO step : requestDTO.getSteps()) {
                UiStepResultVO result = page.executeStep(step);
                stepResults.add(result);
                if (!"passed".equalsIgnoreCase(result.getStatus())) {
                    success = false;
                    errorMessage = result.getMessage();
                    break;
                }
            }
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("UI 异常报错：", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        long duration = System.currentTimeMillis() - start;
        UiTestResultVO resultVO = UiTestResultVO.builder()
                .executionId(executionId)
                .success(success)
                .duration(duration)
                .passedSteps((int) stepResults.stream().filter(r -> "passed".equalsIgnoreCase(r.getStatus())).count())
                .failedSteps((int) stepResults.stream().filter(r -> !"passed".equalsIgnoreCase(r.getStatus())).count())
                .steps(stepResults)
                .error(errorMessage)
                .build();
        return resultVO;
    }

    @Override
    public UiBatchExecuteResultVO batchTest(BatchExecuteDTO batchExecuteDTO) {
        long start=System.currentTimeMillis();

        List<Integer> useCaseIds = batchExecuteDTO.getUseCaseIds();
        Integer executionCount = batchExecuteDTO.getExecutionCount() != null ? batchExecuteDTO.getExecutionCount() : 1;
        Integer maxConcurrency = batchExecuteDTO.getMaxConcurrency() != null ? batchExecuteDTO.getMaxConcurrency() : 10;

        List<UiBatchExecuteResultVO.ExecuteDetailVO> details=new ArrayList<>();
        int successCount=0;
        int failedCount=0;
        //控制并发数
        Semaphore semaphore = new Semaphore(maxConcurrency);

        Map<Integer, UIUseCase> useCaseMap = new HashMap<>();
        for(Integer useCaseId : useCaseIds) {
            UIUseCase useCase = uiUseCaseMapper.selectById(useCaseId);
            if (useCase != null) {
                useCaseMap.put(useCaseId, useCase);
            }
        }

        // 构建所有需要执行的任务
        List<CompletableFuture<UiBatchExecuteResultVO.ExecuteDetailVO>> futures = new ArrayList<>();

        for (Integer useCaseId : useCaseIds) {
            UIUseCase useCase = useCaseMap.get(useCaseId);
            if (useCase == null) {
                continue;
            }

            for (int i=0;i<executionCount;i++) {

                final int executionIndex = i + 1;
                final UIUseCase finalUseCase = useCase;
                CompletableFuture<UiBatchExecuteResultVO.ExecuteDetailVO> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // 获取信号量许可（控制并发数）
                        semaphore.acquire();

                        long taskStartTime = System.currentTimeMillis();

                        // 执行UI测试
                        UiTestCaseRequestDTO requestDTO = new UiTestCaseRequestDTO();
                        BeanUtils.copyProperties(finalUseCase, requestDTO);
                        String stepsJson = finalUseCase.getSteps();

                        if (stepsJson != null) {
                            List<UiTestStepDTO> steps = objectMapper.readValue(stepsJson, new TypeReference<>() {
                            });
                            requestDTO.setSteps(steps);
                        }


                        UiTestResultVO responseVO = run(requestDTO);

                        long taskDuration = System.currentTimeMillis() - taskStartTime;




                        return UiBatchExecuteResultVO.ExecuteDetailVO.builder()
                                .useCaseId(finalUseCase.getId())
                                .useCaseName(finalUseCase.getName())
                                .executionIndex(executionIndex)
                                .result(responseVO)
                                .success(responseVO.isSuccess())
                                .duration(taskDuration)
                                .build();
                    } catch (Exception e) {
                        log.error("UI 批量执行单条用例失败", e);
                        return UiBatchExecuteResultVO.ExecuteDetailVO.builder()
                                .useCaseId(finalUseCase.getId())
                                .useCaseName(finalUseCase.getName())
                                .executionIndex(executionIndex)
                                .result(null)
                                .success(false)
                                .duration(0L)
                                .build();
                    } finally {
                        // 释放信号量许可
                        semaphore.release();
                    }
                }, uiTestExecutor);

                futures.add(future);

            }

        }


        // 等待所有任务完成并收集结果
        List<CompletableFuture<UiBatchExecuteResultVO.ExecuteDetailVO>> allFutures =
                futures.stream().map(f -> f.exceptionally(ex -> {
                    log.error("UI 批量执行任务异常", ex);
                    // 异常情况下返回失败结果（内部已处理异常，这里只是兜底）
                    return UiBatchExecuteResultVO.ExecuteDetailVO.builder()
                            .success(false)
                            .duration(0L)
                            .result(null)
                            .build();
                })).collect(Collectors.toList());

        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                allFutures.toArray(new CompletableFuture[0])
        );

        // 等待所有任务完成
        allTasks.join();

        // 收集结果
        for (CompletableFuture<UiBatchExecuteResultVO.ExecuteDetailVO> future : allFutures) {
            try {
                UiBatchExecuteResultVO.ExecuteDetailVO detail = future.get();
                details.add(detail);
                if (detail.getSuccess() != null && detail.getSuccess()) {
                    successCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("UI 批量执行汇总失败", e);
                failedCount++;
            }
        }

        long totalTime = System.currentTimeMillis() - start;

        return UiBatchExecuteResultVO.builder()
                .total(details.size())
                .success(successCount)
                .failed(failedCount)
                .totalTime(totalTime)
                .details(details)
                .build();

    }


    //检验参数

    private void validateRequest(UiTestCaseRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (!CollectionUtils.isEmpty(requestDTO.getSteps()) && requestDTO.getSteps().stream().anyMatch(step -> step.getAction() == null)) {
            throw new IllegalArgumentException("所有步骤都必须指定操作类型");
        }
    }
}

