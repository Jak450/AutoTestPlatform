package org.example.ai_study_notes.agent;

import io.jsonwebtoken.Claims;
import org.example.ai_study_notes.agent.auth.JwtUtil;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private JwtUtil newJwtUtil() {
        AgentProperties properties = new AgentProperties();
        properties.getJwt().setSecret("unit-test-jwt-secret-0123456789abcdef0123456789abcdef");
        return new JwtUtil(properties);
    }

    @Test
    void generateAndParse() {
        JwtUtil jwtUtil = newJwtUtil();
        String token = jwtUtil.generate(1L, "admin", "admin");
        Claims claims = jwtUtil.parse(token);
        assertEquals(1L, ((Number) claims.get("uid")).longValue());
        assertEquals("admin", claims.get("username", String.class));
        assertEquals("admin", claims.get("role", String.class));
    }

    @Test
    void tamperedTokenRejected() {
        JwtUtil jwtUtil = newJwtUtil();
        String token = jwtUtil.generate(1L, "admin", "admin");
        assertThrows(Exception.class, () -> jwtUtil.parse(token + "x"));
    }

    @Test
    void shortSecretRejected() {
        AgentProperties properties = new AgentProperties();
        properties.getJwt().setSecret("too-short");
        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }
}
