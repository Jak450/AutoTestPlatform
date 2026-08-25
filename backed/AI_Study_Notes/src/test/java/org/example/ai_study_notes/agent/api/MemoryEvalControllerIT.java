package org.example.ai_study_notes.agent.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class MemoryEvalControllerIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void retrieveEndpointRequiresAuth() {
        ResponseEntity<String> anon = rest.exchange(
                "/api/agent/memory/retrieve?query=测试&topK=5",
                HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertEquals(401, anon.getStatusCode().value());
    }
}
