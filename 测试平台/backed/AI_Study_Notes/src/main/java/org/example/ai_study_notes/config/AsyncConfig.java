package org.example.ai_study_notes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 * 用于配置接口并发执行的线程池
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * 接口测试执行线程池
     * 用于并发执行多个接口测试用例
     */
    @Bean("testExecutor")
    public Executor apiTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：保持运行的线程数
        executor.setCorePoolSize(10);
        // 最大线程数：最大可创建的线程数
        executor.setMaxPoolSize(50);
        // 队列容量：等待执行的任务数
        executor.setQueueCapacity(100);
        // 线程名前缀
        executor.setThreadNamePrefix("auto-test-");
        // 拒绝策略：当线程池和队列都满了，如何处理新任务
        // CallerRunsPolicy：由调用线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("自动化测试线程池初始化完成: 核心线程={}, 最大线程={}, 队列容量={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());
        return executor;
    }
}

