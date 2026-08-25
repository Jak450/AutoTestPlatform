package org.example.ai_study_notes.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.agent.audit.AuditService;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.execution.AgentToolExecution;
import org.example.ai_study_notes.agent.execution.ToolExecutionMapper;
import org.example.ai_study_notes.agent.memory.episode.EpisodeRecorder;
import org.example.ai_study_notes.agent.middleware.MiddlewareChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具重传幂等：写类工具同参数只执行一次，成功结果可重放；失败允许重试；执行中拦截；读工具不记录。
 */
class ToolExecutionIdempotencyTest {

    private ToolRegistry registry;
    private SchemaValidator schemaValidator;
    private MiddlewareChain middlewareChain;
    private AuditService auditService;
    private ToolExecutionMapper executionMapper;
    private ToolExecutionService service;
    private AtomicInteger invocations;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        schemaValidator = mock(SchemaValidator.class);
        middlewareChain = mock(MiddlewareChain.class);
        auditService = mock(AuditService.class);
        executionMapper = mock(ToolExecutionMapper.class);
        service = new ToolExecutionService(registry, schemaValidator, middlewareChain, auditService,
                executionMapper, new ObjectMapper(), mock(EpisodeRecorder.class));
        invocations = new AtomicInteger();
        when(schemaValidator.validate(any(), any())).thenReturn(List.of());
        when(middlewareChain.before(anyString(), anyMap(), any())).thenReturn(Optional.empty());
        doNothing().when(middlewareChain).after(anyString(), anyMap(), any(), any());
    }

    private void registerWriteTool(String name, ToolDefinition.ToolExecutor executor) {
        registry.register(ToolDefinition.builder()
                .name(name)
                .label(name)
                .description("test write tool")
                .inputSchema(Map.of("type", "object", "properties", Map.of("a", Map.of("type", "integer"))))
                .permission(ToolPermission.CONFIRM_WRITE)
                .category("测试")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(executor)
                .build());
    }

    private ToolDefinition.ToolExecutor successExecutor(String name) {
        return new ToolDefinition.ToolExecutor() {
            @Override
            public ToolDefinition definition() {
                return null;
            }

            @Override
            public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                invocations.incrementAndGet();
                return ToolResult.success(name, Map.of("done", true), "ok");
            }
        };
    }

    private ToolContext context(Long conversationId, String toolCallId) {
        return ToolContext.builder().userId(1L).conversationId(conversationId).toolCallId(toolCallId).build();
    }

    @Test
    void samePayloadExecutesOnceAndReplays() {
        registerWriteTool("create_demo", successExecutor("create_demo"));
        when(executionMapper.insert(any(AgentToolExecution.class))).thenReturn(1);

        ToolResult first = service.execute("create_demo", Map.of("a", 1), context(10L, "call_1"), true);
        assertEquals(ToolResultMeta.Status.SUCCESS, first.getStatus());
        assertEquals(1, invocations.get());

        AgentToolExecution existing = AgentToolExecution.builder()
                .id(1L)
                .conversationId(10L)
                .toolName("create_demo")
                .payloadHash("same")
                .status("success")
                .result("{\"status\":\"SUCCESS\",\"data\":{\"done\":true},\"message\":\"ok\"}")
                .updatedAt(LocalDateTime.now())
                .build();
        when(executionMapper.insert(any(AgentToolExecution.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(executionMapper.selectOne(any())).thenReturn(existing);

        ToolResult second = service.execute("create_demo", Map.of("a", 1), context(10L, "call_2"), true);
        assertEquals(ToolResultMeta.Status.SUCCESS, second.getStatus());
        assertTrue(second.getMessage().contains("幂等重放"));
        assertEquals(1, invocations.get());
    }

    @Test
    void failedExecutionAllowsRetry() {
        registerWriteTool("create_flaky", new ToolDefinition.ToolExecutor() {
            @Override
            public ToolDefinition definition() {
                return null;
            }

            @Override
            public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                if (invocations.incrementAndGet() == 1) {
                    throw new RuntimeException("boom");
                }
                return ToolResult.success("create_flaky", Map.of("done", true), "ok");
            }
        });
        when(executionMapper.insert(any(AgentToolExecution.class))).thenReturn(1);

        ToolResult first = service.execute("create_flaky", Map.of("a", 1), context(10L, "call_1"), true);
        assertEquals(ToolResultMeta.Status.ERROR, first.getStatus());

        AgentToolExecution failed = AgentToolExecution.builder()
                .id(1L)
                .conversationId(10L)
                .toolName("create_flaky")
                .payloadHash("same")
                .status("failed")
                .result("{\"status\":\"ERROR\",\"message\":\"boom\"}")
                .updatedAt(LocalDateTime.now())
                .build();
        when(executionMapper.insert(any(AgentToolExecution.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(executionMapper.selectOne(any())).thenReturn(failed);

        ToolResult second = service.execute("create_flaky", Map.of("a", 1), context(10L, "call_2"), true);
        assertEquals(ToolResultMeta.Status.SUCCESS, second.getStatus());
        assertEquals(2, invocations.get());
    }

    @Test
    void runningExecutionIsBlocked() {
        registerWriteTool("create_demo", successExecutor("create_demo"));
        AgentToolExecution running = AgentToolExecution.builder()
                .id(1L)
                .conversationId(10L)
                .toolName("create_demo")
                .payloadHash("same")
                .status("running")
                .updatedAt(LocalDateTime.now())
                .build();
        when(executionMapper.insert(any(AgentToolExecution.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(executionMapper.selectOne(any())).thenReturn(running);

        ToolResult result = service.execute("create_demo", Map.of("a", 1), context(10L, "call_1"), true);
        assertEquals(ToolResultMeta.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("正在执行中"));
        assertEquals(0, invocations.get());
    }

    @Test
    void readToolsAreNotRecorded() {
        registry.register(ToolDefinition.builder()
                .name("query_demo")
                .label("query_demo")
                .description("read tool")
                .inputSchema(Map.of("type", "object", "properties", Map.of()))
                .permission(ToolPermission.READ)
                .category("测试")
                .activeByDefault(true)
                .version("1.0.0")
                .executor(new ToolDefinition.ToolExecutor() {
                    @Override
                    public ToolDefinition definition() {
                        return null;
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                        invocations.incrementAndGet();
                        return ToolResult.success("query_demo", Map.of("rows", 0), "ok");
                    }
                })
                .build());
        service.execute("query_demo", Map.of(), context(10L, "call_1"), true);
        assertEquals(1, invocations.get());
        verify(executionMapper, never()).insert(any(AgentToolExecution.class));
    }
}
