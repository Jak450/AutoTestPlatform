package org.example.ai_study_notes.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单个会话的事件缓冲与广播。
 * 事件先入缓冲再广播，新连接可从 lastEventId 断点续传。
 */
public class ConversationEventStream {

    private static final int MAX_BUFFER = 10000;

    private final Long conversationId;
    private final ObjectMapper objectMapper;
    private final AtomicLong seq = new AtomicLong(0);
    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<AgentEvent> buffer = new ConcurrentLinkedQueue<>();

    public ConversationEventStream(Long conversationId, ObjectMapper objectMapper) {
        this.conversationId = conversationId;
        this.objectMapper = objectMapper;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public AgentEvent emit(String type, Map<String, Object> data) {
        AgentEvent event = AgentEvent.builder()
                .id(seq.incrementAndGet())
                .type(type)
                .data(data)
                .ts(System.currentTimeMillis())
                .build();
        buffer.add(event);
        while (buffer.size() > MAX_BUFFER) {
            buffer.poll();
        }
        for (SseEmitter emitter : subscribers) {
            send(emitter, event);
        }
        // run 结束（agent_end）后清空缓冲，避免新连接重放旧 run 事件造成重复渲染
        if ("agent_end".equals(type)) {
            buffer.clear();
        }
        return event;
    }

    public void replay(SseEmitter emitter, long lastEventId) {
        for (AgentEvent event : buffer) {
            if (event.getId() > lastEventId) {
                send(emitter, event);
            }
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
