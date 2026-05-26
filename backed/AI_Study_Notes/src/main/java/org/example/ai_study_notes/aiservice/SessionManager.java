package org.example.ai_study_notes.aiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.context.DocContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class SessionManager {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "ai:session:";

    public String create(DocContext context) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        try {
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, json, SESSION_TTL);
            return sessionId;
        } catch (Exception e) {
            log.error("Failed to create session", e);
            throw new RuntimeException("创建会话失败", e);
        }
    }

    public DocContext get(String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json == null) return null;
            return objectMapper.readValue(json, DocContext.class);
        } catch (Exception e) {
            log.error("Failed to get session {}", sessionId, e);
            return null;
        }
    }

    public void update(String sessionId, DocContext context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, json, SESSION_TTL);
        } catch (Exception e) {
            log.error("Failed to update session {}", sessionId, e);
            throw new RuntimeException("更新会话失败", e);
        }
    }

    public void remove(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }
}