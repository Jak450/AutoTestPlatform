package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnnotationToolSchemaTest {

    public String fixture(@ToolParam(name = "name", description = "姓名") String name,
                          @ToolParam(name = "count", required = false) Integer count,
                          @ToolParam(name = "tags") List<String> tags,
                          ToolContext context) {
        return name;
    }

    @Test
    void generatesTypedSchemaAndSkipsToolContext() throws Exception {
        Method method = getClass().getMethod("fixture", String.class, Integer.class, List.class, ToolContext.class);
        Map<String, Object> schema = AnnotationToolSchema.generate(method);

        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals("string", ((Map<String, Object>) properties.get("name")).get("type"));
        assertEquals("integer", ((Map<String, Object>) properties.get("count")).get("type"));
        assertEquals("array", ((Map<String, Object>) properties.get("tags")).get("type"));
        assertFalse(properties.containsKey("context"));
        assertEquals(List.of("name", "tags"), schema.get("required"));
    }
}
