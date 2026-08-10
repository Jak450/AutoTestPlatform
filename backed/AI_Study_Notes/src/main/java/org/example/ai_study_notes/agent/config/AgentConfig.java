package org.example.ai_study_notes.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.agent.auth.JwtAuthFilter;
import org.example.ai_study_notes.agent.auth.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 模块配置入口。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    /**
     * Agent 运行专用线程池，避免与接口测试并发池互相挤占。
     */
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("agent-run-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * JWT 认证过滤器：拦截所有 /api/* 请求，登录接口除外。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtUtil, redisTemplate, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setName("jwtAuthFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
