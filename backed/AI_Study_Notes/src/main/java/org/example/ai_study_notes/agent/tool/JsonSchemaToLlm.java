package org.example.ai_study_notes.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;
import java.util.Map;

/**
 * 将内部 JSON Schema（Map）转换为 LangChain4j JsonObjectSchema。
 */
public final class JsonSchemaToLlm {

    private JsonSchemaToLlm() {
    }

    public static JsonObjectSchema convert(Map<String, Object> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (schema == null) {
            return builder.build();
        }
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                    builder.addProperty(name, convertElement((Map<String, Object>) propertySchema));
                }
            }
        }
        if (schema.get("required") instanceof List<?> required) {
            builder.required(required.stream().map(String::valueOf).toList());
        }
        return builder.build();
    }

    private static JsonSchemaElement convertElement(Map<String, Object> schema) {
        String type = String.valueOf(schema.getOrDefault("type", "string"));
        switch (type) {
            case "object":
                return convert(schema);
            case "array": {
                JsonArraySchema.Builder builder = JsonArraySchema.builder();
                if (schema.get("items") instanceof Map<?, ?> items) {
                    builder.items(convertElement((Map<String, Object>) items));
                }
                return builder.build();
            }
            case "integer":
                return JsonIntegerSchema.builder().build();
            case "number":
                return JsonNumberSchema.builder().build();
            case "boolean":
                return JsonBooleanSchema.builder().build();
            case "string":
            default: {
                if (schema.get("enum") instanceof List<?> enumList && !enumList.isEmpty()) {
                    return JsonEnumSchema.builder()
                            .enumValues(enumList.stream().map(String::valueOf).toList())
                            .build();
                }
                return JsonStringSchema.builder().build();
            }
        }
    }
}
