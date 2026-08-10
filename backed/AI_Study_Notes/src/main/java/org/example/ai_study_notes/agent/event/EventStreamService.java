package org.example.ai_study_notes.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 会话事件流注册表。
 */
@Service
public class EventStreamService {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentMap<Long, ConversationEventStream> streams = new ConcurrentHashMap<>();

    public EventStreamService(ObjectMapper objectMapper, RedisTemplate<String, Object> redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public ConversationEventStream getOrCreate(Long conversationId) {
        return streams.computeIfAbsent(conversationId,
                id -> new ConversationEventStream(id, objectMapper, redisTemplate));
    }

    public void remove(Long conversationId) {
        ConversationEventStream stream = streams.remove(conversationId);
        if (stream != null) {
            stream.close();
        }
    }
}
