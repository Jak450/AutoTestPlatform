package org.example.ai_study_notes.Aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.example.ai_study_notes.Aop.anno.Idempotent;
import org.example.ai_study_notes.Pojo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Aspect
@Component
public class IdempotentAspect {

    private static final String KEY_PREFIX = "idempotent:";
    private static final ThreadLocal<String> KEY_CACHE = new ThreadLocal<>();

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before("@annotation(idempotent)")
    public void doBefore(JoinPoint joinPoint, Idempotent idempotent) {
        long interval = idempotent.timeUnit().toMillis(idempotent.interval());
        if (interval < 1000) {
            throw new IllegalArgumentException("重复提交间隔时间不能小于'1'秒");
        }

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String url = request.getRequestURI();
        String bodyHash = hashArgs(joinPoint.getArgs());
        String cacheKey = KEY_PREFIX + url + ":" + bodyHash;

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(cacheKey, "1", Duration.ofMillis(interval));

        if (Boolean.FALSE.equals(success)) {
            throw new RuntimeException(idempotent.message());
        }

        KEY_CACHE.set(cacheKey);
    }

    @AfterReturning(pointcut = "@annotation(idempotent)", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Idempotent idempotent, Object result) {
        try {
            redisTemplate.delete(KEY_CACHE.get());
        } finally {
            KEY_CACHE.remove();
        }
    }

    @AfterThrowing(pointcut = "@annotation(idempotent)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Idempotent idempotent, Exception e) {
        redisTemplate.delete(KEY_CACHE.get());
        KEY_CACHE.remove();
    }

    private String hashArgs(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(args);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.identityHashCode(args));
        }
    }
}
