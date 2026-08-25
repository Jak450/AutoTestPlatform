package org.example.ai_study_notes.agent.memory.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleEmbeddingClientTest {

    @Test
    void embedAllReturnsVectors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] resp = "{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3,0.4]}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        try {
            AgentProperties props = new AgentProperties();
            props.getEmbedding().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            props.getEmbedding().setApiKey("test-key");
            OpenAiCompatibleEmbeddingClient client =
                    new OpenAiCompatibleEmbeddingClient(props, new ObjectMapper());
            List<List<Float>> result = client.embedAll(List.of("a", "b"));
            assertEquals(2, result.size());
            assertEquals(0.1f, result.get(0).get(0));
            assertEquals(0.4f, result.get(1).get(1));
        } finally {
            server.stop(0);
        }
    }
}
