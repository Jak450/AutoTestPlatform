package org.example.ai_study_notes.agent.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 轻量 JSON Schema 校验器，支持 type/required/properties/items/enum/minimum/maximum。
 */
@Component
public class SchemaValidator {

    public List<String> validate(Map<String, Object> schema, Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        if (schema == null) {
            return errors;
        }
        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> required) {
            for (Object item : required) {
                String key = String.valueOf(item);
                if (args == null || !args.containsKey(key) || args.get(key) == null) {
                    errors.add("缺少必填参数: " + key);
                }
            }
        }
        Object propertiesObj = schema.get("properties");
        if (args != null && propertiesObj instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!args.containsKey(key) || args.get(key) == null) {
                    continue;
                }
                if (entry.getValue() instanceof Map<?, ?> propSchema) {
                    validateValue(key, (Map<String, Object>) propSchema, args.get(key), errors);
                }
            }
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void validateValue(String key, Map<String, Object> schema, Object value, List<String> errors) {
        String type = String.valueOf(schema.getOrDefault("type", "string"));
        switch (type) {
            case "string" -> {
                if (!(value instanceof String)) {
                    errors.add("参数 " + key + " 必须是字符串");
                } else {
                    checkEnum(key, schema, (String) value, errors);
                }
            }
            case "integer" -> {
                if (!(value instanceof Number) || ((Number) value).doubleValue() != Math.floor(((Number) value).doubleValue())) {
                    errors.add("参数 " + key + " 必须是整数");
                } else {
                    checkNumberRange(key, schema, ((Number) value).doubleValue(), errors);
                }
            }
            case "number" -> {
                if (!(value instanceof Number)) {
                    errors.add("参数 " + key + " 必须是数字");
                } else {
                    checkNumberRange(key, schema, ((Number) value).doubleValue(), errors);
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    errors.add("参数 " + key + " 必须是布尔值");
                }
            }
            case "array" -> {
                if (!(value instanceof List<?> list)) {
                    errors.add("参数 " + key + " 必须是数组");
                } else if (schema.get("items") instanceof Map<?, ?> items) {
                    for (int i = 0; i < list.size(); i++) {
                        validateValue(key + "[" + i + "]", (Map<String, Object>) items, list.get(i), errors);
                    }
                }
            }
            case "object" -> {
                if (!(value instanceof Map<?, ?>)) {
                    errors.add("参数 " + key + " 必须是对象");
                }
            }
            default -> {
                // unknown type: skip
            }
        }
    }

    private void checkEnum(String key, Map<String, Object> schema, String value, List<String> errors) {
        if (schema.get("enum") instanceof List<?> enumList) {
            boolean matched = enumList.stream().anyMatch(item -> String.valueOf(item).equals(value));
            if (!matched) {
                errors.add("参数 " + key + " 必须是以下值之一: " + enumList);
            }
        }
    }

    private void checkNumberRange(String key, Map<String, Object> schema, double value, List<String> errors) {
        if (schema.get("minimum") instanceof Number min && value < min.doubleValue()) {
            errors.add("参数 " + key + " 不能小于 " + min.doubleValue());
        }
        if (schema.get("maximum") instanceof Number max && value > max.doubleValue()) {
            errors.add("参数 " + key + " 不能大于 " + max.doubleValue());
        }
    }
}
