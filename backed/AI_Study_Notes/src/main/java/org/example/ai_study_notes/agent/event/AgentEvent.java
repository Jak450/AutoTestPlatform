package org.example.ai_study_notes.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Agent SSE 事件，id 在会话内单调递增。
 */
@Data
@Builder
public class AgentEvent {

    private long id;
    private String type;
    private Map<String, Object> data;
    private long ts;

    public String toJson(ObjectMapper objectMapper) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("id", id);
            body.put("type", type);
            body.put("data", data);
            body.put("ts", ts);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"id\":" + id + ",\"type\":\"" + type + "\",\"data\":{},\"ts\":" + ts + "}";
        }
    }
}
