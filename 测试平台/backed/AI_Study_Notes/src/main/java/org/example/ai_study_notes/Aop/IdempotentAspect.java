package org.example.ai_study_notes.Aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.ai_study_notes.Aop.anno.Idempotent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;

@Aspect
@Component
public class IdempotentAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader("Idempotent-Token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("请求头缺少 Idempotent-Token");
        }

        String key = "idempotent:" + token;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "consumed", Duration.ofSeconds(idempotent.ttl()));

        if (Boolean.FALSE.equals(success)) {
            throw new RuntimeException("请求已提交，请勿重复操作");
        }

        return joinPoint.proceed();
    }
}