package org.example.ai_study_notes.agent.tool.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolMethodExecutorTest {

    public static class FixtureBean {
        public String greet(@ToolParam(name = "name") String name,
                            @ToolParam(name = "count", required = false) Integer count,
                            ToolContext context) {
            return "hello " + name + ":" + count + ":ctx=" + context.getConversationId();
        }

        public String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void bindsArgsAndInjectsContext() throws Exception {
        FixtureBean bean = new FixtureBean();
        Method method = FixtureBean.class.getMethod("greet", String.class, Integer.class, ToolContext.class);
        ToolDefinition definition = ToolDefinition.builder()
                .name("greet").permission(ToolPermission.READ).build();
        ToolMethodExecutor executor = new ToolMethodExecutor(definition, bean, method, new ObjectMapper());

        ToolResult result = executor.execute(
                Map.of("name", "张三", "count", 3),
                ToolContext.builder().userId(1L).conversationId(7L).build());

        assertEquals(ToolResultMeta.Status.SUCCESS, result.getStatus());
        assertEquals("hello 张三:3:ctx=7", result.getData());
    }

    @Test
    void wrapsExceptionIntoErrorResult() throws Exception {
        FixtureBean bean = new FixtureBean();
        Method method = FixtureBean.class.getMethod("boom");
        ToolDefinition definition = ToolDefinition.builder()
                .name("boom").permission(ToolPermission.READ).build();
        ToolMethodExecutor executor = new ToolMethodExecutor(definition, bean, method, new ObjectMapper());

        ToolResult result = executor.execute(Map.of(), ToolContext.builder().build());

        assertEquals(ToolResultMeta.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("boom"));
    }
}
