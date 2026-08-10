package org.example.ai_study_notes.agent;

import org.example.ai_study_notes.agent.tool.SchemaValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void missingRequiredField() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("pid", Map.of("type", "integer")),
                "required", List.of("pid"));
        List<String> errors = validator.validate(schema, Map.of());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("pid"));
    }

    @Test
    void wrongTypeRejected() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("id", Map.of("type", "integer")),
                "required", List.of("id"));
        List<String> errors = validator.validate(schema, Map.of("id", "abc"));
        assertEquals(1, errors.size());
    }

    @Test
    void enumValueValidated() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("method", Map.of("type", "string", "enum", List.of("GET", "POST"))),
                "required", List.of("method"));
        assertTrue(validator.validate(schema, Map.of("method", "GET")).isEmpty());
        assertEquals(1, validator.validate(schema, Map.of("method", "DELETE")).size());
    }

    @Test
    void arrayItemsValidated() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("useCaseIds", Map.of("type", "array", "items", Map.of("type", "integer"))),
                "required", List.of("useCaseIds"));
        assertTrue(validator.validate(schema, Map.of("useCaseIds", List.of(1, 2))).isEmpty());
        assertEquals(1, validator.validate(schema, Map.of("useCaseIds", List.of(1, "x"))).size());
    }

    @Test
    void numberRangeValidated() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("maxConcurrency", Map.of("type", "integer", "minimum", 1, "maximum", 50)),
                "required", List.of());
        assertTrue(validator.validate(schema, Map.of("maxConcurrency", 5)).isEmpty());
        assertEquals(1, validator.validate(schema, Map.of("maxConcurrency", 99)).size());
    }
}
