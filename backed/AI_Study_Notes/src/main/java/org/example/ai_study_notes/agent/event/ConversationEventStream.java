package org.example.ai_study_notes.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.agent.contract.AgentContract;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 单个会话的事件缓冲与广播。
 * 事件持久化到 Redis（断线重放/重启不丢），并实时广播给订阅者。
 * 心跳事件只广播不落库，避免污染事件流。
 */
public class ConversationEventStream {

    private static final int MAX_BUFFER = 10000;
    private static final String EVENTS_KEY_PREFIX = "agent:events:";
    private static final String SEQ_KEY_SUFFIX = ":seq";

    private final Long conversationId;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public ConversationEventStream(Long conversationId,
                                   ObjectMapper objectMapper,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.conversationId = conversationId;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public AgentEvent emit(String type, Map<String, Object> data) {
        AgentEvent event = nextEvent(type, data);
        String json = event.toJson(objectMapper);
        try {
            String key = eventsKey();
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_BUFFER, -1);
            redisTemplate.expire(key, AgentContract.SESSION_RETENTION_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            // 事件落库失败不影响实时广播
        }
        broadcast(event);
        if ("agent_end".equals(type)) {
            clear();
        }
        return event;
    }

    /**
     * 心跳等实时事件：只广播，不落库。
     */
    public AgentEvent emitLive(String type, Map<String, Object> data) {
        AgentEvent event = nextEvent(type, data);
        broadcast(event);
        return event;
    }

    public void replay(SseEmitter emitter, long lastEventId) {
        try {
            List<Object> raw = redisTemplate.opsForList().range(eventsKey(), 0, -1);
            if (raw == null) {
                return;
            }
            for (Object item : raw) {
                try {
                    AgentEvent event = objectMapper.readValue(String.valueOf(item), AgentEvent.class);
                    if (event.getId() > lastEventId) {
                        send(emitter, event);
                    }
                } catch (Exception ignored) {
                    // 单条解析失败跳过
                }
            }
        } catch (Exception e) {
            // 重放失败忽略，等待实时事件
        }
    }

    public void attach(SseEmitter emitter) {
        subscribers.add(emitter);
    }

    public void detach(SseEmitter emitter) {
        subscribers.remove(emitter);
    }

    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    public void close() {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // ignore
            }
        }
        subscribers.clear();
    }

    private AgentEvent nextEvent(String type, Map<String, Object> data) {
        long id = nextId();
        return AgentEvent.builder()
                .id(id)
                .type(type)
                .data(data)
                .ts(System.currentTimeMillis())
                .build();
    }

    private long nextId() {
        try {
            Long seq = redisTemplate.opsForValue().increment(eventsKey() + SEQ_KEY_SUFFIX);
            return seq == null ? 1 : seq;
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private void clear() {
        try {
            redisTemplate.delete(List.of(eventsKey(), eventsKey() + SEQ_KEY_SUFFIX));
        } catch (Exception ignored) {
            // ignore
        }
    }

    private String eventsKey() {
        return EVENTS_KEY_PREFIX + conversationId;
    }

    private void broadcast(AgentEvent event) {
        for (SseEmitter emitter : subscribers) {
            send(emitter, event);
        }
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.getId()))
                    .name(event.getType())
                    .data(event.toJson(objectMapper), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            subscribers.remove(emitter);
        }
    }
}
