package org.example.ai_study_notes.agent.tool.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 注解工具反射执行器：参数绑定（按名）+ ToolContext 注入 + 异常统一包装。
 */
@Slf4j
public class ToolMethodExecutor implements ToolDefinition.ToolExecutor {

    private final ToolDefinition definition;
    private final Object target;
    private final Method method;
    private final ObjectMapper objectMapper;

    public ToolMethodExecutor(ToolDefinition definition, Object target, Method method,
                              ObjectMapper objectMapper) {
        this.definition = definition;
        this.target = target;
        this.method = method;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext context) {
        long start = System.currentTimeMillis();
        try {
            Object result = method.invoke(target, bind(args, context));
            return ToolResult.success(definition.getName(), result, null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("注解工具执行失败 tool={}", definition.getName(), cause);
            return ToolResult.error(definition.getName(), "工具执行失败: " + cause.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE, start);
        } catch (Exception e) {
            log.warn("注解工具调用失败 tool={}", definition.getName(), e);
            return ToolResult.error(definition.getName(), "工具调用失败: " + e.getMessage(),
                    ToolResultMeta.ErrorType.INTERNAL,
                    ToolResultMeta.RecommendedNextAction.TRY_ALTERNATIVE, start);
        }
    }

    private Object[] bind(Map<String, Object> args, ToolContext context) {
        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType().equals(ToolContext.class)) {
                values[i] = context;
                continue;
            }
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            String name = toolParam != null && !toolParam.name().isBlank()
                    ? toolParam.name() : parameter.getName();
            Object raw = args.get(name);
            if (raw == null) {
                if (toolParam == null || toolParam.required()) {
                    throw new IllegalArgumentException("缺少参数: " + name);
                }
                values[i] = null;
            } else {
                values[i] = objectMapper.convertValue(raw, parameter.getType());
            }
        }
        return values;
    }
}
