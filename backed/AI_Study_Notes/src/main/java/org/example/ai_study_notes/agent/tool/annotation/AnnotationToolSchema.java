package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.tool.ToolContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注解工具 inputSchema 生成器：从方法签名反射生成 JSON Schema（纯函数，无状态）。
 */
public final class AnnotationToolSchema {

    private AnnotationToolSchema() {
    }

    public static Map<String, Object> generate(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType().equals(ToolContext.class)) {
                continue;
            }
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            String name = toolParam != null && !toolParam.name().isBlank()
                    ? toolParam.name() : parameter.getName();
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", typeOf(parameter.getType()));
            if (toolParam != null && !toolParam.description().isBlank()) {
                property.put("description", toolParam.description());
            }
            properties.put(name, property);
            if (toolParam == null || toolParam.required()) {
                required.add(name);
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static String typeOf(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }
}
