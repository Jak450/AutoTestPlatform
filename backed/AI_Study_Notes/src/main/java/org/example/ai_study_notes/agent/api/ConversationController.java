package org.example.ai_study_notes.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.contract.EventType;
import org.example.ai_study_notes.agent.core.AgentLoop;
import org.example.ai_study_notes.agent.core.RunRegistry;
import org.example.ai_study_notes.agent.context.ContextCompactor;
import org.example.ai_study_notes.agent.event.ConversationEventStream;
import org.example.ai_study_notes.agent.event.EventStreamService;
import org.example.ai_study_notes.agent.session.AgentConversation;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.example.ai_study_notes.agent.session.MessageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent 会话与消息 API：CRUD、发送消息（异步 run）、SSE 事件流、取消。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/conversations")
public class ConversationController {

    private static final long SSE_HEARTBEAT_SECONDS = 15;
    private static final String IDEMPOTENT_PREFIX = "agent:msg:idem:";

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final EventStreamService eventStreamService;
    private final AgentLoop agentLoop;
    private final RunRegistry runRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ContextCompactor contextCompactor;
    private final Executor agentExecutor;

    public ConversationController(ConversationService conversationService,
                                  MessageService messageService,
                                  EventStreamService eventStreamService,
                                  AgentLoop agentLoop,
                                  RunRegistry runRegistry,
                                  RedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper,
                                  ContextCompactor contextCompactor,
                                  @Qualifier("agentExecutor") Executor agentExecutor) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.eventStreamService = eventStreamService;
        this.agentLoop = agentLoop;
        this.runRegistry = runRegistry;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.contextCompactor = contextCompactor;
        this.agentExecutor = agentExecutor;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateConversationRequest request) {
        AgentConversation conversation = conversationService.create(UserContext.userId(), request.getTitle());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", conversation.getId());
        data.put("title", conversation.getTitle());
        data.put("createdAt", conversation.getCreatedAt());
        return Result.success(data);
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<AgentConversation> conversations = conversationService.list(UserContext.userId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentConversation conversation : conversations) {
            result.add(toConversationMap(conversation));
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        AgentConversation conversation = requireOwned(id);
        Map<String, Object> data = toConversationMap(conversation);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AgentMessage message : messageService.list(id)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", message.getId());
            map.put("role", message.getRole());
            map.put("type", message.getType());
            map.put("content", message.getContent());
            map.put("toolMeta", parseMeta(message.getToolMeta()));
            map.put("seq", message.getSeq());
            map.put("createdAt", message.getCreatedAt());
            messages.add(map);
        }
        data.put("messages", messages);
        data.put("running", runRegistry.isRunning(id));
        return Result.success(data);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Long userId = UserContext.userId();
        conversationService.delete(userId, id);
        eventStreamService.remove(id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable("id") Long id) {
        requireOwned(id);
        if (!runRegistry.isRunning(id)) {
            return Result.error("当前没有正在执行的 run");
        }
        runRegistry.cancel(id);
        return Result.success(Map.of("status", "cancelled"));
    }

    @PostMapping("/{id}/compact")
    public Result<Map<String, Object>> compact(@PathVariable("id") Long id) {
        requireOwned(id);
        String summary = contextCompactor.compact(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("summary", summary);
        return Result.success(data);
    }

    /**
     * 发送用户消息并启动 Agent run，返回 202，后续事件走 SSE。
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<Result<Map<String, Object>>> sendMessage(@PathVariable("id") Long id,
                                                                   @RequestBody SendMessageRequest request) {
        Long userId = UserContext.userId();
        requireOwned(id);
        if (request.getContent() == null || request.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Result.error("消息内容不能为空"));
        }
        if (runRegistry.isRunning(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error("当前会话正在处理中，请稍后再试"));
        }
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(
                    IDEMPOTENT_PREFIX + id + ":" + idempotencyKey, "1", 60, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(first)) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Result.success(Map.of("status", "duplicate", "conversationId", id)));
            }
        }
        String runId = "run-" + UUID.randomUUID();
        runRegistry.start(id);
        agentExecutor.execute(() -> agentLoop.run(id, userId, request.getContent()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "accepted");
        data.put("conversationId", id);
        data.put("runId", runId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(data));
    }

    /**
     * SSE 事件流：支持 Last-Event-ID 断点续传与心跳。
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") Long id,
                             @RequestParam(name = "lastEventId", defaultValue = "0") long lastEventId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        if (lastEventId <= 0 && lastEventIdHeader != null) {
            try {
                lastEventId = Long.parseLong(lastEventIdHeader.trim());
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        requireOwned(id);
        ConversationEventStream stream = eventStreamService.getOrCreate(id);
        SseEmitter emitter = new SseEmitter(0L);
        stream.replay(emitter, lastEventId);
        stream.attach(emitter);
        emitter.onCompletion(() -> stream.detach(emitter));
        emitter.onTimeout(() -> stream.detach(emitter));
        emitter.onError(e -> stream.detach(emitter));

        boolean runActive = runRegistry.isRunning(id);
        if (!runActive) {
            agentExecutor.execute(() -> {
                try {
                    Thread.sleep(500);
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            });
            return emitter;
        }
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "agent-heartbeat-" + id);
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                if (!runRegistry.isRunning(id)) {
                    emitter.complete();
                    heartbeat.shutdown();
                } else {
                    stream.emit(EventType.HEARTBEAT.value(), Map.of("ts", System.currentTimeMillis()));
                }
            } catch (Exception e) {
                heartbeat.shutdown();
            }
        }, SSE_HEARTBEAT_SECONDS, SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        return emitter;
    }

    private AgentConversation requireOwned(Long id) {
        AgentConversation conversation = conversationService.getOwned(UserContext.userId(), id);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return conversation;
    }

    private Map<String, Object> toConversationMap(AgentConversation conversation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", conversation.getId());
        map.put("title", conversation.getTitle());
        map.put("status", conversation.getStatus());
        map.put("createdAt", conversation.getCreatedAt());
        map.put("updatedAt", conversation.getUpdatedAt());
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMeta(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Data
    public static class CreateConversationRequest {
        private String title;
    }

    @Data
    public static class SendMessageRequest {
        private String content;
        private String idempotencyKey;
    }
}
